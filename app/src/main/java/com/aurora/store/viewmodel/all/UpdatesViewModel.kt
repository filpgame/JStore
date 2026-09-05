/*
 * Aurora Store
 *  Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
 *
 *  Aurora Store is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Aurora Store is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.aurora.store.viewmodel.all

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.store.data.ExodusRepository
import com.aurora.store.data.StoreCatalogRepository
import com.aurora.store.data.helper.DownloadEnqueueResult
import com.aurora.store.data.helper.DownloadHelper
import com.aurora.store.data.helper.UpdateHelper
import com.aurora.store.data.model.ExodusTracker
import com.aurora.store.data.room.update.Update
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    val updateHelper: UpdateHelper,
    private val downloadHelper: DownloadHelper,
    private val exodusRepository: ExodusRepository,
    private val storeCatalogRepository: StoreCatalogRepository
) : ViewModel() {

    var updateAllEnqueued: Boolean = false

    val downloadsList get() = downloadHelper.downloadsList
    val updates get() = updateHelper.updates
    val ignoredUpdates get() = updateHelper.ignoredUpdates

    val fetchingUpdates = updateHelper.isCheckingUpdates

    private val _premiumBlocked = MutableSharedFlow<DownloadEnqueueResult.PremiumBlocked>(
        extraBufferCapacity = 1
    )
    val premiumBlocked = _premiumBlocked.asSharedFlow()

    private data class PendingPremiumUpdate(
        val update: Update,
        val blocked: DownloadEnqueueResult.PremiumBlocked
    )

    private val pendingPremiumUpdates = ArrayDeque<PendingPremiumUpdate>()
    private var activePremiumUpdate: PendingPremiumUpdate? = null

    fun fetchUpdates() {
        updateHelper.checkUpdatesNow()
    }

    fun unignore(packageName: String) {
        viewModelScope.launch { updateHelper.unignore(packageName) }
    }

    fun download(update: Update) {
        viewModelScope.launch { processUpdate(update) }
    }

    suspend fun getNewTrackers(
        packageName: String,
        installedVersionCode: Long
    ): List<ExodusTracker> = exodusRepository.getNewTrackers(packageName, installedVersionCode)

    suspend fun isCatalogPackage(packageName: String): Boolean =
        storeCatalogRepository.findByCustomPackage(packageName) != null

    fun downloadAll(updates: List<Update>) {
        viewModelScope.launch {
            updates.forEach { processUpdate(it) }
        }
    }

    fun retryPremiumDownload() {
        val pending = activePremiumUpdate ?: return
        activePremiumUpdate = null
        viewModelScope.launch { processUpdate(pending.update) }
    }

    private suspend fun processUpdate(update: Update) {
        when (val result = downloadHelper.enqueueUpdate(update)) {
            DownloadEnqueueResult.Enqueued -> showNextPremiumBlock()
            is DownloadEnqueueResult.PremiumBlocked -> {
                pendingPremiumUpdates.addLast(PendingPremiumUpdate(update, result))
                showNextPremiumBlock()
            }
        }
    }

    private suspend fun showNextPremiumBlock() {
        if (activePremiumUpdate != null || pendingPremiumUpdates.isEmpty()) return
        activePremiumUpdate = pendingPremiumUpdates.removeFirst()
        _premiumBlocked.emit(checkNotNull(activePremiumUpdate).blocked)
    }

    fun cancelDownload(packageName: String) {
        viewModelScope.launch { downloadHelper.cancelDownload(packageName) }
    }

    fun cancelAll() {
        viewModelScope.launch { downloadHelper.cancelAll(true) }
    }
}
