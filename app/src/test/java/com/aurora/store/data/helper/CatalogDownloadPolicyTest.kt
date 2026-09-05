/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.helper

import com.aurora.store.data.installer.jaecoo.JaecooPlanDetails
import com.aurora.store.data.installer.jaecoo.JaecooPlanDiagnostic
import com.aurora.store.data.installer.jaecoo.JaecooPlanResult
import com.aurora.store.data.room.catalog.StoreCatalogEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CatalogDownloadPolicyTest {
    @Test
    fun nonPremiumEntry_doesNotQueryEntitlement() = runBlocking {
        var calls = 0
        val policy = CatalogDownloadPolicy(
            planProvider = {
                calls++
                details(JaecooPlanResult.FREE)
            },
            flavor = "jaecoo"
        )

        val result = policy.evaluate(entry(isPremium = false))

        assertThat(result).isEqualTo(CatalogDownloadAccess.Allowed)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun premiumEntry_onNonJaecooFlavor_isAllowedWithoutEntitlementCheck() = runBlocking {
        var calls = 0
        val policy = CatalogDownloadPolicy(
            planProvider = {
                calls++
                details(JaecooPlanResult.FREE)
            },
            flavor = "vanilla"
        )

        val result = policy.evaluate(entry(isPremium = true))

        assertThat(result).isEqualTo(CatalogDownloadAccess.Allowed)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun premiumEntry_withTrialPlan_isAllowed() = runBlocking {
        val policy = CatalogDownloadPolicy(
            planProvider = { details(JaecooPlanResult.TRIAL) },
            flavor = "jaecoo"
        )

        assertThat(policy.evaluate(entry(isPremium = true)))
            .isEqualTo(CatalogDownloadAccess.Allowed)
    }

    @Test
    fun premiumEntry_withPremiumPlan_isAllowed() = runBlocking {
        val policy = CatalogDownloadPolicy(
            planProvider = { details(JaecooPlanResult.PREMIUM) },
            flavor = "jaecoo"
        )

        assertThat(policy.evaluate(entry(isPremium = true)))
            .isEqualTo(CatalogDownloadAccess.Allowed)
    }

    @Test
    fun premiumEntry_withFreePlan_isBlockedWithPlanDetails() = runBlocking {
        val expected = details(JaecooPlanResult.FREE, JaecooPlanDiagnostic.PLAN_FREE)
        val policy = CatalogDownloadPolicy(
            planProvider = { expected },
            flavor = "jaecoo"
        )

        val result = policy.evaluate(entry(isPremium = true))

        assertThat(result).isEqualTo(
            CatalogDownloadAccess.Blocked(entry(isPremium = true), expected)
        )
    }

    @Test
    fun premiumEntry_withUnavailablePlan_isBlocked() = runBlocking {
        val policy = CatalogDownloadPolicy(
            planProvider = { details(JaecooPlanResult.JCONFIG_UNAVAILABLE) },
            flavor = "jaecoo"
        )

        assertThat(policy.evaluate(entry(isPremium = true)))
            .isInstanceOf(CatalogDownloadAccess.Blocked::class.java)
    }

    @Test
    fun premiumEntry_whenPlanProviderThrows_isBlocked() = runBlocking {
        val policy = CatalogDownloadPolicy(
            planProvider = { error("bridge unavailable") },
            flavor = "jaecoo"
        )

        assertThat(policy.evaluate(entry(isPremium = true)))
            .isInstanceOf(CatalogDownloadAccess.Blocked::class.java)
    }

    private fun details(
        plan: JaecooPlanResult,
        diagnostic: JaecooPlanDiagnostic = JaecooPlanDiagnostic.NONE
    ) = JaecooPlanDetails(plan = plan, diagnostic = diagnostic)

    private fun entry(isPremium: Boolean) = StoreCatalogEntry(
        originalPackageId = "com.example.original",
        customPackageId = "com.example.custom",
        title = "Example",
        summary = "Example app",
        developerName = "Example",
        iconUrl = "https://example.com/icon.png",
        versionCode = 1,
        versionName = "1.0",
        apkName = "example.apk",
        downloadUrl = "https://example.com/example.apk",
        sizeBytes = 1_024,
        sha256 = "a".repeat(64),
        signerSha256 = "b".repeat(64),
        isPremium = isPremium,
        changelog = ""
    )
}
