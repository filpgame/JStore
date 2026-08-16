/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.room.catalog

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "store_catalog_state")
data class StoreCatalogState(
    @PrimaryKey val id: Int = ID,
    val isValid: Boolean
) {
    companion object {
        const val ID = 1
    }
}
