/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.helper

import com.aurora.store.data.installer.jaecoo.JaecooPlanDetails
import com.aurora.store.data.room.catalog.StoreCatalogEntry

sealed interface DownloadEnqueueResult {
    data object Enqueued : DownloadEnqueueResult

    data class PremiumBlocked(
        val entry: StoreCatalogEntry,
        val details: JaecooPlanDetails
    ) : DownloadEnqueueResult
}
