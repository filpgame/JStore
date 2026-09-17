/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.providers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JaecooBaseProfileSelectionTest {

    @Test
    fun api30_selectsAndroid11Arm64PocoProfile() {
        assertThat(jaecooBaseProfileNameForSdk(30)).isEqualTo("reloaded_beryllium")
    }

    @Test
    fun api29_keepsTheNokiaProfile() {
        assertThat(jaecooBaseProfileNameForSdk(29)).isEqualTo("Nokia 1.3")
    }

    @Test
    fun api31And32_useTheNewestAvailableAndroid11Profile() {
        assertThat(jaecooBaseProfileNameForSdk(31)).isEqualTo("reloaded_beryllium")
        assertThat(jaecooBaseProfileNameForSdk(32)).isEqualTo("reloaded_beryllium")
    }
}
