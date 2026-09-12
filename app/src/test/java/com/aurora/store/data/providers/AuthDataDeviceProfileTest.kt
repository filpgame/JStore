/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.providers

import com.google.common.truth.Truth.assertThat
import java.util.Properties
import org.junit.Test

class AuthDataDeviceProfileTest {

    @Test
    fun missingSavedProfileIsRejected() {
        assertThat(authDataMatchesDeviceProfile(null, profile("30"))).isFalse()
    }

    @Test
    fun outdatedSavedProfileIsRejectedAfterUpgrade() {
        assertThat(authDataMatchesDeviceProfile(profile("29"), profile("30"))).isFalse()
    }

    @Test
    fun matchingSavedProfileIsAccepted() {
        assertThat(authDataMatchesDeviceProfile(profile("30"), profile("30"))).isTrue()
    }

    private fun profile(sdk: String) = Properties().apply {
        setProperty("Build.VERSION.SDK_INT", sdk)
    }
}
