/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer.jaecoo

import com.aurora.store.data.installer.JaecooInstaller
import com.google.common.truth.Truth.assertThat
import com.jaecoo.installer.bridge.IJaecooInstallerBridge
import com.jaecoo.installer.bridge.InstallerCapabilities
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class JaecooPlanGateTest {
    // ---- pure mapping ----------------------------------------------------

    @Test
    fun mapWireToResult_givenTrialWire_returnsTrial() {
        assertThat(mapWireToResult("trial")).isEqualTo(JaecooPlanResult.TRIAL)
    }

    @Test
    fun mapWireToResult_givenPremiumWire_returnsPremium() {
        assertThat(mapWireToResult("premium")).isEqualTo(JaecooPlanResult.PREMIUM)
    }

    @Test
    fun mapWireToResult_givenFreeWire_returnsFree() {
        assertThat(mapWireToResult("free")).isEqualTo(JaecooPlanResult.FREE)
    }

    @Test
    fun mapWireToResult_givenIdentityUnavailableWire_returnsIdentityUnavailable() {
        assertThat(mapWireToResult("identity_unavailable"))
            .isEqualTo(JaecooPlanResult.IDENTITY_UNAVAILABLE)
    }

    @Test
    fun mapWireToResult_givenLoadingWire_returnsLoading() {
        assertThat(mapWireToResult("loading")).isEqualTo(JaecooPlanResult.LOADING)
    }

    @Test
    fun mapWireToResult_givenNullBridge_returnsJconfigUnavailable() {
        assertThat(mapWireToResult(null)).isEqualTo(JaecooPlanResult.JCONFIG_UNAVAILABLE)
    }

    @Test
    fun mapWireToResult_givenUnknownWire_returnsError() {
        assertThat(mapWireToResult("not-a-known-state")).isEqualTo(JaecooPlanResult.ERROR)
    }

    // ---- currentPlan() integration ---------------------------------------

    @Test
    fun currentPlan_givenNullBridgeSource_returnsJconfigUnavailable() = runBlocking {
        val gate = JaecooPlanGate(bridgeSource = { null })

        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.JCONFIG_UNAVAILABLE)
    }

    @Test
    fun currentPlan_givenBridgeOlderThanEntitlementMin_returnsJconfigOutdated() = runBlocking {
        val bridge = fakeBridge(
            serviceVersion = JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT - 1,
            entitlementWire = "premium"
        )
        val gate = JaecooPlanGate(bridgeSource = { bridge })

        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.JCONFIG_OUTDATED)
    }

    @Test
    fun currentPlan_givenBridgeAtEntitlementMinAndPremiumWire_returnsPremium() = runBlocking {
        val bridge = fakeBridge(
            serviceVersion = JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT,
            entitlementWire = "premium"
        )
        val gate = JaecooPlanGate(bridgeSource = { bridge })

        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.PREMIUM)
    }

    @Test
    fun currentPlan_givenBridgeAtEntitlementMinAndFreeWire_returnsFree() = runBlocking {
        val bridge = fakeBridge(
            serviceVersion = JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT,
            entitlementWire = "free"
        )
        val gate = JaecooPlanGate(bridgeSource = { bridge })

        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.FREE)
    }

    @Test
    fun currentPlan_givenCapabilitiesCallThrows_returnsJconfigOutdated() = runBlocking {
        val bridge = fakeBridge(
            capabilitiesBehavior = { throw IllegalStateException("boom") },
            entitlementWire = "premium"
        )
        val gate = JaecooPlanGate(bridgeSource = { bridge })

        // Capabilities failure is treated as "version unknown" → safer to block with OUTDATED.
        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.JCONFIG_OUTDATED)
    }

    @Test
    fun currentPlan_givenEntitlementCallThrows_returnsJconfigUnavailable() = runBlocking {
        val bridge = fakeBridge(
            serviceVersion = JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT,
            entitlementBehavior = { throw IllegalStateException("boom") }
        )
        val gate = JaecooPlanGate(bridgeSource = { bridge })

        // AIDL wraps binder failures as RuntimeException for the client, so the gate's
        // runCatching converts any failure to null → JCONFIG_UNAVAILABLE. The ERROR case
        // is only reached for an unrecognised wire value (covered by mapWireToResult tests).
        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.JCONFIG_UNAVAILABLE)
    }
}

/**
 * Build an AIDL-style bridge stub via java.lang.reflect.Proxy.
 *
 * Cannot use `IJaecooInstallerBridge.Stub` directly — its constructor calls `Binder.attachInterface`,
 * which is not mocked in unit tests and throws `RuntimeException`.
 */
private fun fakeBridge(
    serviceVersion: Int = JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT,
    entitlementWire: String? = null,
    capabilitiesBehavior: () -> InstallerCapabilities = {
        InstallerCapabilities(
            /* protocolVersion = */ 1,
            serviceVersion,
            /* androidSdk = */ 33,
            /* isDeviceOwner = */ true,
            /* servicePackage = */ "com.frodrigues.jconfig"
        )
    },
    entitlementBehavior: () -> String = { entitlementWire ?: error("not set") }
): IJaecooInstallerBridge = Proxy.newProxyInstance(
    IJaecooInstallerBridge::class.java.classLoader,
    arrayOf(IJaecooInstallerBridge::class.java),
    InvocationHandler { _, method: Method, _ ->
        when (method.name) {
            "getCapabilities" -> capabilitiesBehavior()
            "getEntitlement" -> entitlementBehavior()
            "toString" -> "FakeBridge"
            "hashCode" -> 0
            "equals" -> false
            else -> throw UnsupportedOperationException("Not stubbed: ${method.name}")
        }
    }
) as IJaecooInstallerBridge
