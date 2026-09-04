/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composable

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.aurora.store.compose.composition.scaledDp

/** Material icon with a size that follows the app's screen-aware layout scale. */
@Composable
internal fun ScaledIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(24.scaledDp),
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
