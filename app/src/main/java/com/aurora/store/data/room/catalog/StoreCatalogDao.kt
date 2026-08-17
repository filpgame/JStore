/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.room.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreCatalogDao {
    @Query("SELECT * FROM store_catalog ORDER BY title COLLATE NOCASE")
    fun entries(): Flow<List<StoreCatalogEntry>>

    @Query("SELECT * FROM store_catalog ORDER BY title COLLATE NOCASE")
    suspend fun getEntries(): List<StoreCatalogEntry>

    @Query("SELECT * FROM store_catalog WHERE originalPackageId = :packageName LIMIT 1")
    suspend fun findByOriginalPackage(packageName: String): StoreCatalogEntry?

    @Query("SELECT * FROM store_catalog WHERE customPackageId = :packageName LIMIT 1")
    suspend fun findByCustomPackage(packageName: String): StoreCatalogEntry?

    @Query("SELECT isValid FROM store_catalog_state WHERE id = 1 LIMIT 1")
    suspend fun hasValidSnapshot(): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<StoreCatalogEntry>)

    @Query("DELETE FROM store_catalog")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(state: StoreCatalogState)

    @Transaction
    suspend fun replaceAll(entries: List<StoreCatalogEntry>) {
        deleteAll()
        insertAll(entries)
        setState(StoreCatalogState(isValid = true))
    }
}
