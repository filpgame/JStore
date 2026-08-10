/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.RemoteException
import com.aurora.store.data.installer.base.InstallerBase
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.PathUtil
import com.aurora.store.util.Preferences
import com.aurora.store.util.Preferences.PREFERENCE_AUTO_DELETE
import com.jaecoo.installer.bridge.IJaecooInstallerBridge
import com.jaecoo.installer.bridge.IJaecooInstallerCallback
import com.jaecoo.installer.bridge.InstallArtifact
import com.jaecoo.installer.bridge.InstallArtifactGroup
import com.jaecoo.installer.bridge.InstallRequest
import com.jaecoo.installer.bridge.OperationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Privileged installer client used exclusively by the Jaecoo product flavor. */
@Singleton
class JaecooInstaller @Inject constructor(
    @ApplicationContext context: Context
) : InstallerBase(context) {
    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val ledger = JaecooInstallLedger(appContext)

    @Volatile private var bridge: IJaecooInstallerBridge? = null

    init {
        executor.execute(::recoverPending)
    }

    override fun install(download: Download) {
        super.install(download)
        executor.execute { submit(download) }
    }

    override fun cancelInstall(packageName: String) {
        executor.execute {
            ledger.recordsForPackage(packageName).forEach { record ->
                record.operationId?.let { operationId ->
                    runCatching {
                        val service = checkNotNull(connect()) { "Jaecoo bridge unavailable" }
                        service.cancel(operationId)
                        terminal(record.attemptId)
                    }
                        .onFailure { postBridgeError(packageName, BridgeFailure.CANCEL, it) }
                }
            }
        }
    }

    override fun clearQueue() {
        ledger.allRecords().forEach { cancelInstall(it.packageName) }
        super.clearQueue()
    }

    private fun submit(download: Download) {
        val attemptId = UUID.randomUUID().toString()
        val request = try {
            request(download, attemptId)
        } catch (exception: Exception) {
            postBridgeError(download.packageName, BridgeFailure.PREPARE, exception)
            return
        }
        // This is deliberately before submit: a process death must not orphan URI grants.
        try {
            ledger.save(JaecooInstallLedger.Record.from(request, download))
        } catch (exception: Throwable) {
            revoke(request)
            postBridgeError(download.packageName, BridgeFailure.PREPARE, exception)
            return
        }
        val service = try {
            connect()
        } catch (exception: SecurityException) {
            postBridgeError(download.packageName, BridgeFailure.SECURITY, exception)
            terminal(attemptId)
            return
        } catch (exception: Throwable) {
            postBridgeError(download.packageName, BridgeFailure.UNAVAILABLE, exception)
            terminal(attemptId)
            return
        } ?: run {
            postBridgeError(download.packageName, BridgeFailure.UNAVAILABLE, null)
            terminal(attemptId)
            return
        }
        val capabilities = try {
            service.capabilities
        } catch (exception: SecurityException) {
            postBridgeError(download.packageName, BridgeFailure.SECURITY, exception)
            terminal(attemptId)
            return
        } catch (exception: Exception) {
            postBridgeError(download.packageName, BridgeFailure.HANDSHAKE, exception)
            terminal(attemptId)
            return
        }
        if (capabilities.protocolVersion != PROTOCOL_VERSION ||
            capabilities.serviceVersion < MIN_SERVICE_VERSION ||
            capabilities.androidSdk < MIN_ANDROID_SDK ||
            capabilities.servicePackage != BRIDGE_PACKAGE
        ) {
            postError(
                download.packageName,
                BridgeFailure.INCOMPATIBLE.message,
                capabilities.toString()
            )
            terminal(attemptId)
            return
        }
        if (!capabilities.isDeviceOwner) {
            postError(
                download.packageName,
                BridgeFailure.DEVICE_OWNER.message,
                capabilities.toString()
            )
            terminal(attemptId)
            return
        }
        try {
            val operationId = service.submit(request, callbackFor(download.packageName, attemptId))
            if (operationId != attemptId) {
                runCatching { service.cancel(operationId) }
                postError(
                    download.packageName,
                    BridgeFailure.INCOMPATIBLE.message,
                    "Bridge returned an unexpected operation id"
                )
                terminal(attemptId)
            }
        } catch (exception: SecurityException) {
            postBridgeError(download.packageName, BridgeFailure.SECURITY, exception)
            terminal(attemptId)
        } catch (exception: IllegalArgumentException) {
            postBridgeError(download.packageName, BridgeFailure.SUBMIT, exception)
            terminal(attemptId)
        } catch (exception: RemoteException) {
            // A transport loss may happen after jconfig accepted the deterministic
            // attempt id. Preserve the ledger and URI grants for recovery.
            postBridgeError(download.packageName, BridgeFailure.SUBMIT, exception)
        } catch (exception: Exception) {
            // The Binder call may have reached jconfig before the transport failed. The
            // operation id is the persisted attempt id, so recovery can observe it safely.
            postBridgeError(download.packageName, BridgeFailure.SUBMIT, exception)
        }
    }

    private fun recoverPending() {
        ledger.allRecords().forEach { record ->
            val operationId = record.operationId
            if (operationId == null) {
                // submit never completed, so no service operation exists to recover.
                terminal(record.attemptId)
                return@forEach
            }
            val service = runCatching { connect() }.getOrNull() ?: return@forEach
            val callback = callbackFor(record.packageName, record.attemptId)
            runCatching { service.observe(operationId, callback) }
                .onFailure {
                    runCatching { service.getStatus(operationId)?.let(callback::onStatus) }
                }
        }
    }

    private fun callbackFor(packageName: String, attemptId: String) =
        object : IJaecooInstallerCallback.Stub() {
            override fun onStatus(status: OperationStatus) {
                if (status.state !in TERMINAL_STATES) return
                if (status.state == STATE_SUCCESS) {
                    ledger.find(attemptId)?.let(::onInstallationSuccess)
                } else {
                    postError(
                        packageName,
                        status.message ?: BridgeFailure.PACKAGE_INSTALLER.message,
                        "errorCode=${status.errorCode}"
                    )
                }
                terminal(attemptId)
            }
        }

    private fun onInstallationSuccess(record: JaecooInstallLedger.Record) {
        notifyInstallation(appContext, record.displayName, record.packageName)
        if (Preferences.getBoolean(appContext, PREFERENCE_AUTO_DELETE)) {
            PathUtil.getAppDownloadDir(appContext, record.packageName, record.versionCode)
                .deleteRecursively()
        }
    }

    private fun terminal(attemptId: String) {
        ledger.remove(attemptId)?.uris?.forEach { uri ->
            runCatching {
                appContext.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun connect(): IJaecooInstallerBridge? {
        bridge?.let { return it }
        val latch = CountDownLatch(1)
        val timedOut = AtomicBoolean(false)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (timedOut.get()) {
                    runCatching { appContext.unbindService(this) }
                    return
                }
                bridge = IJaecooInstallerBridge.Stub.asInterface(binder)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                bridge = null
            }
        }
        val intent = Intent(BRIDGE_ACTION).setPackage(BRIDGE_PACKAGE)
        if (!appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) return null
        val connected = try {
            latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!connected) {
            timedOut.set(true)
            bridge = null
            runCatching { appContext.unbindService(connection) }
            return null
        }
        return bridge
    }

    private fun request(download: Download, attemptId: String): InstallRequest {
        val grantedUris = mutableListOf<Uri>()
        fun artifact(file: File): InstallArtifact {
            val uri = getUri(file)
            appContext.grantUriPermission(
                BRIDGE_PACKAGE,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            grantedUris += uri
            return InstallArtifact(uri, file.name, file.length(), sha256(file))
        }
        fun group(packageName: String, version: Long, files: List<File>) =
            InstallArtifactGroup(packageName, version, files.map(::artifact))
        try {
            val app = group(
                download.packageName,
                download.versionCode,
                getFiles(download.packageName, download.versionCode)
            )
            val libraries = download.sharedLibs.map { library ->
                group(
                    library.packageName,
                    library.versionCode,
                    getFiles(download.packageName, download.versionCode, library.packageName)
                )
            }
            return InstallRequest(
                PROTOCOL_VERSION,
                attemptId,
                fingerprint(app, libraries),
                app,
                libraries
            )
        } catch (exception: Throwable) {
            grantedUris.forEach { uri ->
                runCatching {
                    appContext.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            throw exception
        }
    }

    private fun revoke(request: InstallRequest) {
        (request.app.artifacts + request.sharedLibraries.flatMap { it.artifacts })
            .forEach { artifact ->
                runCatching {
                    appContext.revokeUriPermission(
                        artifact.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
    }

    private fun postBridgeError(
        packageName: String,
        failure: BridgeFailure,
        exception: Throwable?
    ) {
        postError(packageName, failure.message, exception?.stackTraceToString())
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fingerprint(
        app: InstallArtifactGroup,
        libraries: List<InstallArtifactGroup>
    ): String = JaecooFingerprint.calculate(
        listOf(app).plus(libraries).map { group ->
            JaecooFingerprint.Group(
                packageName = group.packageName,
                versionCode = group.versionCode,
                artifacts = group.artifacts.map { artifact ->
                    JaecooFingerprint.Artifact(
                        name = artifact.name,
                        size = artifact.size,
                        sha256 = artifact.sha256
                    )
                }
            )
        }
    )

    private enum class BridgeFailure(val message: String) {
        UNAVAILABLE("Jaecoo installer bridge unavailable"),
        HANDSHAKE("Jaecoo bridge handshake failed"),
        INCOMPATIBLE("Jaecoo bridge protocol is incompatible"),
        DEVICE_OWNER("Jaecoo installer lost Device Owner state"),
        SECURITY("Jaecoo bridge signature permission rejected the caller"),
        PREPARE("Failed to prepare Jaecoo install artifacts"),
        SUBMIT("Jaecoo bridge submit failed"),
        CANCEL("Jaecoo bridge cancellation failed"),
        PACKAGE_INSTALLER("Jaecoo PackageInstaller operation failed")
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        const val BRIDGE_ACTION = "com.jaecoo.installer.bridge.BIND"
        const val BRIDGE_PACKAGE = "com.frodrigues.jconfig"
        const val MIN_SERVICE_VERSION = 1
        const val MIN_ANDROID_SDK = 29
        const val STATE_SUCCESS = 3
        const val STATE_FAILURE = 4
        const val STATE_CANCELLED = 5
        const val BIND_TIMEOUT_SECONDS = 5L
        val TERMINAL_STATES = setOf(STATE_SUCCESS, STATE_FAILURE, STATE_CANCELLED)
    }
}

/** Persistent, multi-operation index. URI grants are retained until a terminal status is seen. */
private class JaecooInstallLedger(context: Context) {
    private val preferences = context.getSharedPreferences(
        "jaecoo_install_ledger",
        Context.MODE_PRIVATE
    )

    data class Record(
        val attemptId: String,
        val operationId: String?,
        val packageName: String,
        val versionCode: Long,
        val displayName: String,
        val fingerprint: String,
        val uris: List<Uri>
    ) {
        companion object {
            fun from(request: InstallRequest, download: Download) = Record(
                attemptId = request.attemptId,
                operationId = request.attemptId,
                packageName = request.app.packageName,
                versionCode = download.versionCode,
                displayName = download.displayName,
                fingerprint = request.fingerprint,
                uris = (request.app.artifacts + request.sharedLibraries.flatMap { it.artifacts })
                    .map { it.uri }
            )
        }
    }

    fun save(record: Record) {
        check(preferences.edit().putString("record.${record.attemptId}", encode(record)).commit()) {
            "Failed to persist Jaecoo install ledger"
        }
    }

    fun remove(attemptId: String): Record? = find(attemptId)?.also {
        preferences.edit().remove("record.$attemptId").apply()
    }

    fun allRecords(): List<Record> = preferences.all.filterKeys { it.startsWith("record.") }
        .values.mapNotNull { it as? String }.mapNotNull(::decode)

    fun recordsForPackage(packageName: String) = allRecords().filter {
        it.packageName == packageName
    }

    fun find(attemptId: String) = preferences.getString("record.$attemptId", null)?.let(::decode)

    private fun encode(record: Record) = listOf(
        record.attemptId,
        record.operationId.orEmpty(),
        record.packageName,
        record.versionCode.toString(),
        record.displayName,
        record.fingerprint,
        record.uris.joinToString("\u001f")
    ).joinToString("\u001e")

    private fun decode(value: String): Record? {
        val fields = value.split("\u001e", limit = 7)
        return fields.takeIf { it.size == 7 && it[0].isNotBlank() }?.let {
            Record(
                attemptId = it[0],
                operationId = it[1].ifBlank { null },
                packageName = it[2],
                versionCode = it[3].toLongOrNull() ?: return null,
                displayName = it[4],
                fingerprint = it[5],
                uris = it[6].split("\u001f").filter(String::isNotBlank).map(Uri::parse)
            )
        }
    }
}
