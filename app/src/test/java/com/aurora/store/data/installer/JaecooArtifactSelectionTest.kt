/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer

import com.aurora.gplayapi.data.models.PlayFile
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JaecooArtifactSelectionTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun singleApkFile_isSelectedAsBase_whenPlayFileListIsEmpty() {
        val only = temp.newFile("xeq.apk")

        val selected = selectBaseArtifactNames(files = listOf(only), playFiles = emptyList())

        assertThat(selected).containsExactly("xeq.apk")
    }

    @Test
    fun baseApk_isSelectedByPlayFileType_whenMultipleFilesExist() {
        val base = temp.newFile("base.apk")
        val split = temp.newFile("config.xxhdpi.apk")

        val selected = selectBaseArtifactNames(
            files = listOf(base, split),
            playFiles = listOf(
                PlayFile(
                    name = "base.apk",
                    url = "https://example/base",
                    size = 1L,
                    type = PlayFile.Type.BASE,
                    sha1 = "",
                    sha256 = ""
                ),
                PlayFile(
                    name = "config.xxhdpi.apk",
                    url = "https://example/split",
                    size = 1L,
                    type = PlayFile.Type.SPLIT,
                    sha1 = "",
                    sha256 = ""
                )
            )
        )

        assertThat(selected).containsExactly("base.apk")
    }

    @Test
    fun catalogSingleApk_isSelectedAsBase_viaDefaultBaseType() {
        // Catalog entries produce PlayFile without setting `type`, which defaults to BASE.
        val apk = temp.newFile("morphe-youtube.apk")

        val selected = selectBaseArtifactNames(
            files = listOf(apk),
            playFiles = listOf(
                PlayFile(
                    name = "morphe-youtube.apk",
                    url = "https://example/x",
                    size = 1L,
                    sha1 = "",
                    sha256 = ""
                )
            )
        )

        assertThat(selected).containsExactly("morphe-youtube.apk")
    }

    @Test
    fun noBase_isSelected_whenMultipleFilesExist_andNoneIsBase() {
        // Defensive: avoid false positives; let the bridge surface BASE_APK_MISSING.
        val a = temp.newFile("a.apk")
        val b = temp.newFile("b.apk")

        val selected = selectBaseArtifactNames(
            files = listOf(a, b),
            playFiles = listOf(
                PlayFile(name = "a.apk", url = "u", size = 1L, type = PlayFile.Type.SPLIT, sha1 = "", sha256 = ""),
                PlayFile(name = "b.apk", url = "u", size = 1L, type = PlayFile.Type.SPLIT, sha1 = "", sha256 = "")
            )
        )

        assertThat(selected).isEmpty()
    }

    @Test
    fun unmatchedBasePlayFile_doesNotFallBackToSingleFileRule() {
        // Play file points to a file that isn't on disk; do NOT silently rename a
        // different file to base.apk.
        val apk = temp.newFile("xeq.apk")

        val selected = selectBaseArtifactNames(
            files = listOf(apk),
            playFiles = listOf(
                PlayFile(
                    name = "missing-base.apk",
                    url = "u",
                    size = 1L,
                    type = PlayFile.Type.BASE,
                    sha1 = "",
                    sha256 = ""
                )
            )
        )

        // The single-file fallback only applies when no BASE play file was found at all.
        // A mismatched BASE must not be ignored.
        assertThat(selected).isEmpty()
    }

    @Test
    fun noBase_isSelected_whenFilesAreEmpty_evenWithBasePlayFilesDeclared() {
        // No on-disk file means no base can be reported; the bridge should surface
        // BASE_APK_MISSING as the diagnostic.
        val selected = selectBaseArtifactNames(
            files = emptyList(),
            playFiles = listOf(
                PlayFile(
                    name = "base.apk",
                    url = "u",
                    size = 1L,
                    type = PlayFile.Type.BASE,
                    sha1 = "",
                    sha256 = ""
                )
            )
        )

        assertThat(selected).isEmpty()
    }
}
