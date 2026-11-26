package com.gizwits.smart.app.feature.control.data

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DataPointWriteTest {
    @Test fun successfulWriteWithoutUnsolicitedReportReadsActualState() = runBlocking {
        val order = mutableListOf<String>()
        // Device can reject/clamp a value or reset a momentary switch to false.
        val actual = JsonObject().apply { add("data", JsonObject().apply { addProperty("power", false) }) }
        val result = sendAndReadDataPoint(
            write = { order += "write"; Result.success(Unit) },
            read = { order += "read"; Result.success(actual) },
            onSent = { order += "sent" }
        )
        assertEquals(listOf("write", "sent", "read"), order)
        assertEquals(actual, result.read?.getOrThrow())
        assertFalse(result.read!!.getOrThrow().getAsJsonObject("data").get("power").asBoolean)
    }

    @Test fun failedWriteDoesNotReadOrClaimSuccess() = runBlocking {
        val failure = IllegalStateException("disconnected")
        val result = sendAndReadDataPoint(
            write = { Result.failure(failure) },
            read = { error("must not read") },
            onSent = { error("must not claim success") }
        )
        assertSame(failure, result.write.exceptionOrNull())
        assertNull(result.read)
    }

    @Test fun readFailureKeepsSuccessfulWriteAndDoesNotInventAReport() = runBlocking {
        val failure = IllegalStateException("read timeout")
        for (throws in listOf(false, true)) {
            val result = sendAndReadDataPoint(
                write = { Result.success(Unit) },
                read = { if (throws) throw failure else Result.failure(failure) },
                onSent = {}
            )
            assertTrue(result.write.isSuccess)
            assertSame(failure, result.read?.exceptionOrNull())
        }
    }

    @Test fun operationStaysPendingUntilReadbackCompletes() = runBlocking {
        val response = CompletableDeferred<Result<JsonObject>>()
        val operation = async(start = CoroutineStart.UNDISPATCHED) {
            sendAndReadDataPoint({ Result.success(Unit) }, { response.await() }, {})
        }
        assertFalse(operation.isCompleted)
        response.complete(Result.success(JsonObject()))
        assertTrue(operation.await().read!!.isSuccess)
    }

    @Test fun cancellationIsNotConvertedIntoReadFailure() = runBlocking {
        val cancellation = CancellationException("page closed")
        try {
            sendAndReadDataPoint({ Result.success(Unit) }, { throw cancellation }, {})
            fail("cancellation must propagate")
        } catch (e: CancellationException) {
            assertSame(cancellation, e)
        }
    }
}
