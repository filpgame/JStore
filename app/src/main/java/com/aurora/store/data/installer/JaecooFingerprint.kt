/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Canonical request fingerprint shared conceptually with jconfig's validator. */
internal object JaecooFingerprint {
    data class Artifact(val name: String, val size: Long, val sha256: String)

    data class Group(
        val packageName: String,
        val versionCode: Long,
        val artifacts: List<Artifact>
    )

    fun calculate(groups: List<Group>): String {
        val canonical = buildString {
            groups.sortedBy(Group::packageName).forEach { group ->
                append(group.packageName).append(':').append(group.versionCode)
                group.artifacts.sortedBy(Artifact::name).forEach { artifact ->
                    append('|')
                        .append(artifact.name)
                        .append(':')
                        .append(artifact.size)
                        .append(':')
                        .append(artifact.sha256)
                }
                append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
