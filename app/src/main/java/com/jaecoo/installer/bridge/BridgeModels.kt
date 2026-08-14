/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.jaecoo.installer.bridge

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

data class InstallerCapabilities(
    val protocolVersion: Int,
    val serviceVersion: Int,
    val androidSdk: Int,
    val isDeviceOwner: Boolean,
    val servicePackage: String
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        protocolVersion = parcel.readInt(),
        serviceVersion = parcel.readInt(),
        androidSdk = parcel.readInt(),
        isDeviceOwner = parcel.readInt() != 0,
        servicePackage = requireNotNull(parcel.readString())
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(protocolVersion)
        parcel.writeInt(serviceVersion)
        parcel.writeInt(androidSdk)
        parcel.writeInt(if (isDeviceOwner) 1 else 0)
        parcel.writeString(servicePackage)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<InstallerCapabilities> =
            object : Parcelable.Creator<InstallerCapabilities> {
                override fun createFromParcel(parcel: Parcel) = InstallerCapabilities(parcel)

                override fun newArray(size: Int): Array<InstallerCapabilities?> = arrayOfNulls(size)
            }
    }
}

data class InstallArtifact(
    val uri: Uri,
    val name: String,
    val size: Long,
    val sha256: String
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        uri = requireNotNull(parcel.readParcelable(Uri::class.java.classLoader)),
        name = requireNotNull(parcel.readString()),
        size = parcel.readLong(),
        sha256 = requireNotNull(parcel.readString())
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(uri, flags)
        parcel.writeString(name)
        parcel.writeLong(size)
        parcel.writeString(sha256)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<InstallArtifact> =
            object : Parcelable.Creator<InstallArtifact> {
                override fun createFromParcel(parcel: Parcel) = InstallArtifact(parcel)

                override fun newArray(size: Int): Array<InstallArtifact?> = arrayOfNulls(size)
            }
    }
}

data class InstallArtifactGroup(
    val packageName: String,
    val versionCode: Long,
    val artifacts: List<InstallArtifact>
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        packageName = requireNotNull(parcel.readString()),
        versionCode = parcel.readLong(),
        artifacts = parcel.createTypedArrayList(InstallArtifact.CREATOR).orEmpty()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageName)
        parcel.writeLong(versionCode)
        parcel.writeTypedList(artifacts)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<InstallArtifactGroup> =
            object : Parcelable.Creator<InstallArtifactGroup> {
                override fun createFromParcel(parcel: Parcel) = InstallArtifactGroup(parcel)

                override fun newArray(size: Int): Array<InstallArtifactGroup?> = arrayOfNulls(size)
            }
    }
}

data class InstallRequest(
    val protocolVersion: Int,
    val attemptId: String,
    val fingerprint: String,
    val app: InstallArtifactGroup,
    val sharedLibraries: List<InstallArtifactGroup>
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        protocolVersion = parcel.readInt(),
        attemptId = requireNotNull(parcel.readString()),
        fingerprint = requireNotNull(parcel.readString()),
        app = requireNotNull(parcel.readParcelable(InstallArtifactGroup::class.java.classLoader)),
        sharedLibraries = parcel.createTypedArrayList(InstallArtifactGroup.CREATOR).orEmpty()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(protocolVersion)
        parcel.writeString(attemptId)
        parcel.writeString(fingerprint)
        parcel.writeParcelable(app, flags)
        parcel.writeTypedList(sharedLibraries)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<InstallRequest> =
            object : Parcelable.Creator<InstallRequest> {
                override fun createFromParcel(parcel: Parcel) = InstallRequest(parcel)

                override fun newArray(size: Int): Array<InstallRequest?> = arrayOfNulls(size)
            }
    }
}

data class OperationStatus(
    val operationId: String,
    val attemptId: String,
    val state: Int,
    val progress: Int,
    val errorCode: Int,
    val message: String?,
    val updatedAt: Long
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        operationId = requireNotNull(parcel.readString()),
        attemptId = requireNotNull(parcel.readString()),
        state = parcel.readInt(),
        progress = parcel.readInt(),
        errorCode = parcel.readInt(),
        message = parcel.readString(),
        updatedAt = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(operationId)
        parcel.writeString(attemptId)
        parcel.writeInt(state)
        parcel.writeInt(progress)
        parcel.writeInt(errorCode)
        parcel.writeString(message)
        parcel.writeLong(updatedAt)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<OperationStatus> =
            object : Parcelable.Creator<OperationStatus> {
                override fun createFromParcel(parcel: Parcel) = OperationStatus(parcel)

                override fun newArray(size: Int): Array<OperationStatus?> = arrayOfNulls(size)
            }
    }
}
