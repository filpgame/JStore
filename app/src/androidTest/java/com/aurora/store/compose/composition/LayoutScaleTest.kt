/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.store.R
import com.aurora.store.compose.composable.ScaledIcon
import com.aurora.store.compose.theme.AuroraTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class LayoutScaleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun scaledDimensionResourceScalesWithLayoutScale() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutScale provides 1.5f) {
                Box(
                    modifier = Modifier
                        .testTag("scaled_card")
                        .size(scaledDimensionResource(R.dimen.icon_size_cluster))
                )
            }
        }

        composeTestRule.onNodeWithTag("scaled_card").assertWidthIsEqualTo(120.dp)
    }

    @Test
    fun auroraThemeScalesTypographyAndInteractiveTargets() {
        var bodyLargeSize = 0.sp
        var emphasizedHeadlineSize = 0.sp
        var minimumInteractiveSize = 0.dp

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutScale provides 1.5f) {
                AuroraTheme {
                    val currentBodyLargeSize = MaterialTheme.typography.bodyLarge.fontSize
                    val currentEmphasizedHeadlineSize =
                        MaterialTheme.typography.headlineLargeEmphasized.fontSize
                    val currentMinimumInteractiveSize =
                        LocalMinimumInteractiveComponentSize.current
                    SideEffect {
                        bodyLargeSize = currentBodyLargeSize
                        emphasizedHeadlineSize = currentEmphasizedHeadlineSize
                        minimumInteractiveSize = currentMinimumInteractiveSize
                    }
                    Text("Scaled text")
                    Box(
                        modifier = Modifier
                            .testTag("interactive_target")
                            .minimumInteractiveComponentSize()
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertThat(bodyLargeSize).isEqualTo(24.sp)
        assertThat(emphasizedHeadlineSize).isEqualTo(48.sp)
        assertThat(minimumInteractiveSize).isEqualTo(72.dp)
        composeTestRule.onNodeWithTag("interactive_target")
            .assertWidthIsEqualTo(72.dp)
            .assertHeightIsEqualTo(72.dp)
    }

    @Test
    fun scaledIconUsesScaledDefaultSize() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutScale provides 1.5f) {
                ScaledIcon(
                    painter = painterResource(R.drawable.ic_apps),
                    contentDescription = "scaled icon"
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("scaled icon")
            .assertWidthIsEqualTo(36.dp)
    }

    @Test
    fun scaledTextStyleScalesLineHeightAndLetterSpacing() {
        var scaledStyle = TextStyle()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutScale provides 1.5f) {
                val currentScaledStyle = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.5.sp
                ).scaled
                SideEffect {
                    scaledStyle = currentScaledStyle
                }
            }
        }
        composeTestRule.waitForIdle()

        assertThat(scaledStyle.fontSize).isEqualTo(24.sp)
        assertThat(scaledStyle.lineHeight).isEqualTo(36.sp)
        assertThat(scaledStyle.letterSpacing).isEqualTo(0.75.sp)
    }

    @Test
    fun auroraThemePreservesSystemFontScale() {
        var systemFontScale = 0f

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalLayoutScale provides 1.5f,
                LocalDensity provides Density(density = 1f, fontScale = 1.25f)
            ) {
                AuroraTheme {
                    val currentSystemFontScale = LocalDensity.current.fontScale
                    SideEffect { systemFontScale = currentSystemFontScale }
                    Text("System-scaled text")
                }
            }
        }
        composeTestRule.waitForIdle()

        assertThat(systemFontScale).isEqualTo(1.25f)
    }
}
