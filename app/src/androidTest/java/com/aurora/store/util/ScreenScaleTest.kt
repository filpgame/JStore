/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScreenScaleTest {

    @Test
    fun clampsPhoneAndUltrawideWidths() {
        assertThat(ScreenScale.forWidth(320)).isEqualTo(1f)
        assertThat(ScreenScale.forWidth(360)).isEqualTo(1f)
        assertThat(ScreenScale.forWidth(1600)).isEqualTo(1.6f)
        assertThat(ScreenScale.forWidth(1920)).isEqualTo(1.6f)
    }

    @Test
    fun keepsPhoneLandscapeAtPlatformScale() {
        assertThat(ScreenScale.forConfiguration(800, 360)).isEqualTo(1f)
        assertThat(ScreenScale.forConfiguration(411, 411)).isEqualTo(1f)
    }

    @Test
    fun interpolatesCurrentAutomotiveWidth() {
        assertThat(ScreenScale.forConfiguration(1440, 1440))
            .isWithin(0.0001f)
            .of(1.5538461f)
    }

    @Test
    fun scalesWideAutomotiveDisplayUsingItsViewportWidth() {
        assertThat(ScreenScale.forConfiguration(1920, 720)).isEqualTo(1.6f)
    }
}
