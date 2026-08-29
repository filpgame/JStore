/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer.jaecoo

import android.os.BadParcelableException
import android.os.DeadObjectException
import android.os.RemoteException
import com.aurora.store.data.installer.JaecooBridgeConnection
import com.aurora.store.data.installer.JaecooInstaller
import com.jaecoo.installer.bridge.IJaecooInstallerBridge
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/** Stable, privacy-safe code shown to users when the Jconfig bridge denies the splash gate. */
enum class JaecooPlanDiagnostic {
    NONE,
    PLAN_FREE,
    BIND_REJECTED,
    BIND_TIMEOUT,
    BIND_SECURITY_EXCEPTION,
    BIND_DEAD_OBJECT,
    BIND_REMOTE_EXCEPTION,
    BIND_PARCEL_EXCEPTION,
    BIND_EXCEPTION,
    CAPABILITIES_SECURITY_EXCEPTION,
    CAPABILITIES_DEAD_OBJECT,
    CAPABILITIES_REMOTE_EXCEPTION,
    CAPABILITIES_PARCEL_EXCEPTION,
    CAPABILITIES_EXCEPTION,
    SERVICE_VERSION_UNSUPPORTED,
    ENTITLEMENT_LOADING,
    IDENTITY_UNAVAILABLE,
    ENTITLEMENT_SECURITY_EXCEPTION,
    ENTITLEMENT_DEAD_OBJECT,
    ENTITLEMENT_REMOTE_EXCEPTION,
    ENTITLEMENT_PARCEL_EXCEPTION,
    ENTITLEMENT_NULL,
    ENTITLEMENT_UNKNOWN_VALUE,
    ENTITLEMENT_EXCEPTION;

    internal val retryable: Boolean
        get() = when (this) {
            BIND_REJECTED,
            BIND_TIMEOUT,
            BIND_DEAD_OBJECT,
            BIND_REMOTE_EXCEPTION,
            CAPABILITIES_DEAD_OBJECT,
            CAPABILITIES_REMOTE_EXCEPTION,
            ENTITLEMENT_DEAD_OBJECT,
            ENTITLEMENT_REMOTE_EXCEPTION -> true
            else -> false
        }
}

/** Result returned to the splash so it can render a support-friendly diagnostic. */
data class JaecooPlanDetails(
    val plan: JaecooPlanResult,
    val diagnostic: JaecooPlanDiagnostic = JaecooPlanDiagnostic.NONE,
    val exceptionSummary: String? = null,
    val attempts: Int = 1
)

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
    private val bridgeSource: () -> JaecooBridgeConnection,
    private val clearCachedBridge: () -> Unit = {},
    private val retryDelayMillis: Long = RETRY_DELAY_MILLIS
) {
    @Inject constructor(installer: JaecooInstaller) : this(
        bridgeSource = installer::currentBridgeConnection,
        clearCachedBridge = installer::clearCachedBridge
    )

    /** Acquire the current plan over IPC. Always returns; never throws. */
    suspend fun currentPlan(): JaecooPlanResult = withContext(Dispatchers.IO) {
        currentPlanAttempt().plan
    }

    /**
     * Acquires the plan and preserves the failed IPC boundary for the splash dialog.
     * One transient bridge failure is retried after a short delay so an app-start race does not
     * block a valid subscriber.
     */
    suspend fun currentPlanDetails(): JaecooPlanDetails = withContext(Dispatchers.IO) {
        val first = currentPlanAttempt()
        if (!first.diagnostic.retryable) return@withContext first

        clearCachedBridge()
        delay(retryDelayMillis)
        currentPlanAttempt().copy(attempts = 2)
    }

    private fun currentPlanAttempt(): JaecooPlanDetails = when (val connection = bridgeSource()) {
        is JaecooBridgeConnection.Connected -> queryEntitlement(connection.bridge)
        JaecooBridgeConnection.BindRejected -> unavailable(JaecooPlanDiagnostic.BIND_REJECTED)
        JaecooBridgeConnection.BindTimedOut -> unavailable(JaecooPlanDiagnostic.BIND_TIMEOUT)
        is JaecooBridgeConnection.BindFailed -> unavailable(
            diagnosticFor(Stage.BIND, connection.exception),
            connection.exception
        )
    }

    private fun queryEntitlement(bridge: IJaecooInstallerBridge): JaecooPlanDetails {
        val capabilities = try {
            bridge.capabilities
        } catch (exception: Exception) {
            return JaecooPlanDetails(
                plan = JaecooPlanResult.JCONFIG_OUTDATED,
                diagnostic = diagnosticFor(Stage.CAPABILITIES, exception),
                exceptionSummary = exception.summary()
            )
        }
        if (capabilities.serviceVersion < JaecooInstaller.MIN_SERVICE_VERSION_FOR_ENTITLEMENT) {
            return JaecooPlanDetails(
                plan = JaecooPlanResult.JCONFIG_OUTDATED,
                diagnostic = JaecooPlanDiagnostic.SERVICE_VERSION_UNSUPPORTED
            )
        }

        val wire = try {
            bridge.entitlement
        } catch (exception: Exception) {
            return unavailable(diagnosticFor(Stage.ENTITLEMENT, exception), exception)
        }
        return when (val plan = mapWireToResult(wire)) {
            JaecooPlanResult.ERROR -> JaecooPlanDetails(
                plan = plan,
                diagnostic = JaecooPlanDiagnostic.ENTITLEMENT_UNKNOWN_VALUE
            )
            JaecooPlanResult.IDENTITY_UNAVAILABLE -> JaecooPlanDetails(
                plan = plan,
                diagnostic = JaecooPlanDiagnostic.IDENTITY_UNAVAILABLE
            )
            JaecooPlanResult.LOADING -> JaecooPlanDetails(
                plan = plan,
                diagnostic = JaecooPlanDiagnostic.ENTITLEMENT_LOADING
            )
            JaecooPlanResult.JCONFIG_UNAVAILABLE -> unavailable(
                JaecooPlanDiagnostic.ENTITLEMENT_NULL
            )
            JaecooPlanResult.FREE -> JaecooPlanDetails(
                plan = plan,
                diagnostic = JaecooPlanDiagnostic.PLAN_FREE
            )
            else -> JaecooPlanDetails(plan = plan)
        }
    }

    private fun unavailable(
        diagnostic: JaecooPlanDiagnostic,
        exception: Exception? = null
    ): JaecooPlanDetails = JaecooPlanDetails(
        plan = JaecooPlanResult.JCONFIG_UNAVAILABLE,
        diagnostic = diagnostic,
        exceptionSummary = exception?.summary()
    )

    private fun diagnosticFor(stage: Stage, exception: Exception): JaecooPlanDiagnostic =
        when (stage) {
            Stage.BIND -> when (exception) {
                is SecurityException -> JaecooPlanDiagnostic.BIND_SECURITY_EXCEPTION
                is DeadObjectException -> JaecooPlanDiagnostic.BIND_DEAD_OBJECT
                is RemoteException -> JaecooPlanDiagnostic.BIND_REMOTE_EXCEPTION
                is BadParcelableException -> JaecooPlanDiagnostic.BIND_PARCEL_EXCEPTION
                else -> JaecooPlanDiagnostic.BIND_EXCEPTION
            }
            Stage.CAPABILITIES -> when (exception) {
                is SecurityException -> JaecooPlanDiagnostic.CAPABILITIES_SECURITY_EXCEPTION
                is DeadObjectException -> JaecooPlanDiagnostic.CAPABILITIES_DEAD_OBJECT
                is RemoteException -> JaecooPlanDiagnostic.CAPABILITIES_REMOTE_EXCEPTION
                is BadParcelableException -> JaecooPlanDiagnostic.CAPABILITIES_PARCEL_EXCEPTION
                else -> JaecooPlanDiagnostic.CAPABILITIES_EXCEPTION
            }
            Stage.ENTITLEMENT -> when (exception) {
                is SecurityException -> JaecooPlanDiagnostic.ENTITLEMENT_SECURITY_EXCEPTION
                is DeadObjectException -> JaecooPlanDiagnostic.ENTITLEMENT_DEAD_OBJECT
                is RemoteException -> JaecooPlanDiagnostic.ENTITLEMENT_REMOTE_EXCEPTION
                is BadParcelableException -> JaecooPlanDiagnostic.ENTITLEMENT_PARCEL_EXCEPTION
                else -> JaecooPlanDiagnostic.ENTITLEMENT_EXCEPTION
            }
        }

    private fun Exception.summary(): String {
        val type = javaClass.simpleName.ifBlank { "Exception" }
        val message = message
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(MAX_EXCEPTION_MESSAGE_LENGTH)
            ?.takeIf(String::isNotEmpty)
        return listOfNotNull(type, message).joinToString(": ")
    }

    private enum class Stage {
        BIND,
        CAPABILITIES,
        ENTITLEMENT
    }

    private companion object {
        const val RETRY_DELAY_MILLIS = 250L
        const val MAX_EXCEPTION_MESSAGE_LENGTH = 240
    }
}
