/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.store

import com.aurora.store.data.event.InstallerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class InstallResultTest {

    @Test
    fun mapsInstalledEventToSuccess() {
        val event = InstallerEvent.Installed(packageName = "com.example.j7")

        val result = InstallResult.fromEvent(event, displayName = "J7 App")

        assertThat(result).isEqualTo(
            InstallResult.Success(packageName = "com.example.j7", displayName = "J7 App")
        )
    }

    @Test
    fun mapsFailedEventWithErrorToFailure() {
        val event = InstallerEvent.Failed(
            packageName = "com.example.j7",
            error = "INSTALL_FAILED_INSUFFICIENT_STORAGE"
        )

        val result = InstallResult.fromEvent(event, displayName = "J7 App")

        assertThat(result).isEqualTo(
            InstallResult.Failure(
                packageName = "com.example.j7",
                displayName = "J7 App",
                reason = "INSTALL_FAILED_INSUFFICIENT_STORAGE"
            )
        )
    }

    @Test
    fun mapsFailedEventWithoutErrorToFailureCarryingExtra() {
        val event = InstallerEvent.Failed(
            packageName = "com.example.j7",
            extra = "Ch授yooo invalid signature"
        )

        val result = InstallResult.fromEvent(event, displayName = "J7 App")

        assertThat(result).isEqualTo(
            InstallResult.Failure(
                packageName = "com.example.j7",
                displayName = "J7 App",
                reason = "Ch授yooo invalid signature"
            )
        )
    }

    @Test
    fun mapsFailedEventWithBothFieldsToFailurePreferringError() {
        val event = InstallerEvent.Failed(
            packageName = "com.example.j7",
            error = "primary",
            extra = "secondary"
        )

        val result = InstallResult.fromEvent(event, displayName = "J7 App")

        assertThat(result).isInstanceOf(InstallResult.Failure::class.java)
        assertThat((result as InstallResult.Failure).reason).isEqualTo("primary")
    }

    @Test
    fun mapsFailedEventWithNeitherFieldToFailureWithNullReason() {
        val event = InstallerEvent.Failed(packageName = "com.example.j7")

        val result = InstallResult.fromEvent(event, displayName = "J7 App")

        assertThat(result).isEqualTo(
            InstallResult.Failure(
                packageName = "com.example.j7",
                displayName = "J7 App",
                reason = null
            )
        )
    }

    @Test
    fun rejectsUnmappedEventTypes() {
        val event = InstallerEvent.Installing(
            packageName = "com.example.j7",
            progress = 0.5F
        )

        assertThrows(IllegalArgumentException::class.java) {
            InstallResult.fromEvent(event, displayName = "J7 App")
        }
    }
}
