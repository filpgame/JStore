/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.store

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.extensions.TAG
import com.aurora.store.AuroraApp
import com.aurora.store.data.StoreCatalogRepository
import com.aurora.store.data.event.InstallerEvent
import com.aurora.store.data.helper.DownloadHelper
import com.aurora.store.data.room.catalog.StoreCatalogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class StoreCatalogViewModel @Inject constructor(
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

    private val refreshGate = AtomicBoolean(false)

    init {
        refresh()
        AuroraApp.events.installerEvent
            .filter { it is InstallerEvent.Installed || it is InstallerEvent.Uninstalled }
            .onEach { _installationRevision.value++ }
            .launchIn(viewModelScope)
    }

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
                downloadHelper.enqueueStoreCatalog(entry)
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
        viewModelScope.launch { downloadHelper.retryDownload(packageName) }
    }
}
