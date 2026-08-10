/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.jaecoo.installer.bridge

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class InstallerCapabilities(
    val protocolVersion: Int,
    val serviceVersion: Int,
    val androidSdk: Int,
    val isDeviceOwner: Boolean,
    val servicePackage: String
) : Parcelable

@Parcelize
data class InstallArtifact(
    val uri: Uri,
    val name: String,
    val size: Long,
    val sha256: String
) : Parcelable

@Parcelize
data class InstallArtifactGroup(
    val packageName: String,
    val versionCode: Long,
    val artifacts: List<InstallArtifact>
) : Parcelable

@Parcelize
data class InstallRequest(
    val protocolVersion: Int,
    val attemptId: String,
    val fingerprint: String,
    val app: InstallArtifactGroup,
    val sharedLibraries: List<InstallArtifactGroup>
) : Parcelable

@Parcelize
data class OperationStatus(
    val operationId: String,
    val attemptId: String,
    val state: Int,
    val progress: Int,
    val errorCode: Int,
    val message: String?,
    val updatedAt: Long
) : Parcelable
