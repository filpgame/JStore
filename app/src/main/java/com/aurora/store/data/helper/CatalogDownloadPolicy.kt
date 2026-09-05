/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.helper

import com.aurora.store.BuildConfig
import com.aurora.store.data.installer.jaecoo.JaecooPlanDetails
import com.aurora.store.data.installer.jaecoo.JaecooPlanDiagnostic
import com.aurora.store.data.installer.jaecoo.JaecooPlanGate
import com.aurora.store.data.installer.jaecoo.JaecooPlanResult
import com.aurora.store.data.installer.jaecoo.allowsPremiumDownload
import com.aurora.store.data.room.catalog.StoreCatalogEntry
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface CatalogDownloadAccess {
    data object Allowed : CatalogDownloadAccess

    data class Blocked(
        val entry: StoreCatalogEntry,
        val details: JaecooPlanDetails
    ) : CatalogDownloadAccess
}

/** Applies the JConfig entitlement only to Premium apps in the Jaecoo catalog. */
class CatalogDownloadPolicy internal constructor(
    private val planProvider: suspend () -> JaecooPlanDetails,
    private val flavor: String
) {
    @Inject
    constructor(planGate: JaecooPlanGate) : this(
        planProvider = planGate::currentPlanDetails,
        flavor = BuildConfig.FLAVOR
    )

    suspend fun evaluate(entry: StoreCatalogEntry): CatalogDownloadAccess {
        if (flavor != "jaecoo" || !entry.isPremium) {
            return CatalogDownloadAccess.Allowed
        }

        val details = try {
            planProvider()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            JaecooPlanDetails(
                plan = JaecooPlanResult.JCONFIG_UNAVAILABLE,
                diagnostic = JaecooPlanDiagnostic.ENTITLEMENT_EXCEPTION,
                exceptionSummary = exception.summary()
            )
        }
        return if (details.plan.allowsPremiumDownload()) {
            CatalogDownloadAccess.Allowed
        } else {
            CatalogDownloadAccess.Blocked(entry = entry, details = details)
        }
    }

    private fun Throwable.summary(): String {
        val type = javaClass.simpleName.ifBlank { "Exception" }
        val message = message
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(240)
            ?.takeIf(String::isNotEmpty)
        return listOfNotNull(type, message).joinToString(": ")
    }
}
