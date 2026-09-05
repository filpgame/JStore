/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.helper

import com.aurora.store.data.model.DownloadStatus
import com.aurora.store.data.room.download.Download
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadInstallReconciliationTest {
    @Test
    fun installedInstallingDownloads_returnsAllConfirmedInstallingDownloads() {
        val installed = download("com.example.installed", 12, DownloadStatus.INSTALLING)
        val missing = download("com.example.missing", 9, DownloadStatus.INSTALLING)
        val older = download("com.example.older", 7, DownloadStatus.INSTALLING)
        val completed = download("com.example.completed", 4, DownloadStatus.COMPLETED)
        val checked = mutableListOf<String>()

        val reconciled = installedInstallingDownloads(
            downloads = listOf(installed, missing, older, completed),
            isInstalledAtExpectedVersion = { packageName, versionCode ->
                checked += "$packageName:$versionCode"
                packageName == installed.packageName && versionCode == installed.versionCode
            }
        )

        assertThat(reconciled).containsExactly(installed)
        assertThat(checked).containsExactly(
            "${installed.packageName}:${installed.versionCode}",
            "${missing.packageName}:${missing.versionCode}",
            "${older.packageName}:${older.versionCode}"
        ).inOrder()
    }

    private fun download(packageName: String, versionCode: Long, status: DownloadStatus) = Download(
        packageName = packageName,
        versionCode = versionCode,
        offerType = 0,
        isInstalled = false,
        displayName = packageName,
        iconURL = "",
        size = 0,
        id = 0,
        status = status,
        progress = 0,
        speed = 0,
        timeRemaining = 0,
        totalFiles = 0,
        downloadedFiles = 0,
        fileList = emptyList(),
        sharedLibs = emptyList()
    )
}
