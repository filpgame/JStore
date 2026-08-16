/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.room.catalog

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.store.data.room.suite.ExternalApk
import com.aurora.store.data.room.update.Update

@Entity(tableName = "store_catalog")
data class StoreCatalogEntry(
    @PrimaryKey val originalPackageId: String,
    val customPackageId: String,
    val title: String,
    val summary: String,
    val developerName: String,
    val iconUrl: String,
    val versionCode: Long,
    val versionName: String,
    val apkName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val signerSha256: String,
    val changelog: String
) {
    fun toPlayFile() = PlayFile(
        name = apkName,
        url = downloadUrl,
        size = sizeBytes,
        sha256 = sha256
    )

    fun toExternalApk() = ExternalApk(
        packageName = customPackageId,
        versionCode = versionCode,
        versionName = versionName,
        displayName = title,
        iconURL = iconUrl,
        developerName = developerName,
        fileList = listOf(toPlayFile())
    )

    fun toUpdate(hasValidCert: Boolean, isIncompatible: Boolean) = Update(
        packageName = customPackageId,
        versionCode = versionCode,
        versionName = versionName,
        displayName = title,
        iconURL = iconUrl,
        changelog = changelog,
        id = 0,
        developerName = developerName,
        size = sizeBytes,
        updatedOn = "",
        hasValidCert = hasValidCert,
        offerType = 0,
        fileList = listOf(toPlayFile()),
        sharedLibs = emptyList(),
        isIncompatible = isIncompatible
    )

    fun toDisplayApp() = App(
        packageName = customPackageId,
        versionCode = versionCode,
        versionName = versionName,
        displayName = title,
        developerName = developerName,
        description = summary,
        iconArtwork = Artwork(url = iconUrl),
        size = sizeBytes,
        fileList = mutableListOf(toPlayFile()),
        isFree = true,
        isInstalled = false
    )
}
