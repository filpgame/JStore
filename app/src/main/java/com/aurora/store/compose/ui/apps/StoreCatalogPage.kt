/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aurora.extensions.toast
import com.aurora.store.R
import com.aurora.store.compose.composable.ContainedLoadingIndicator
import com.aurora.store.compose.composable.Placeholder
import com.aurora.store.data.model.DownloadStatus
import com.aurora.store.data.room.catalog.StoreCatalogEntry
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.CertUtil
import com.aurora.store.util.CommonUtil
import com.aurora.store.util.PackageUtil
import com.aurora.store.viewmodel.store.StoreCatalogViewModel

@Composable
fun StoreCatalogPage(viewModel: StoreCatalogViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasError by viewModel.hasError.collectAsStateWithLifecycle()
    val installationRevision by viewModel.installationRevision.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.installFailed.collect { context.toast(R.string.store_catalog_error) }
    }

    StoreCatalogContent(
        entries = entries,
        downloads = downloads.associateBy { it.packageName },
        isLoading = isLoading,
        hasError = hasError,
        installationRevision = installationRevision,
        onInstall = viewModel::install,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh
    )
}

@Composable
private fun StoreCatalogContent(
    entries: List<StoreCatalogEntry>,
    downloads: Map<String, Download>,
    isLoading: Boolean,
    hasError: Boolean,
    installationRevision: Int,
    onInstall: (StoreCatalogEntry) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRefresh: () -> Unit
) {
    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = isLoading,
        onRefresh = onRefresh
    ) {
        when {
            isLoading && entries.isEmpty() -> ContainedLoadingIndicator()
            hasError && entries.isEmpty() -> Placeholder(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(R.drawable.ic_apps_outage),
                message = stringResource(R.string.store_catalog_error),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRefresh
            )
            entries.isEmpty() -> Placeholder(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(R.drawable.ic_apps_outage),
                message = stringResource(R.string.store_catalog_empty)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.spacing_small)
                )
            ) {
                items(entries, key = StoreCatalogEntry::originalPackageId) { entry ->
                    StoreCatalogItem(
                        entry = entry,
                        download = downloads[entry.customPackageId],
                        installationRevision = installationRevision,
                        onInstall = { onInstall(entry) },
                        onCancel = { onCancel(entry.customPackageId) },
                        onRetry = { onRetry(entry.customPackageId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreCatalogItem(
    entry: StoreCatalogEntry,
    download: Download?,
    installationRevision: Int,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val packageState = remember(entry, installationRevision) {
        val isInstalled = PackageUtil.isInstalled(context, entry.customPackageId)
        CatalogPackageState(
            isInstalled = isInstalled,
            isUpToDate = PackageUtil.isInstalled(
                context,
                entry.customPackageId,
                entry.versionCode
            ),
            hasValidSigner = !isInstalled ||
                entry.signerSha256 in
                CertUtil.getCertificateSha256Hashes(context, entry.customPackageId)
        )
    }
    val matchesCurrentArtifact = download?.matches(entry) == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(R.dimen.spacing_medium),
                vertical = dimensionResource(R.dimen.spacing_small)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.iconUrl,
            contentDescription = null,
            modifier = Modifier.requiredSize(dimensionResource(R.dimen.icon_size_medium))
        )
        Spacer(Modifier.width(dimensionResource(R.dimen.spacing_medium)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${entry.versionName}  •  ${CommonUtil.addSiPrefix(entry.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(dimensionResource(R.dimen.spacing_small)))
        when {
            download?.isActive == true -> OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
            !packageState.hasValidSigner -> OutlinedButton(onClick = {}, enabled = false) {
                Text(stringResource(R.string.store_catalog_incompatible_signature))
            }
            download?.status == DownloadStatus.FAILED && matchesCurrentArtifact ->
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry))
                }
            packageState.isUpToDate -> OutlinedButton(onClick = {}, enabled = false) {
                Text(stringResource(R.string.store_catalog_installed))
            }
            else -> Button(onClick = onInstall) {
                Text(
                    stringResource(
                        if (packageState.isInstalled) {
                            R.string.action_update
                        } else {
                            R.string.action_install
                        }
                    )
                )
            }
        }
    }
}

private data class CatalogPackageState(
    val isInstalled: Boolean,
    val isUpToDate: Boolean,
    val hasValidSigner: Boolean
)

internal fun Download.matches(entry: StoreCatalogEntry): Boolean {
    val file = fileList.singleOrNull() ?: return false
    return versionCode == entry.versionCode &&
        file.name == entry.apkName &&
        file.url == entry.downloadUrl &&
        file.sha256.equals(entry.sha256, ignoreCase = true)
}
