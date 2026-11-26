package com.gizwits.smart.app.core.setup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WifiListRetryTest {
    @Test fun transientFailureAndEmptyScanRetryUntilSuccess() = runBlocking {
        var calls = 0
        val attempts = mutableListOf<Int>()
        val result = retryWifiList(request = {
            when (++calls) {
                1 -> Result.failure(IllegalStateException("not ready"))
                2 -> Result.success(emptyList())
                else -> Result.success(listOf("Home"))
            }
        }, onAttempt = { attempts += it }, pause = {})
        assertEquals(listOf("Home"), result.getOrThrow())
        assertEquals(listOf(1, 2, 3), attempts)
    }
    @Test fun exhaustedRequestsPreserveFailureAndStopAtThree() = runBlocking {
        var calls = 0
        val failure = IllegalStateException("disconnected")
        val result = retryWifiList<String>({ calls++; Result.failure(failure) }, {}, {})
        assertSame(failure, result.exceptionOrNull()); assertEquals(3, calls)
    }
    @Test fun cancellationDuringWaitDoesNotIssueAnotherRequest() = runBlocking {
        var calls = 0
        val waiting = CompletableDeferred<Unit>()
        val job = launch {
            retryWifiList<String>({ calls++; Result.success(emptyList()) }, {}, {
                waiting.complete(Unit); awaitCancellation()
            })
        }
        waiting.await(); job.cancelAndJoin()
        assertEquals(1, calls)
    }
    @Test fun returnedCancellationIsNotRetried() = runBlocking {
        var calls = 0
        try {
            retryWifiList<String>({ calls++; Result.failure(CancellationException()) }, {}, {})
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { assertEquals(1, calls) }
    }
}
