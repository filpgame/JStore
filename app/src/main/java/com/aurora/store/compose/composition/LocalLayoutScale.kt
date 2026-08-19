/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal carrying a continuous UI scale factor derived from the device's
 * screen width (see [com.aurora.store.util.ScreenScale]). Default `1f` (no scaling).
 *
 * Provided once at the root of [com.aurora.store.ComposeActivity]. Composables that
 * want to scale with the host screen should read [LocalLayoutScale.current] via the
 * `scaledDp` / `scaled` extensions below — composables that ignore it are unaffected
 * by changes to this value.
 *
 * Uses [staticCompositionLocalOf] because the value is stable for the lifetime of
 * the activity (the activity is re-created on configuration changes that change
 * screen width, since `ComposeActivity` does not declare `android:configChanges` in
 * the manifest — keep that invariant; adding `screenSize|orientation` to the
 * manifest would silently break scale propagation here). The static form avoids
 * spurious recomposition when the value is stable.
 *
 * The scale is **multiplicative** on raw `dp` values and stacks with the active
 * `Density` (which converts `dp` → `px`): `finalPx = (rawDp * scale) * density`.
 * Do not combine `.scaledDp` with a manually pre-scaled value — it would double-scale.
 */
val LocalLayoutScale = staticCompositionLocalOf { 1f }

/** Returns the receiver (treated as raw `dp`) multiplied by [LocalLayoutScale] (e.g. `56.scaledDp`). */
val Int.scaledDp: Dp
    @Composable
    @ReadOnlyComposable
    get() = (this * LocalLayoutScale.current).dp

/** Returns the receiver (treated as raw `dp`) multiplied by [LocalLayoutScale] (e.g. `12.5.scaledDp`). */
val Float.scaledDp: Dp
    @Composable
    @ReadOnlyComposable
    get() = (this * LocalLayoutScale.current).dp

/** Scale an existing [Dp] value by [LocalLayoutScale] (e.g. `8.dp.scaled`). */
val Dp.scaled: Dp
    @Composable
    @ReadOnlyComposable
    get() = this * LocalLayoutScale.current

/** Scale a [TextUnit] (typically an `sp` literal) by [LocalLayoutScale]. */
val TextUnit.scaled: TextUnit
    @Composable
    @ReadOnlyComposable
    get() = this * LocalLayoutScale.current
