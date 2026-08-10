/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JaecooFingerprintTest {
    @Test
    fun calculate_isStableAcrossGroupAndArtifactOrdering() {
        val app = JaecooFingerprint.Group(
            packageName = "com.example.app",
            versionCode = 12,
            artifacts = listOf(
                JaecooFingerprint.Artifact("split_config.en.apk", 2, "bbb"),
                JaecooFingerprint.Artifact("base.apk", 1, "aaa")
            )
        )
        val library = JaecooFingerprint.Group(
            packageName = "com.example.library",
            versionCode = 3,
            artifacts = listOf(JaecooFingerprint.Artifact("base.apk", 4, "ccc"))
        )

        val first = JaecooFingerprint.calculate(listOf(app, library))
        val reordered = JaecooFingerprint.calculate(
            listOf(library, app.copy(artifacts = app.artifacts.reversed()))
        )

        assertThat(reordered).isEqualTo(first)
        assertThat(first).hasLength(64)
    }

    @Test
    fun calculate_changesWhenArtifactMetadataChanges() {
        val group = JaecooFingerprint.Group(
            packageName = "com.example.app",
            versionCode = 12,
            artifacts = listOf(JaecooFingerprint.Artifact("base.apk", 1, "aaa"))
        )

        val original = JaecooFingerprint.calculate(listOf(group))
        val changed = JaecooFingerprint.calculate(
            listOf(group.copy(artifacts = listOf(group.artifacts.single().copy(size = 2))))
        )

        assertThat(changed).isNotEqualTo(original)
    }
}
