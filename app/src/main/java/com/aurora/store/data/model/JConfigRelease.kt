/*
 * SPDX-FileCopyrightText: 2026 JConfig
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.model

import com.aurora.Constants.JSTORE_ICON_URL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class JConfigRelease(
    @SerialName("tagName")
    val tagName: String = "",
    @SerialName("versionName")
    val versionName: String = "",
    @SerialName("versionCode")
    val versionCode: Long = 0,
    @SerialName("apkDownloadUrl")
    val apkDownloadUrl: String = "",
    @SerialName("apkSize")
    val apkSize: Long = 0,
    val sha256: String? = null,
    @SerialName("releasedAt")
    val releasedAt: String? = null
) {
    fun toSelfUpdateOrNull(): SelfUpdate? {
        val downloadUrl = apkDownloadUrl.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        val normalizedSha256 = sha256?.lowercase() ?: return null
        if (
            versionName.isBlank() ||
            versionCode <= 0 ||
            apkSize <= 0 ||
            !SHA256.matches(normalizedSha256)
        ) {
            return null
        }

        return SelfUpdate(
            versionName = versionName,
            versionCodeRaw = versionCode.toString(),
            downloadUrl = downloadUrl.toString(),
            iconUrl = JSTORE_ICON_URL,
            sha256 = normalizedSha256,
            sizeRaw = apkSize.toString(),
            updatedOn = releasedAt.orEmpty()
        )
    }

    private companion object {
        val SHA256 = Regex("^[a-fA-F0-9]{64}$")
    }
}
