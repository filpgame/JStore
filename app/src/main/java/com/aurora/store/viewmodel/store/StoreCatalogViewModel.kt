/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.store

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.extensions.TAG
import com.aurora.store.AuroraApp
import com.aurora.store.data.StoreCatalogRepository
import com.aurora.store.data.event.InstallerEvent
import com.aurora.store.data.helper.DownloadEnqueueResult
import com.aurora.store.data.helper.DownloadHelper
import com.aurora.store.data.installer.AppInstaller
import com.aurora.store.data.room.catalog.StoreCatalogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Terminal outcome of an install attempt surfaced once to the UI as a one-shot event.
 */
sealed class InstallResult {
    abstract val packageName: String
    abstract val displayName: String

    data class Success(
        override val packageName: String,
        override val displayName: String
    ) : InstallResult()

    data class Failure(
        override val packageName: String,
        override val displayName: String,
        val reason: String?
    ) : InstallResult()

    companion object {
        /**
         * Maps a terminal [InstallerEvent] into the user-facing [InstallResult]. Other
         * [InstallerEvent] subtypes (e.g. [InstallerEvent.Installing]) are not terminal
         * and must not be mapped here.
         */
        fun fromEvent(event: InstallerEvent, displayName: String): InstallResult = when (event) {
            is InstallerEvent.Installed -> Success(event.packageName, displayName)
            is InstallerEvent.Failed -> Failure(
                packageName = event.packageName,
                displayName = displayName,
                reason = event.error ?: event.extra
            )
            else -> throw IllegalArgumentException("Cannot map non-terminal event: $event")
        }
    }
}

@HiltViewModel
class StoreCatalogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StoreCatalogRepository,
    private val downloadHelper: DownloadHelper
) : ViewModel() {
    val entries = repository.entries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val downloads = downloadHelper.downloadsList

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError = _hasError.asStateFlow()

    private val _installationRevision = MutableStateFlow(0)
    val installationRevision = _installationRevision.asStateFlow()

    private val _installFailed = MutableSharedFlow<Unit>()
    val installFailed = _installFailed.asSharedFlow()

    private val _installResult = MutableSharedFlow<InstallResult>(extraBufferCapacity = 1)
    val installResult = _installResult.asSharedFlow()

    private val _premiumBlocked = MutableSharedFlow<DownloadEnqueueResult.PremiumBlocked>(
        extraBufferCapacity = 1
    )
    val premiumBlocked = _premiumBlocked.asSharedFlow()

    private val refreshGate = AtomicBoolean(false)

    init {
        refresh()
        AuroraApp.events.installerEvent
            .filter { it is InstallerEvent.Installed || it is InstallerEvent.Uninstalled }
            .onEach { _installationRevision.value++ }
            .launchIn(viewModelScope)
        AuroraApp.events.installerEvent
            .filter { it is InstallerEvent.Installed || it is InstallerEvent.Failed }
            .onEach { event ->
                val displayName = displayNameFor(event.packageName)
                _installResult.emit(InstallResult.fromEvent(event, displayName))
            }
            .launchIn(viewModelScope)
    }

    private fun displayNameFor(packageName: String): String =
        downloads.value.firstOrNull { it.packageName == packageName }?.displayName ?: packageName

    fun refresh() {
        if (!refreshGate.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = repository.refresh()
                _hasError.value = result.isFailure && !repository.hasValidSnapshot()
            } finally {
                _isLoading.value = false
                refreshGate.set(false)
            }
        }
    }

    fun install(entry: StoreCatalogEntry) {
        viewModelScope.launch {
            try {
                when (val result = downloadHelper.enqueueStoreCatalog(entry)) {
                    DownloadEnqueueResult.Enqueued -> Unit
                    is DownloadEnqueueResult.PremiumBlocked -> _premiumBlocked.emit(result)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to enqueue catalog download", exception)
                _installFailed.emit(Unit)
            }
        }
    }

    fun cancel(packageName: String) {
        viewModelScope.launch { downloadHelper.cancelDownload(packageName) }
    }

    fun retry(packageName: String) {
        viewModelScope.launch {
            when (val result = downloadHelper.retryDownload(packageName)) {
                DownloadEnqueueResult.Enqueued -> Unit
                is DownloadEnqueueResult.PremiumBlocked -> _premiumBlocked.emit(result)
            }
        }
    }

    fun uninstall(entry: StoreCatalogEntry) {
        AppInstaller.uninstall(context, entry.customPackageId)
    }
}
