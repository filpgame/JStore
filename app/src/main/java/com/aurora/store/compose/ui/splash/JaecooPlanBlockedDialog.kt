/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.splash

import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.aurora.store.R
import com.aurora.store.data.installer.jaecoo.JaecooPlanResult

private const val JCONFIG_PACKAGE = "com.frodrigues.jconfig"
private const val TAG = "JaecooPlanBlockedDialog"

/**
 * Blocking dialog shown when the Jconfig plan-gate denies store access.
 *
 * Non-dismissable (both click-outside and back-press are disabled) so the user cannot
 * bypass the gate by tapping outside; dismissing requires an explicit retry (re-checks
 * the plan) or "Open Jconfig" which launches the Jconfig package.
 */
@Composable
fun JaecooPlanBlockedDialog(
    result: JaecooPlanResult,
    fromSavedSession: Boolean,
    onRetry: () -> Unit,
    onOpenJconfig: () -> Unit
) {
    val context = LocalContext.current
    val message = when (result) {
        JaecooPlanResult.FREE -> if (fromSavedSession) {
            stringResource(R.string.jconfig_gate_message_session)
        } else {
            stringResource(R.string.jconfig_gate_message_fresh)
        }
        JaecooPlanResult.IDENTITY_UNAVAILABLE -> stringResource(
            R.string.jconfig_gate_message_identity
        )
        JaecooPlanResult.LOADING -> stringResource(R.string.jconfig_gate_message_loading)
        JaecooPlanResult.JCONFIG_OUTDATED -> stringResource(R.string.jconfig_gate_message_outdated)
        JaecooPlanResult.JCONFIG_UNAVAILABLE,
        JaecooPlanResult.ERROR -> stringResource(R.string.jconfig_gate_message_unavailable)
        JaecooPlanResult.TRIAL,
        JaecooPlanResult.PREMIUM -> return // gate passed; dialog should not be rendered
    }

    val jconfigLauncher = remember(context) {
        runCatching {
            context.packageManager.getLaunchIntentForPackage(JCONFIG_PACKAGE)
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = { /* non-dismissable: see DialogProperties */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(stringResource(R.string.jconfig_gate_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.jconfig_gate_retry))
            }
        },
        dismissButton = if (jconfigLauncher != null) {
            {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(jconfigLauncher)
                        }.onFailure { Log.e(TAG, "Failed to launch Jconfig", it) }
                        onOpenJconfig()
                    }
                ) {
                    Text(stringResource(R.string.jconfig_gate_open_jconfig))
                }
            }
        } else {
            null
        }
    )
}
