/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.room

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreCatalogMigrationTest {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(12) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )
        helper.writableDatabase
    }

    @After
    fun teardown() {
        helper.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migration12To13CreatesCatalogSnapshotSchema() {
        val database = helper.writableDatabase
        assertThat(database.version).isEqualTo(12)

        MigrationHelper.MIGRATION_12_13.migrate(database)

        assertThat(database.tableNames()).containsAtLeast(
            "store_catalog",
            "store_catalog_state"
        )
        assertThat(database.columnNames("store_catalog")).containsAtLeast(
            "originalPackageId",
            "customPackageId",
            "sha256",
            "signerSha256"
        )
        database.execSQL(
            "INSERT INTO store_catalog_state (id, isValid) VALUES (1, 1)"
        )
        database.query("SELECT isValid FROM store_catalog_state WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }
    }

    @Test
    fun migration13To14AddsPremiumFlagWithFalseDefault() {
        val database = helper.writableDatabase

        MigrationHelper.MIGRATION_12_13.migrate(database)
        MigrationHelper.MIGRATION_13_14.migrate(database)

        assertThat(database.columnNames("store_catalog")).contains("isPremium")
        database.query("PRAGMA table_info(`store_catalog`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
            var premiumDefault: String? = null
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "isPremium") {
                    premiumDefault = cursor.getString(defaultIndex)
                }
            }
            assertThat(premiumDefault).isEqualTo("0")
        }
    }

    private fun SupportSQLiteDatabase.tableNames(): List<String> =
        query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun SupportSQLiteDatabase.columnNames(tableName: String): List<String> =
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    companion object {
        private const val DATABASE_NAME = "store-catalog-migration-test.db"
    }
}
