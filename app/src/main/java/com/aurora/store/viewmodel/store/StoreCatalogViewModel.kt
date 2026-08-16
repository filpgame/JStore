/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.store.data.StoreCatalogRepository
import com.aurora.store.data.helper.DownloadHelper
import com.aurora.store.data.room.catalog.StoreCatalogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        refresh()
    }

    fun refresh() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.refresh()
            _hasError.value = result.isFailure && !repository.hasValidSnapshot()
            _isLoading.value = false
        }
    }

    fun install(entry: StoreCatalogEntry) {
        viewModelScope.launch { downloadHelper.enqueueStoreCatalog(entry) }
    }

    fun cancel(packageName: String) {
        viewModelScope.launch { downloadHelper.cancelDownload(packageName) }
    }

    fun retry(packageName: String) {
        viewModelScope.launch { downloadHelper.retryDownload(packageName) }
    }
}
