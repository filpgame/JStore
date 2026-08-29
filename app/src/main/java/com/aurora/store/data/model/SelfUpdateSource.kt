/*
 * SPDX-FileCopyrightText: 2026 JConfig
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.model

import com.aurora.Constants.FLAVOUR_JAECOO
import com.aurora.Constants.FLAVOUR_PRELOAD
import com.aurora.Constants.FLAVOUR_VANILLA
import com.aurora.Constants.UPDATE_URL_JSTORE
import com.aurora.Constants.UPDATE_URL_NIGHTLY
import com.aurora.Constants.UPDATE_URL_VANILLA

internal enum class SelfUpdateSource(val url: String) {
    AURORA_RELEASE(UPDATE_URL_VANILLA),
    AURORA_NIGHTLY(UPDATE_URL_NIGHTLY),
    JCONFIG_RELEASE(UPDATE_URL_JSTORE)
}

internal fun resolveSelfUpdateSource(flavour: String, buildType: BuildType): SelfUpdateSource? =
    when (buildType) {
        BuildType.RELEASE -> when (flavour) {
            FLAVOUR_JAECOO -> SelfUpdateSource.JCONFIG_RELEASE
            FLAVOUR_VANILLA, FLAVOUR_PRELOAD -> SelfUpdateSource.AURORA_RELEASE
            else -> null
        }
        BuildType.NIGHTLY -> when (flavour) {
            FLAVOUR_VANILLA, FLAVOUR_PRELOAD -> SelfUpdateSource.AURORA_NIGHTLY
            else -> null
        }
        BuildType.DEBUG -> null
    }
