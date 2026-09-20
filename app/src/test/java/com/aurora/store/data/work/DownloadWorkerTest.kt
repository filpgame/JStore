/*
 * SPDX-FileCopyrightText: 2026 Aurora Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.work

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.lang.reflect.Method
import org.junit.Test
import sun.misc.Unsafe

class DownloadWorkerTest {
    @Test
    fun http2StreamReset_isRetryable() {
        assertThat(invokeIsRetryable(IOException("stream was reset: INTERNAL_ERROR"))).isTrue()
    }

    private fun invokeIsRetryable(exception: Throwable): Boolean {
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe").apply {
            isAccessible = true
        }
        val unsafe = unsafeField.get(null) as Unsafe
        val worker = unsafe.allocateInstance(DownloadWorker::class.java)
        val method: Method = DownloadWorker::class.java.getDeclaredMethod(
            "isRetryable",
            Throwable::class.java
        ).apply {
            isAccessible = true
        }
        return method.invoke(worker, exception) as Boolean
    }
}
