/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.store.compose.ui.apps.matches
import com.aurora.store.data.model.DownloadStatus
import com.aurora.store.data.room.download.Download
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

class StoreCatalogItemTest {
    @Test
    fun appliesCatalogIconToPlayApp() {
        val playApp = App(
            packageName = "com.example.play",
            iconArtwork = Artwork(url = "https://play.example.com/icon.png")
        )

        val displayed = item().toEntity().applyIconTo(playApp)

        assertThat(displayed.iconArtwork.url).isEqualTo("https://downloads.example.com/icon.png")
    }

    @Test
    fun createsIntegrityCheckedExternalApk() {
        val entry = item(premium = true).toEntity()

        assertThat(entry.originalPackageId).isEqualTo("com.example.play")
        assertThat(entry.customPackageId).isEqualTo("com.example.j7")
        assertThat(entry.isPremium).isTrue()
        assertThat(entry.toExternalApk().fileList.single().sha256)
            .isEqualTo("a".repeat(64))
        assertThat(entry.toExternalApk().fileList.single().url)
            .isEqualTo("https://downloads.example.com/app.apk")
        assertThat(entry.signerSha256).isEqualTo("b".repeat(64))
        val update = entry.toUpdate(hasValidCert = false, isIncompatible = true)
        assertThat(update.hasValidCert).isFalse()
        assertThat(update.isIncompatible).isTrue()
    }

    @Test
    fun defaultsMissingPremiumFlagToFalse() {
        assertThat(item().toEntity().isPremium).isFalse()
    }

    @Test
    fun rejectsMissingSha256() {
        assertThrows(IllegalArgumentException::class.java) {
            item(sha256 = "").toEntity()
        }
    }

    @Test
    fun rejectsMissingSignerSha256() {
        assertThrows(IllegalArgumentException::class.java) {
            item(signerSha256 = "").toEntity()
        }
    }

    @Test
    fun rejectsEntryForAnotherDeviceModel() {
        assertThrows(IllegalArgumentException::class.java) {
            item(deviceModels = listOf("J8")).toEntity()
        }
    }

    @Test
    fun rejectsPathTraversalInPackageAndApkNames() {
        assertThrows(IllegalArgumentException::class.java) {
            item(customPackageId = "../../data/local/tmp").toEntity()
        }
        assertThrows(IllegalArgumentException::class.java) {
            item(apkName = "../payload.apk").toEntity()
        }
        assertThrows(IllegalArgumentException::class.java) {
            item(apkName = "folder/payload.apk").toEntity()
        }
    }

    @Test
    fun externalDownloadRetainsArtifactMetadataAndInstalledState() {
        val entry = item().toEntity()
        val before = System.currentTimeMillis()

        val download = Download.fromExternalApk(entry.toExternalApk(), isInstalled = true)

        assertThat(download.isInstalled).isTrue()
        assertThat(download.size).isEqualTo(entry.sizeBytes)
        assertThat(download.downloadedAt).isAtLeast(before)
    }

    @Test
    fun failedDownloadOnlyMatchesTheCurrentCatalogArtifact() {
        val entry = item().toEntity()
        val current = Download.fromExternalApk(entry.toExternalApk(), isInstalled = false)
            .copy(status = DownloadStatus.FAILED)
        val stale = current.copy(versionCode = entry.versionCode - 1)

        assertThat(current.matches(entry)).isTrue()
        assertThat(stale.matches(entry)).isFalse()
    }

    @Test
    fun downloadArtifactIdentityRejectsRepublishedHashAtSameVersion() {
        val entry = item().toEntity()
        val current = Download.fromExternalApk(entry.toExternalApk(), isInstalled = false)
        val republished = current.copy(
            fileList = current.fileList.map { it.copy(sha256 = "c".repeat(64)) }
        )

        assertThat(current.hasSameArtifactAs(current.copy())).isTrue()
        assertThat(current.hasSameArtifactAs(republished)).isFalse()
    }

    @Test
    fun downloadArtifactIdentityPreservesUnresolvedPlayAndStableSha1() {
        val entry = item().toEntity()
        val current = Download.fromExternalApk(entry.toExternalApk(), isInstalled = false)
        val unresolved = current.copy(fileList = emptyList())
        val sha1File = current.fileList.single().copy(
            sha256 = "",
            sha1 = "d".repeat(40),
            url = "https://downloads.example.com/old.apk"
        )
        val rotatedUrl = sha1File.copy(url = "https://downloads.example.com/new.apk")

        assertThat(current.hasSameArtifactAs(unresolved)).isTrue()
        assertThat(
            current.copy(fileList = listOf(sha1File)).hasSameArtifactAs(
                current.copy(fileList = listOf(rotatedUrl))
            )
        ).isTrue()
    }

    @Test
    fun acceptsNullableServerFields() {
        val response = Json.decodeFromString<StoreCatalogRepository.StoreCatalogResponse>(
            """{"generatedAt":null,"apps":[]}"""
        )

        assertThat(response.generatedAt).isNull()
        assertThat(item().copy(changelog = null).toEntity().changelog).isEmpty()
    }

    @Test
    fun rejectsCrossRolePackageCollision() {
        val first = item().toEntity()
        val second = item(
            originalPackageId = first.customPackageId,
            customPackageId = "com.example.other"
        ).toEntity()

        assertThrows(IllegalArgumentException::class.java) {
            validateCatalogEntries(listOf(first, second))
        }
    }

    @Test
    fun allowsSameOriginalAndCustomWithinOneEntry() {
        val entry = item(
            originalPackageId = "com.example.same",
            customPackageId = "com.example.same"
        ).toEntity()

        validateCatalogEntries(listOf(entry))
    }

    private fun item(
        sha256: String = "a".repeat(64),
        signerSha256: String = "b".repeat(64),
        deviceModels: List<String> = listOf("J7"),
        originalPackageId: String = "com.example.play",
        customPackageId: String = "com.example.j7",
        apkName: String = "app.apk",
        premium: Boolean = false
    ) = StoreCatalogRepository.StoreCatalogItem(
        originalPackageId = originalPackageId,
        customPackageId = customPackageId,
        title = "Example",
        summary = "Built for the device",
        developerName = "Example Inc.",
        iconUrl = "https://downloads.example.com/icon.png",
        versionCode = 2,
        versionName = "2.0",
        apkName = apkName,
        downloadUrl = "https://downloads.example.com/app.apk",
        sizeBytes = 1_024,
        sha256 = sha256,
        signerSha256 = signerSha256,
        deviceModels = deviceModels,
        premium = premium
    )
}
