package com.gizwits.smart.app.core.setup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay

/** Retries an empty scan as well as a failed request; cancellation always stops the loop. */
internal suspend fun <T> retryWifiList(
    request: suspend () -> Result<List<T>>,
    onAttempt: (Int) -> Unit,
    pause: suspend () -> Unit = { delay(1000) },
): Result<List<T>> {
    repeat(3) { index ->
        currentCoroutineContext().ensureActive()
        onAttempt(index + 1)
        val result = request()
        (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        if (result.getOrNull()?.isNotEmpty() == true || index == 2) return result
        pause()
    }
    error("Unreachable")
}
