/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.apps

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.aurora.store.BuildConfig
import com.aurora.store.IsolatedTest
import com.aurora.store.R
import com.aurora.store.data.room.catalog.StoreCatalogEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StoreCatalogPageTest : IsolatedTest() {
    @Test
    fun rendersDividerBetweenCatalogApps() {
        setContent {
            StoreCatalogContent(
                entries = listOf(entry("first"), entry("second")),
                downloads = emptyMap(),
                isLoading = false,
                hasError = false,
                installationRevision = 0,
                onInstall = {},
                onCancel = {},
                onRetry = {},
                onUninstall = {},
                onRefresh = {}
            )
        }

        composeTestRule.onAllNodesWithTag("store_catalog_divider")
            .assertCountEquals(1)
    }

    @Test
    fun doesNotRenderDividerForSingleCatalogApp() {
        setContent {
            StoreCatalogContent(
                entries = listOf(entry("only")),
                downloads = emptyMap(),
                isLoading = false,
                hasError = false,
                installationRevision = 0,
                onInstall = {},
                onCancel = {},
                onRetry = {},
                onUninstall = {},
                onRefresh = {}
            )
        }

        composeTestRule.onAllNodesWithTag("store_catalog_divider")
            .assertCountEquals(0)
    }

    @Test
    fun offersUninstallActionForIncompatibleSignature() {
        var uninstallRequests = 0
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        setContent {
            StoreCatalogContent(
                entries = listOf(
                    entry("incompatible").copy(
                        customPackageId = BuildConfig.APPLICATION_ID
                    )
                ),
                downloads = emptyMap(),
                isLoading = false,
                hasError = false,
                installationRevision = 0,
                onInstall = {},
                onCancel = {},
                onRetry = {},
                onUninstall = { uninstallRequests++ },
                onRefresh = {}
            )
        }

        composeTestRule.onNodeWithText(
            context.getString(R.string.store_catalog_incompatible_signature)
        ).assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText(context.getString(R.string.action_uninstall))
            .performClick()

        assertThat(uninstallRequests).isEqualTo(1)
    }

    private fun entry(suffix: String) = StoreCatalogEntry(
        originalPackageId = "com.example.original.$suffix",
        customPackageId = "com.example.custom.$suffix",
        title = "Example $suffix",
        summary = "Example app",
        developerName = "Example",
        iconUrl = "",
        versionCode = 1,
        versionName = "1.0",
        apkName = "example.apk",
        downloadUrl = "https://example.com/example.apk",
        sizeBytes = 1_024,
        sha256 = "a".repeat(64),
        signerSha256 = "b".repeat(64),
        changelog = ""
    )
}
