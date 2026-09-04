/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.util.lerp

/**
 * Continuous UI scale factor keyed off the device's current screen width in dp.
 *
 * Linear interpolation between anchor points. The anchors deliberately span
 * phone / tablet / automotive / ultrawide so the same APK works across Jaecoo's
 * 9"–15" infotainment variants and future Chery/Omoda/Tiggo screens. Phone
 * orientation is handled separately through `smallestScreenWidthDp`, so ordinary
 * phones stay at 1.0f.
 *
 * Why not `WindowSizeClass`? Material's buckets (Compact/Medium/Expanded) are
 * coarse and don't cover vertical automotive (e.g. Tesla-style 1080×1920) or
 * ultrawide dashboards — both common in cars. A continuous scale adapts cleanly.
 */
object ScreenScale {

    private const val PHONE_SMALLEST_WIDTH_DP = 600

    private data class Anchor(val widthDp: Float, val scale: Float)

    private val anchors = listOf(
        // (widthDp, scale): phone → tablet → automotive → ultrawide
        Anchor(360f, 1.00f), // typical phone portrait
        Anchor(600f, 1.15f), // 7" tablet / small car screen
        Anchor(800f, 1.30f), // 9-10" car / large tablet
        Anchor(1080f, 1.45f), // 12-15" automotive
        Anchor(1600f, 1.60f) // ultrawide / dual-screen dashboards
    )

    /**
     * Returns the scale factor for the given screen width in dp. Clamps to the
     * first/last anchor outside the configured range.
     */
    fun forWidth(widthDp: Int): Float {
        val w = widthDp.toFloat()
        val first = anchors.first()
        val last = anchors.last()
        if (w <= first.widthDp) return first.scale
        if (w >= last.widthDp) return last.scale

        for (i in 0 until anchors.size - 1) {
            val lo = anchors[i]
            val hi = anchors[i + 1]
            if (w in lo.widthDp..hi.widthDp) {
                val t = (w - lo.widthDp) / (hi.widthDp - lo.widthDp)
                return lerp(lo.scale, hi.scale, t)
            }
        }
        return 1f // defensive: unreachable given the bounds above
    }

    /**
     * Returns the width-based scale while keeping phones at the platform default in landscape.
     * Android's `smallestScreenWidthDp` is stable across orientation changes, so a phone with a
     * wide landscape viewport does not get mistaken for a tablet or automotive display.
     */
    internal fun forConfiguration(screenWidthDp: Int, smallestScreenWidthDp: Int): Float =
        if (smallestScreenWidthDp < PHONE_SMALLEST_WIDTH_DP) {
            1f
        } else {
            forWidth(screenWidthDp)
        }
}

/**
 * Returns the scale factor for the current [LocalConfiguration] — provided once
 * at the root of [com.aurora.store.ComposeActivity] via [LocalLayoutScale].
 * The smallest width protects phones from being scaled up in landscape.
 *
 * Named `currentScreenScale` (not `rememberScreenScale`) because it does not
 * retain state — it simply reads [LocalConfiguration] and computes a derived
 * value, matching the `LocalConfiguration.current` naming convention.
 */
@Composable
@ReadOnlyComposable
fun currentScreenScale(): Float {
    val cfg = LocalConfiguration.current
    return ScreenScale.forConfiguration(cfg.screenWidthDp, cfg.smallestScreenWidthDp)
}
