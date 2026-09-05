/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer.jaecoo

import android.os.RemoteException
import com.aurora.store.data.installer.JaecooBridgeConnection
import com.aurora.store.data.installer.JaecooInstaller
import com.google.common.truth.Truth.assertThat
import com.jaecoo.installer.bridge.IJaecooInstallerBridge
import com.jaecoo.installer.bridge.InstallerCapabilities
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Test

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

    @Test
    fun freePlan_doesNotAllowPremiumDownload() {
        assertThat(JaecooPlanResult.FREE.allowsPremiumDownload()).isFalse()
    }

    @Test
    fun trialPlan_allowsPremiumDownload() {
        assertThat(JaecooPlanResult.TRIAL.allowsPremiumDownload()).isTrue()
    }

    @Test
    fun premiumPlan_allowsPremiumDownload() {
        assertThat(JaecooPlanResult.PREMIUM.allowsPremiumDownload()).isTrue()
    }

    // ---- currentPlan() integration ---------------------------------------

    @Test
    fun currentPlan_givenBindRejected_doesNotRetryOutsideSplash() = runBlocking {
        var attempts = 0
        val gate = JaecooPlanGate(
            bridgeSource = {
                attempts += 1
                JaecooBridgeConnection.BindRejected
            },
            retryDelayMillis = 0
        )

        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.JCONFIG_UNAVAILABLE)
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun currentPlanDetails_givenBridgeIsUnavailableOnce_retriesBeforeBlockingAccess() =
        runBlocking {
            val bridge = fakeBridge(entitlementWire = "premium")
            var attempts = 0
            var clearedConnections = 0
            val gate = JaecooPlanGate(
                bridgeSource = {
                    attempts += 1
                    if (attempts == 1) {
                        JaecooBridgeConnection.BindRejected
                    } else {
                        JaecooBridgeConnection.Connected(bridge)
                    }
                },
                clearCachedBridge = { clearedConnections += 1 },
                retryDelayMillis = 0
            )

            assertThat(gate.currentPlanDetails().plan).isEqualTo(JaecooPlanResult.PREMIUM)
            assertThat(attempts).isEqualTo(2)
            assertThat(clearedConnections).isEqualTo(1)
        }

    @Test
    fun currentPlan_givenBridgeOlderThanEntitlementMin_returnsJconfigOutdated() = runBlocking {
        val bridge = fakeBridge(
            serviceVersion = JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT - 1,
            entitlementWire = "premium"
        )
        val gate = JaecooPlanGate(bridgeSource = { JaecooBridgeConnection.Connected(bridge) })

        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.JCONFIG_OUTDATED)
    }

    @Test
    fun currentPlan_givenBridgeAtEntitlementMinAndPremiumWire_returnsPremium() = runBlocking {
        val bridge = fakeBridge(
            serviceVersion = JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT,
            entitlementWire = "premium"
        )
        val gate = JaecooPlanGate(bridgeSource = { JaecooBridgeConnection.Connected(bridge) })

        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.PREMIUM)
    }

    @Test
    fun currentPlan_givenBridgeAtEntitlementMinAndFreeWire_returnsFree() = runBlocking {
        val bridge = fakeBridge(
            serviceVersion = JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT,
            entitlementWire = "free"
        )
        val gate = JaecooPlanGate(bridgeSource = { JaecooBridgeConnection.Connected(bridge) })

        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.FREE)
    }

    @Test
    fun currentPlanDetails_givenFreeWire_exposesPlanFreeDiagnostic() = runBlocking {
        val bridge = fakeBridge(entitlementWire = "free")
        val gate = JaecooPlanGate(bridgeSource = { JaecooBridgeConnection.Connected(bridge) })

        val details = gate.currentPlanDetails()

        assertThat(details.plan).isEqualTo(JaecooPlanResult.FREE)
        assertThat(details.diagnostic).isEqualTo(JaecooPlanDiagnostic.PLAN_FREE)
    }

    @Test
    fun currentPlan_givenCapabilitiesCallThrows_returnsJconfigOutdated() = runBlocking {
        val bridge = fakeBridge(
            capabilitiesBehavior = { throw IllegalStateException("boom") },
            entitlementWire = "premium"
        )
        val gate = JaecooPlanGate(bridgeSource = { JaecooBridgeConnection.Connected(bridge) })

        // Capabilities failure is treated as "version unknown" → safer to block with OUTDATED.
        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.JCONFIG_OUTDATED)
    }

    @Test
    fun currentPlan_givenEntitlementCallThrows_returnsJconfigUnavailable() = runBlocking {
        val bridge = fakeBridge(
            serviceVersion = JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT,
            entitlementBehavior = { throw IllegalStateException("boom") }
        )
        val gate = JaecooPlanGate(bridgeSource = { JaecooBridgeConnection.Connected(bridge) })

        // A failed entitlement transaction blocks access; its technical cause is available
        // through currentPlanDetails(), while currentPlan() preserves the installer contract.
        assertThat(gate.currentPlan()).isEqualTo(JaecooPlanResult.JCONFIG_UNAVAILABLE)
    }

    @Test
    fun currentPlanDetails_givenEntitlementException_exposesSanitizedTechnicalDetails() =
        runBlocking {
            val bridge = fakeBridge(
                entitlementBehavior = {
                    throw IllegalStateException("Service failed\nwhile loading entitlement")
                }
            )
            val gate = JaecooPlanGate(
                bridgeSource = { JaecooBridgeConnection.Connected(bridge) },
                retryDelayMillis = 0
            )

            val details = gate.currentPlanDetails()

            assertThat(details.plan).isEqualTo(JaecooPlanResult.JCONFIG_UNAVAILABLE)
            assertThat(details.diagnostic).isEqualTo(
                JaecooPlanDiagnostic.ENTITLEMENT_EXCEPTION
            )
            assertThat(details.exceptionSummary)
                .isEqualTo("IllegalStateException: Service failed while loading entitlement")
        }

    @Test
    fun currentPlanDetails_givenFirstBindTimeout_retriesAndKeepsSuccessfulPlan() = runBlocking {
        val bridge = fakeBridge(entitlementWire = "premium")
        var attempts = 0
        var clearedConnections = 0
        val gate = JaecooPlanGate(
            bridgeSource = {
                attempts += 1
                if (attempts == 1) {
                    JaecooBridgeConnection.BindTimedOut
                } else {
                    JaecooBridgeConnection.Connected(bridge)
                }
            },
            clearCachedBridge = { clearedConnections += 1 },
            retryDelayMillis = 0
        )

        val details = gate.currentPlanDetails()

        assertThat(details.plan).isEqualTo(JaecooPlanResult.PREMIUM)
        assertThat(details.diagnostic).isEqualTo(JaecooPlanDiagnostic.NONE)
        assertThat(details.attempts).isEqualTo(2)
        assertThat(clearedConnections).isEqualTo(1)
    }

    @Test
    fun currentPlanDetails_givenBindSecurityException_doesNotRetryOrHideTheReason() = runBlocking {
        var attempts = 0
        val gate = JaecooPlanGate(
            bridgeSource = {
                attempts += 1
                JaecooBridgeConnection.BindFailed(SecurityException("signature mismatch"))
            },
            retryDelayMillis = 0
        )

        val details = gate.currentPlanDetails()

        assertThat(details.plan).isEqualTo(JaecooPlanResult.JCONFIG_UNAVAILABLE)
        assertThat(details.diagnostic).isEqualTo(JaecooPlanDiagnostic.BIND_SECURITY_EXCEPTION)
        assertThat(details.exceptionSummary).isEqualTo("SecurityException: signature mismatch")
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun currentPlanDetails_givenGenericBindException_doesNotRetry() = runBlocking {
        var attempts = 0
        val gate = JaecooPlanGate(
            bridgeSource = {
                attempts += 1
                JaecooBridgeConnection.BindFailed(IllegalStateException("bind failed"))
            },
            retryDelayMillis = 0
        )

        val details = gate.currentPlanDetails()

        assertThat(details.diagnostic).isEqualTo(JaecooPlanDiagnostic.BIND_EXCEPTION)
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun currentPlanDetails_givenBindRemoteException_retriesWithFreshBridge() = runBlocking {
        val bridge = fakeBridge(entitlementWire = "premium")
        var attempts = 0
        var clearedConnections = 0
        val gate = JaecooPlanGate(
            bridgeSource = {
                attempts += 1
                if (attempts == 1) {
                    JaecooBridgeConnection.BindFailed(RemoteException("service died"))
                } else {
                    JaecooBridgeConnection.Connected(bridge)
                }
            },
            clearCachedBridge = { clearedConnections += 1 },
            retryDelayMillis = 0
        )

        val details = gate.currentPlanDetails()

        assertThat(details.plan).isEqualTo(JaecooPlanResult.PREMIUM)
        assertThat(details.attempts).isEqualTo(2)
        assertThat(clearedConnections).isEqualTo(1)
    }

    @Test
    fun currentPlanDetails_givenEntitlementRemoteException_retriesWithFreshBridge() = runBlocking {
        val failedBridge = fakeBridge(
            entitlementBehavior = {
                throw RemoteException("service died")
            }
        )
        val recoveredBridge = fakeBridge(entitlementWire = "premium")
        var attempts = 0
        var clearedConnections = 0
        val gate = JaecooPlanGate(
            bridgeSource = {
                attempts += 1
                JaecooBridgeConnection.Connected(
                    if (attempts == 1) failedBridge else recoveredBridge
                )
            },
            clearCachedBridge = { clearedConnections += 1 },
            retryDelayMillis = 0
        )

        val details = gate.currentPlanDetails()

        assertThat(details.plan).isEqualTo(JaecooPlanResult.PREMIUM)
        assertThat(details.attempts).isEqualTo(2)
        assertThat(clearedConnections).isEqualTo(1)
    }

    @Test
    fun currentPlanDetails_givenTwoTransientFailures_retriesOnlyOnce() = runBlocking {
        var attempts = 0
        var clearedConnections = 0
        val gate = JaecooPlanGate(
            bridgeSource = {
                attempts += 1
                JaecooBridgeConnection.BindTimedOut
            },
            clearCachedBridge = { clearedConnections += 1 },
            retryDelayMillis = 0
        )

        val details = gate.currentPlanDetails()

        assertThat(details.plan).isEqualTo(JaecooPlanResult.JCONFIG_UNAVAILABLE)
        assertThat(details.diagnostic).isEqualTo(JaecooPlanDiagnostic.BIND_TIMEOUT)
        assertThat(details.attempts).isEqualTo(2)
        assertThat(attempts).isEqualTo(2)
        assertThat(clearedConnections).isEqualTo(1)
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
            /* protocolVersion = */
            1,
            serviceVersion,
            /* androidSdk = */
            33,
            /* isDeviceOwner = */
            true,
            /* servicePackage = */
            "com.frodrigues.jconfig"
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
