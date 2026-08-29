/*
 * SPDX-FileCopyrightText: 2026 JConfig
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.model

import com.aurora.Constants.FLAVOUR_JAECOO
import com.aurora.Constants.FLAVOUR_PRELOAD
import com.aurora.Constants.FLAVOUR_VANILLA
import com.aurora.store.util.PackageUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class JConfigSelfUpdateTest {
    @Test
    fun convertsVersioncheckPayloadToIntegrityCheckedSelfUpdate() {
        val release = Json.decodeFromString<JConfigRelease>(
            """
            {
              "tagName": "v4.14.1",
              "versionName": "4.14.1",
              "versionCode": 11,
              "apkDownloadUrl": "https://jconfig.app/v1/download/jstore-4.14.1-11.apk",
              "apkSize": 9426623,
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "releasedAt": "2026-08-29T12:00:00Z"
            }
            """.trimIndent()
        )

        val update = release.toSelfUpdateOrNull()

        assertThat(update).isNotNull()
        assertThat(update!!.versionName).isEqualTo("4.14.1")
        assertThat(update.versionCode).isEqualTo(11L)
        assertThat(update.downloadUrl)
            .isEqualTo("https://jconfig.app/v1/download/jstore-4.14.1-11.apk")
        assertThat(update.size).isEqualTo(9426623L)
        assertThat(update.sha256).isEqualTo("a".repeat(64))
        assertThat(update.updatedOn).isEqualTo("2026-08-29T12:00:00Z")
    }

    @Test
    fun rejectsVersioncheckPayloadWithoutHttpsDownload() {
        val release = JConfigRelease(
            versionName = "4.14.1",
            versionCode = 11,
            apkDownloadUrl = "http://jconfig.app/jstore.apk",
            apkSize = 1,
            sha256 = "a".repeat(64)
        )

        assertThat(release.toSelfUpdateOrNull()).isNull()
    }

    @Test
    fun rejectsVersioncheckPayloadWithoutSha256() {
        val release = JConfigRelease(
            versionName = "4.14.1",
            versionCode = 11,
            apkDownloadUrl = "https://jconfig.app/jstore.apk",
            apkSize = 1,
            sha256 = null
        )

        assertThat(release.toSelfUpdateOrNull()).isNull()
    }

    @Test
    fun jaecooReleaseUsesJconfigSelfUpdateSource() {
        assertThat(resolveSelfUpdateSource(FLAVOUR_JAECOO, BuildType.RELEASE))
            .isEqualTo(SelfUpdateSource.JCONFIG_RELEASE)
        assertThat(
            PackageUtil.isSelfUpdateSupported(
                flavour = FLAVOUR_JAECOO,
                buildType = BuildType.RELEASE,
                isFDroid = false
            )
        ).isTrue()
    }

    @Test
    fun jaecooNightlyAndDebugDoNotSupportSelfUpdate() {
        listOf(BuildType.NIGHTLY, BuildType.DEBUG).forEach { buildType ->
            assertThat(resolveSelfUpdateSource(FLAVOUR_JAECOO, buildType)).isNull()
            assertThat(
                PackageUtil.isSelfUpdateSupported(
                    flavour = FLAVOUR_JAECOO,
                    buildType = buildType,
                    isFDroid = false
                )
            ).isFalse()
        }
    }

    @Test
    fun vanillaAndPreloadKeepTheirExistingSources() {
        assertThat(resolveSelfUpdateSource(FLAVOUR_VANILLA, BuildType.RELEASE))
            .isEqualTo(SelfUpdateSource.AURORA_RELEASE)
        assertThat(resolveSelfUpdateSource(FLAVOUR_PRELOAD, BuildType.NIGHTLY))
            .isEqualTo(SelfUpdateSource.AURORA_NIGHTLY)
    }
}
