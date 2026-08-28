/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer.jaecoo

import com.aurora.store.data.installer.JaecooInstaller
import com.jaecoo.installer.bridge.IJaecooInstallerBridge
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Possible outcomes of a Jconfig plan-gate check. */
enum class JaecooPlanResult {
    /** User is on the Jconfig trial plan — allowed. */
    TRIAL,

    /** User has an active Premium subscription — allowed. */
    PREMIUM,

    /** User is on the Free plan — must be blocked from the store. */
    FREE,

    /** Jconfig is still resolving the plan — currently no decision. */
    LOADING,

    /** Jconfig cannot identify the device (no VIN/serial) — must be blocked. */
    IDENTITY_UNAVAILABLE,

    /** Jconfig service is not installed or bind-timeout fired — must be blocked. */
    JCONFIG_UNAVAILABLE,

    /** Bridge is older than [JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT] — must be blocked. */
    JCONFIG_OUTDATED,

    /** Bridge call failed with an unknown error or returned an unrecognised wire value. */
    ERROR
}

/** Only trial and premium plans can submit installations through the Jaecoo bridge. */
fun JaecooPlanResult.allowsJaecooInstall(): Boolean = when (this) {
    JaecooPlanResult.TRIAL, JaecooPlanResult.PREMIUM -> true
    JaecooPlanResult.FREE,
    JaecooPlanResult.LOADING,
    JaecooPlanResult.IDENTITY_UNAVAILABLE,
    JaecooPlanResult.JCONFIG_UNAVAILABLE,
    JaecooPlanResult.JCONFIG_OUTDATED,
    JaecooPlanResult.ERROR -> false
}

/**
 * Translates the wire-format string returned by `IJaecooInstallerBridge.getEntitlement()`
 * into the consumer-facing [JaecooPlanResult] used by the splash-screen gate.
 *
 * Pure function extracted so the mapping can be unit tested without the bridge binder.
 */
fun mapWireToResult(wire: String?): JaecooPlanResult = when (wire) {
    "trial" -> JaecooPlanResult.TRIAL
    "premium" -> JaecooPlanResult.PREMIUM
    "free" -> JaecooPlanResult.FREE
    "identity_unavailable" -> JaecooPlanResult.IDENTITY_UNAVAILABLE
    "loading" -> JaecooPlanResult.LOADING
    null -> JaecooPlanResult.JCONFIG_UNAVAILABLE
    else -> JaecooPlanResult.ERROR
}

/**
 * Splash-screen gate that queries the Jconfig installer bridge for the current plan.
 *
 * The gate runs only on the `jaecoo` product flavor (see the SplashScreen
 * LaunchedEffect). On other flavors the gate is a no-op and consumers should
 * skip [currentPlan] altogether.
 */
@Singleton
class JaecooPlanGate(
    private val bridgeSource: () -> IJaecooInstallerBridge?
) {
    @Inject constructor(installer: JaecooInstaller) : this(installer::currentBridge)

    /** Acquire the current plan over IPC. Always returns; never throws. */
    suspend fun currentPlan(): JaecooPlanResult = withContext(Dispatchers.IO) {
        val bridge = bridgeSource()
            ?: return@withContext JaecooPlanResult.JCONFIG_UNAVAILABLE
        val supportsEntitlement = runCatching {
            bridge.capabilities.serviceVersion >=
                JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT
        }.getOrDefault(false)
        if (!supportsEntitlement) return@withContext JaecooPlanResult.JCONFIG_OUTDATED
        val wire = runCatching { bridge.entitlement }.getOrNull()
        mapWireToResult(wire)
    }
}
