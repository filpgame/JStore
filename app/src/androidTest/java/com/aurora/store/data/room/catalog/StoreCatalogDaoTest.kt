/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.room.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.store.data.room.AuroraDatabase
import com.aurora.store.data.room.account.AccountConverter
import com.aurora.store.data.room.download.DownloadConverter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreCatalogDaoTest {
    private lateinit var database: AuroraDatabase
    private lateinit var dao: StoreCatalogDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuroraDatabase::class.java)
            .addTypeConverter(DownloadConverter(Json { ignoreUnknownKeys = true }))
            .addTypeConverter(AccountConverter())
            .allowMainThreadQueries()
            .build()
        dao = database.storeCatalogDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun emptyReplacementIsPersistedAsValidSnapshot() = runBlocking {
        assertThat(dao.hasValidSnapshot()).isNull()

        dao.replaceAll(emptyList())

        assertThat(dao.hasValidSnapshot()).isTrue()
        assertThat(dao.getEntries()).isEmpty()
    }
}
