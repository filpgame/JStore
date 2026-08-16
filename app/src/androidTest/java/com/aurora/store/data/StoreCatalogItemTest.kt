/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

class StoreCatalogItemTest {
    @Test
    fun createsIntegrityCheckedExternalApk() {
        val entry = item().toEntity()

        assertThat(entry.originalPackageId).isEqualTo("com.example.play")
        assertThat(entry.customPackageId).isEqualTo("com.example.j7")
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
        customPackageId: String = "com.example.j7"
    ) = StoreCatalogRepository.StoreCatalogItem(
        originalPackageId = originalPackageId,
        customPackageId = customPackageId,
        title = "Example",
        summary = "Built for the device",
        developerName = "Example Inc.",
        iconUrl = "https://downloads.example.com/icon.png",
        versionCode = 2,
        versionName = "2.0",
        apkName = "app.apk",
        downloadUrl = "https://downloads.example.com/app.apk",
        sizeBytes = 1_024,
        sha256 = sha256,
        signerSha256 = signerSha256,
        deviceModels = deviceModels
    )
}
