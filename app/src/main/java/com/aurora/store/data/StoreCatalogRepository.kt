/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data

import android.util.Log
import com.aurora.extensions.TAG
import com.aurora.store.data.network.HttpClient
import com.aurora.store.data.room.catalog.StoreCatalogDao
import com.aurora.store.data.room.catalog.StoreCatalogEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
class StoreCatalogRepository @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val catalogDao: StoreCatalogDao
) {
    companion object {
        const val DEVICE_MODEL = "J7"
        const val CATALOG_URL = "https://jconfig.app/v1/store/catalog?model=$DEVICE_MODEL"

        private val SHA256 = Regex("^[a-fA-F0-9]{64}$")
        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        private val APK_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*\\.apk$")
    }

    val entries: Flow<List<StoreCatalogEntry>> = catalogDao.entries()

    private val refreshMutex = Mutex()

    suspend fun refresh(): Result<List<StoreCatalogEntry>> = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            try {
                val response = httpClient.call(CATALOG_URL, mapOf("Cache-Control" to "no-cache"))
                Result.success(
                    response.use {
                        check(it.isSuccessful) { "Catalog request failed with HTTP ${it.code}" }
                        val payload = json.decodeFromString<StoreCatalogResponse>(it.body.string())
                        val entries = payload.apps.map(StoreCatalogItem::toEntity)
                        validateCatalogEntries(entries)
                        catalogDao.replaceAll(entries)
                        entries
                    }
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to refresh store catalog", exception)
                Result.failure(exception)
            }
        }
    }

    suspend fun getEntries(): List<StoreCatalogEntry> = catalogDao.getEntries()

    suspend fun findByOriginalPackage(packageName: String): StoreCatalogEntry? =
        catalogDao.findByOriginalPackage(packageName)

    suspend fun findByCustomPackage(packageName: String): StoreCatalogEntry? =
        catalogDao.findByCustomPackage(packageName)

    suspend fun hasValidSnapshot(): Boolean = catalogDao.hasValidSnapshot() == true

    suspend fun resolveForDownload(packageName: String): StoreCatalogEntry? {
        val refreshResult = refresh()
        if (refreshResult.isFailure && !hasValidSnapshot()) {
            throw StoreCatalogUnavailableException(refreshResult.exceptionOrNull())
        }
        return findByOriginalPackage(packageName) ?: findByCustomPackage(packageName)
    }

    @Serializable
    data class StoreCatalogResponse(
        val generatedAt: String? = null,
        val apps: List<StoreCatalogItem> = emptyList()
    )

    @Serializable
    data class StoreCatalogItem(
        val originalPackageId: String,
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
        val changelog: String? = null,
        val deviceModels: List<String>
    ) {
        fun toEntity(): StoreCatalogEntry {
            require(PACKAGE_NAME.matches(originalPackageId))
            require(PACKAGE_NAME.matches(customPackageId))
            require(APK_NAME.matches(apkName) && ".." !in apkName)
            require(versionCode > 0 && sizeBytes > 0)
            require(downloadUrl.startsWith("https://") && iconUrl.startsWith("https://"))
            require(SHA256.matches(sha256))
            require(SHA256.matches(signerSha256))
            require(DEVICE_MODEL in deviceModels)
            return StoreCatalogEntry(
                originalPackageId = originalPackageId,
                customPackageId = customPackageId,
                title = title,
                summary = summary,
                developerName = developerName,
                iconUrl = iconUrl,
                versionCode = versionCode,
                versionName = versionName,
                apkName = apkName,
                downloadUrl = downloadUrl,
                sizeBytes = sizeBytes,
                sha256 = sha256.lowercase(),
                signerSha256 = signerSha256.lowercase(),
                changelog = changelog.orEmpty()
            )
        }
    }
}

class StoreCatalogUnavailableException(cause: Throwable?) :
    IllegalStateException("Store catalog is unavailable and has no valid cached snapshot", cause)

internal fun validateCatalogEntries(entries: List<StoreCatalogEntry>) {
    val packageOwners = mutableMapOf<String, Int>()
    entries.forEachIndexed { index, entry ->
        setOf(entry.originalPackageId, entry.customPackageId).forEach { packageName ->
            require(packageOwners.putIfAbsent(packageName, index) == null) {
                "Catalog package ID collides across entries: $packageName"
            }
        }
    }
}
