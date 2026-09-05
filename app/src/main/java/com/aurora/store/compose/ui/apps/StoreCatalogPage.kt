/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.apps

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aurora.extensions.toast
import com.aurora.store.R
import com.aurora.store.compose.composable.ContainedLoadingIndicator
import com.aurora.store.compose.composable.Placeholder
import com.aurora.store.compose.composition.scaledDimensionResource
import com.aurora.store.compose.ui.commons.PremiumCatalogBlockedDialog
import com.aurora.store.data.helper.DownloadEnqueueResult
import com.aurora.store.data.model.DownloadStatus
import com.aurora.store.data.room.catalog.StoreCatalogEntry
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.CertUtil
import com.aurora.store.util.CommonUtil
import com.aurora.store.util.PackageUtil
import com.aurora.store.viewmodel.store.InstallResult
import com.aurora.store.viewmodel.store.StoreCatalogViewModel

@Composable
fun StoreCatalogPage(viewModel: StoreCatalogViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasError by viewModel.hasError.collectAsStateWithLifecycle()
    val installationRevision by viewModel.installationRevision.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var result by remember { mutableStateOf<InstallResult?>(null) }
    var premiumBlocked by remember {
        mutableStateOf<DownloadEnqueueResult.PremiumBlocked?>(null)
    }

    LaunchedEffect(viewModel) {
        viewModel.installFailed.collect { context.toast(R.string.store_catalog_error) }
    }
    LaunchedEffect(viewModel) {
        viewModel.installResult.collect { result = it }
    }
    LaunchedEffect(viewModel) {
        viewModel.premiumBlocked.collect { premiumBlocked = it }
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
        onUninstall = viewModel::uninstall,
        onRefresh = viewModel::refresh
    )

    result?.let { current ->
        InstallResultDialog(
            result = current,
            onRetry = {
                viewModel.retry(current.packageName)
                result = null
            },
            onDismiss = { result = null }
        )
    }

    premiumBlocked?.let { blocked ->
        PremiumCatalogBlockedDialog(
            entry = blocked.entry,
            details = blocked.details,
            onRetry = {
                premiumBlocked = null
                viewModel.install(blocked.entry)
            },
            onDismiss = { premiumBlocked = null }
        )
    }
}

@Composable
internal fun StoreCatalogContent(
    entries: List<StoreCatalogEntry>,
    downloads: Map<String, Download>,
    isLoading: Boolean,
    hasError: Boolean,
    installationRevision: Int,
    onInstall: (StoreCatalogEntry) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onUninstall: (StoreCatalogEntry) -> Unit,
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
                    scaledDimensionResource(R.dimen.spacing_small)
                )
            ) {
                itemsIndexed(
                    entries,
                    key = { _, entry -> entry.originalPackageId }
                ) { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier
                                .padding(
                                    horizontal = scaledDimensionResource(R.dimen.spacing_medium)
                                )
                                .semantics { testTag = "store_catalog_divider" }
                        )
                    }
                    StoreCatalogItem(
                        entry = entry,
                        download = downloads[entry.customPackageId],
                        installationRevision = installationRevision,
                        onInstall = { onInstall(entry) },
                        onCancel = { onCancel(entry.customPackageId) },
                        onRetry = { onRetry(entry.customPackageId) },
                        onUninstall = { onUninstall(entry) }
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
    onRetry: () -> Unit,
    onUninstall: () -> Unit
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
                horizontal = scaledDimensionResource(R.dimen.spacing_medium),
                vertical = scaledDimensionResource(R.dimen.spacing_small)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.iconUrl,
            contentDescription = null,
            modifier = Modifier.requiredSize(scaledDimensionResource(R.dimen.icon_size_medium))
        )
        Spacer(Modifier.width(scaledDimensionResource(R.dimen.spacing_medium)))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.isPremium) {
                    Spacer(Modifier.width(scaledDimensionResource(R.dimen.spacing_xsmall)))
                    Text(
                        text = stringResource(R.string.store_catalog_premium),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
        Spacer(Modifier.width(scaledDimensionResource(R.dimen.spacing_small)))
        CatalogActionArea(
            modifier = Modifier.widthIn(
                max = scaledDimensionResource(R.dimen.action_area_max_width)
            ),
            download = download,
            packageState = packageState,
            matchesCurrentArtifact = matchesCurrentArtifact,
            onInstall = onInstall,
            onCancel = onCancel,
            onRetry = onRetry,
            onUninstall = onUninstall
        )
    }
}

@Composable
private fun CatalogActionArea(
    modifier: Modifier = Modifier,
    download: Download?,
    packageState: CatalogPackageState,
    matchesCurrentArtifact: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onUninstall: () -> Unit
) {
    when {
        download?.status == DownloadStatus.INSTALLING -> {
            InstallingState(modifier = modifier, onCancel = onCancel)
        }
        download?.isActive == true -> {
            DownloadingState(modifier = modifier, download = download, onCancel = onCancel)
        }
        !packageState.hasValidSigner -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(
                scaledDimensionResource(R.dimen.spacing_xsmall)
            )
        ) {
            OutlinedButton(onClick = {}, enabled = false) {
                Text(stringResource(R.string.store_catalog_incompatible_signature))
            }
            OutlinedButton(onClick = onUninstall) {
                Text(stringResource(R.string.action_uninstall))
            }
        }
        download?.status == DownloadStatus.FAILED && matchesCurrentArtifact ->
            OutlinedButton(modifier = modifier, onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        packageState.isUpToDate -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(
                scaledDimensionResource(R.dimen.spacing_xsmall)
            )
        ) {
            OutlinedButton(onClick = {}, enabled = false) {
                Text(stringResource(R.string.store_catalog_installed))
            }
            OutlinedButton(onClick = onUninstall) {
                Text(stringResource(R.string.action_uninstall))
            }
        }
        else -> Button(modifier = modifier, onClick = onInstall) {
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

@Composable
private fun DownloadingState(
    modifier: Modifier = Modifier,
    download: Download,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val progressLabel = remember(
        download.status,
        download.progress,
        download.speed,
        download.timeRemaining
    ) {
        formatDownloadProgress(context, download)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(
            scaledDimensionResource(R.dimen.spacing_xsmall)
        )
    ) {
        LinearProgressIndicator(
            progress = { (download.progress.coerceIn(0, 100)) / 100f },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun InstallingState(modifier: Modifier = Modifier, onCancel: () -> Unit) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(
            scaledDimensionResource(R.dimen.spacing_xsmall)
        )
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.store_catalog_status_installing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun InstallResultDialog(result: InstallResult, onRetry: () -> Unit, onDismiss: () -> Unit) {
    when (result) {
        is InstallResult.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.store_catalog_install_success)) },
            text = { Text(result.displayName) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
        is InstallResult.Failure -> {
            val reason = result.reason
                ?: stringResource(R.string.store_catalog_install_no_reason)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.store_catalog_install_failure)) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(
                            scaledDimensionResource(R.dimen.spacing_small)
                        )
                    ) {
                        Text(result.displayName)
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

private fun formatDownloadProgress(context: android.content.Context, download: Download): String {
    val percent = download.progress.coerceIn(0, 100)
    val placeholder = context.getString(R.string.store_catalog_status_queued)
    return when (download.status) {
        DownloadStatus.QUEUED,
        DownloadStatus.PURCHASING -> placeholder
        else -> {
            val speed = download.speed
            val eta = download.timeRemaining
            when {
                speed > 0L && eta > 0L -> context.getString(
                    R.string.store_catalog_install_progress_with_eta,
                    percent,
                    Formatter.formatShortFileSize(context, speed),
                    CommonUtil.getETAString(context, eta)
                )
                speed > 0L -> context.getString(
                    R.string.store_catalog_install_progress_with_speed,
                    percent,
                    Formatter.formatShortFileSize(context, speed)
                )
                else -> context.getString(R.string.store_catalog_install_progress, percent)
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
