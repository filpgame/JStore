/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.commons

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aurora.store.R
import com.aurora.store.data.installer.jaecoo.JaecooPlanDetails
import com.aurora.store.data.installer.jaecoo.JaecooPlanDiagnostic
import com.aurora.store.data.installer.jaecoo.JaecooPlanResult
import com.aurora.store.data.room.catalog.StoreCatalogEntry

private const val TAG = "PremiumCatalogBlockedDialog"
private const val JCONFIG_PACKAGE = "com.frodrigues.jconfig"
private const val JCONFIG_PLAN_URI = "jconfig://settings/plan/purchase"

@Composable
fun PremiumCatalogBlockedDialog(
    entry: StoreCatalogEntry,
    details: JaecooPlanDetails,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val jconfigLauncher = remember(context) {
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse(JCONFIG_PLAN_URI)).apply {
            setPackage(JCONFIG_PACKAGE)
        }
        if (deepLink.resolveActivity(context.packageManager) != null) {
            deepLink
        } else {
            runCatching {
                context.packageManager.getLaunchIntentForPackage(JCONFIG_PACKAGE)
            }.getOrNull()
        }
    }
    val message = if (details.plan == JaecooPlanResult.FREE) {
        stringResource(R.string.store_catalog_premium_required_message, entry.title)
    } else {
        stringResource(R.string.store_catalog_premium_unavailable_message, entry.title)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.store_catalog_premium_required_title)) },
        text = {
            Column {
                Text(message)
                if (details.diagnostic != JaecooPlanDiagnostic.NONE) {
                    Text(
                        text = stringResource(
                            R.string.store_catalog_premium_diagnostic_code,
                            details.diagnostic.name
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(
                            R.string.store_catalog_premium_diagnostic_attempts,
                            details.attempts
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                details.exceptionSummary?.let { exceptionSummary ->
                    Text(
                        text = stringResource(
                            R.string.store_catalog_premium_diagnostic_exception,
                            exceptionSummary
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.store_catalog_premium_retry))
            }
        },
        dismissButton = jconfigLauncher?.let { launcher ->
            {
                TextButton(
                    onClick = {
                        onDismiss()
                        runCatching { context.startActivity(launcher) }
                            .onFailure { Log.e(TAG, "Failed to open JConfig", it) }
                    }
                ) {
                    Text(stringResource(R.string.store_catalog_premium_open_jconfig))
                }
            }
        }
    )
}
