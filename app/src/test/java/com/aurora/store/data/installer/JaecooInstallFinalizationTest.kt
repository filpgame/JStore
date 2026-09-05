/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer

import com.aurora.store.data.event.InstallerEvent
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertThrows
import org.junit.Test

class JaecooInstallFinalizationTest {
    @Test
    fun finalizer_successIsOneShotAcrossConcurrentCallbacks() {
        val records = AtomicReference<JaecooInstallRecord?>(record())
        val queue = CopyOnWriteArrayList<String>()
        val persisted = CopyOnWriteArrayList<String>()
        val events = CopyOnWriteArrayList<InstallerEvent>()
        val timeline = CopyOnWriteArrayList<String>()
        val notifications = CopyOnWriteArrayList<Pair<String, String>>()
        val revoked = CopyOnWriteArrayList<JaecooInstallRecord>()
        val deleted = CopyOnWriteArrayList<JaecooInstallRecord>()
        val effects = effects(
            queue,
            persisted,
            events,
            timeline,
            notifications,
            revoked,
            deleted
        )
        val finalizer = JaecooInstallFinalizer({ records.getAndSet(null) }, effects)
        val started = CountDownLatch(1)
        val finished = CountDownLatch(2)

        repeat(2) {
            Thread {
                started.await()
                finalizer.finish("attempt", isSuccess = true, failure = failure())
                finished.countDown()
            }.start()
        }
        started.countDown()

        assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue()
        assertThat(queue).containsExactly("com.example.app")
        assertThat(persisted).containsExactly("com.example.app")
        assertThat(events).containsExactly(InstallerEvent.Installed("com.example.app"))
        assertThat(timeline).containsExactly("persist", "emit").inOrder()
        assertThat(notifications).containsExactly("Example" to "com.example.app")
        assertThat(revoked).containsExactly(record())
        assertThat(deleted).containsExactly(record())
    }

    @Test
    fun finalizer_successCleansUpWhenNotificationThrows() {
        val records = AtomicReference<JaecooInstallRecord?>(record())
        val queue = mutableListOf<String>()
        val persisted = mutableListOf<String>()
        val events = mutableListOf<InstallerEvent>()
        val timeline = mutableListOf<String>()
        val notifications = mutableListOf<Pair<String, String>>()
        val revoked = mutableListOf<JaecooInstallRecord>()
        val deleted = mutableListOf<JaecooInstallRecord>()
        val effects = effects(
            queue,
            persisted,
            events,
            timeline,
            notifications,
            revoked,
            deleted,
            throwOnNotify = true
        )
        val finalizer = JaecooInstallFinalizer({ records.getAndSet(null) }, effects)

        assertThrows(IllegalStateException::class.java) {
            finalizer.finish("attempt", isSuccess = true, failure = failure())
        }

        assertThat(records.get()).isNull()
        assertThat(queue).containsExactly("com.example.app")
        assertThat(persisted).containsExactly("com.example.app")
        assertThat(events).containsExactly(InstallerEvent.Installed("com.example.app"))
        assertThat(timeline).containsExactly("persist", "emit").inOrder()
        assertThat(notifications).containsExactly("Example" to "com.example.app")
        assertThat(revoked).containsExactly(record())
        assertThat(deleted).containsExactly(record())
    }

    @Test
    fun finalizer_failureReportsAndRevokesWithoutInstalledEffects() {
        val queue = mutableListOf<String>()
        val persisted = mutableListOf<String>()
        val events = mutableListOf<InstallerEvent>()
        val timeline = mutableListOf<String>()
        val notifications = mutableListOf<Pair<String, String>>()
        val revoked = mutableListOf<JaecooInstallRecord>()
        val deleted = mutableListOf<JaecooInstallRecord>()
        val failures = mutableListOf<JaecooInstallFailure>()
        val effects = effects(
            queue,
            persisted,
            events,
            timeline,
            notifications,
            revoked,
            deleted,
            failures
        )
        val finalizer = JaecooInstallFinalizer({ record() }, effects)

        finalizer.finish("attempt", isSuccess = false, failure = failure())

        assertThat(queue).isEmpty()
        assertThat(persisted).isEmpty()
        assertThat(events).isEmpty()
        assertThat(timeline).isEmpty()
        assertThat(notifications).isEmpty()
        assertThat(failures).containsExactly(failure())
        assertThat(revoked).containsExactly(record())
        assertThat(deleted).isEmpty()
    }

    @Test
    fun consumePersistedRecord_failsWhenLedgerRemovalIsNotDurable() {
        assertThrows(IllegalStateException::class.java) {
            consumePersistedRecord("attempt") { false }
        }
    }

    @Test
    fun finalizer_persistFailureCleansUpWithoutEmittingInstalled() {
        val records = AtomicReference<JaecooInstallRecord?>(record())
        val queue = mutableListOf<String>()
        val persisted = mutableListOf<String>()
        val events = mutableListOf<InstallerEvent>()
        val timeline = mutableListOf<String>()
        val notifications = mutableListOf<Pair<String, String>>()
        val revoked = mutableListOf<JaecooInstallRecord>()
        val deleted = mutableListOf<JaecooInstallRecord>()
        val effects = effects(
            queue,
            persisted,
            events,
            timeline,
            notifications,
            revoked,
            deleted,
            throwOnPersist = true
        )
        val finalizer = JaecooInstallFinalizer({ records.getAndSet(null) }, effects)

        assertThrows(IllegalStateException::class.java) {
            finalizer.finish("attempt", isSuccess = true, failure = failure())
        }

        assertThat(records.get()).isNull()
        assertThat(queue).containsExactly("com.example.app")
        assertThat(persisted).containsExactly("com.example.app")
        assertThat(events).isEmpty()
        assertThat(notifications).isEmpty()
        assertThat(revoked).containsExactly(record())
        assertThat(deleted).containsExactly(record())
    }

    private fun record() = JaecooInstallRecord(
        attemptId = "attempt",
        operationId = "operation",
        packageName = "com.example.app",
        versionCode = 4,
        displayName = "Example",
        fingerprint = "fingerprint",
        uris = emptyList()
    )

    private fun failure() = JaecooInstallFailure(
        packageName = "com.example.app",
        message = "failed",
        extra = "errorCode=5"
    )

    private fun effects(
        queue: MutableList<String>,
        persisted: MutableList<String>,
        events: MutableList<InstallerEvent>,
        timeline: MutableList<String>,
        notifications: MutableList<Pair<String, String>>,
        revoked: MutableList<JaecooInstallRecord>,
        deleted: MutableList<JaecooInstallRecord>,
        failures: MutableList<JaecooInstallFailure> = mutableListOf(),
        throwOnNotify: Boolean = false,
        throwOnPersist: Boolean = false
    ) = JaecooInstallEffects(
        removeFromQueue = queue::add,
        markInstalled = { packageName ->
            persisted += packageName
            timeline += "persist"
            if (throwOnPersist) throw IllegalStateException("persistence failed")
        },
        emitEvent = { event ->
            events += event
            timeline += "emit"
        },
        notifyInstalled = { displayName, packageName ->
            notifications += displayName to packageName
            if (throwOnNotify) throw IllegalStateException("notification failed")
        },
        reportFailure = failures::add,
        revokeUris = revoked::add,
        autoDelete = deleted::add
    )
}
