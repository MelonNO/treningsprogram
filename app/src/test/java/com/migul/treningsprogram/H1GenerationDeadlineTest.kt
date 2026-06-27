package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.GENERATION_TIMEOUT_MESSAGE
import com.migul.treningsprogram.data.repository.isTransientAiError
import com.migul.treningsprogram.data.repository.isTransientGenerationError
import com.migul.treningsprogram.data.repository.withAiRetry
import com.migul.treningsprogram.data.repository.withGenerationDeadline
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * H1 — generation must reach a TERMINAL outcome, never hang on "Attempt N of 3".
 *
 * Two pure seams guard the fix without an [com.migul.treningsprogram.data.repository.AiRepository]
 * instance:
 *  1. [withGenerationDeadline] — the overall wall-clock bound. A timeout is converted into a clear,
 *     friendly, TERMINAL [IllegalStateException] (the caller's runCatching turns it into a
 *     Result.failure); a fast block returns its value; a non-timeout error propagates unchanged
 *     (the timeout catch must NOT swallow other failures).
 *  2. [isTransientGenerationError] + [withAiRetry] — a timed-out (SocketTimeout) GENERATION call is
 *     NOT retried (so it does not pay the wasteful double-timeout), while genuine 5xx/429 blips still
 *     retry. The other AI callers keep the default [isTransientAiError] policy (unchanged).
 */
class H1GenerationDeadlineTest {

    // ── withGenerationDeadline ──────────────────────────────────────────────────────────────────────

    @Test fun deadline_timeout_throwsFriendlyTerminalMessage() = runBlocking {
        var thrown: Throwable? = null
        try {
            // 20 ms budget, block tries to run for 10 s → the deadline fires first.
            withGenerationDeadline(timeoutMs = 20L) {
                delay(10_000L)
                "should never be returned"
            }
        } catch (t: Throwable) {
            thrown = t
        }
        // Terminal & friendly: an IllegalStateException (→ Result.failure), NOT a raw
        // TimeoutCancellationException, carrying the user-facing message.
        assertTrue("timeout must surface as IllegalStateException, got $thrown", thrown is IllegalStateException)
        assertEquals(GENERATION_TIMEOUT_MESSAGE, thrown?.message)
        assertTrue("message must mention it was stopped", GENERATION_TIMEOUT_MESSAGE.contains("too long"))
        assertTrue("message must guide the user", GENERATION_TIMEOUT_MESSAGE.contains("try again", ignoreCase = true))
    }

    @Test fun deadline_fastBlock_returnsValue() = runBlocking {
        val result = withGenerationDeadline(timeoutMs = 5_000L) { "done" }
        assertEquals("done", result)
    }

    @Test fun deadline_doesNotSwallowNonTimeoutFailure() = runBlocking {
        // A normal failure inside the block (e.g. the loop's "Program rejected" throw) must propagate
        // AS-IS — the timeout catch is narrow and must not convert it into the timeout message.
        var thrown: Throwable? = null
        try {
            withGenerationDeadline(timeoutMs = 5_000L) {
                throw IllegalStateException("Program rejected after 3 attempts.")
            }
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue(thrown is IllegalStateException)
        assertEquals("Program rejected after 3 attempts.", thrown?.message)
    }

    // ── isTransientGenerationError ──────────────────────────────────────────────────────────────────

    @Test fun generationRetry_socketTimeout_isNotRetryable() {
        // The crux of H1: a generation call that hit its callTimeout must NOT be re-issued.
        assertFalse(isTransientGenerationError(SocketTimeoutException("read timed out")))
        // …even though the shared classifier treats it as transient for other callers.
        assertTrue(isTransientAiError(SocketTimeoutException("read timed out")))
    }

    @Test fun generationRetry_keepsRealBlipsRetryable() {
        assertTrue("5xx still retried", isTransientGenerationError(httpException(500)))
        assertTrue("503 still retried", isTransientGenerationError(httpException(503)))
        assertTrue("429 still retried", isTransientGenerationError(httpException(429)))
        // A fast-failing IOException (e.g. connection reset) that is NOT a SocketTimeout still retries.
        assertTrue("connection reset still retried", isTransientGenerationError(IOException("connection reset")))
    }

    @Test fun generationRetry_nonTransientStaysNonRetryable() {
        assertFalse(isTransientGenerationError(httpException(401)))
        assertFalse(isTransientGenerationError(httpException(400)))
        assertFalse(isTransientGenerationError(IllegalStateException("Program rejected")))
    }

    // ── withAiRetry with the generation policy: bounded, no unbounded looping ───────────────────────

    @Test fun withAiRetry_generationPolicy_socketTimeoutDoesNotLoop() = runBlocking {
        var calls = 0
        var thrown: Throwable? = null
        try {
            withAiRetry(maxAttempts = 2, isRetryable = ::isTransientGenerationError) {
                calls++
                throw SocketTimeoutException("generation stalled")
            }
        } catch (t: Throwable) {
            thrown = t
        }
        // It must fail FAST after exactly one call (no immediate second timeout), surfacing the timeout.
        assertEquals("a timed-out generate call must not be retried", 1, calls)
        assertTrue(thrown is SocketTimeoutException)
    }

    @Test fun withAiRetry_generationPolicy_5xxThenSuccessStillRetries() = runBlocking {
        var calls = 0
        val result = withAiRetry(maxAttempts = 2, isRetryable = ::isTransientGenerationError) {
            calls++
            if (calls == 1) throw httpException(503)
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(2, calls)
    }

    @Test fun withAiRetry_defaultPolicyUnchanged_socketTimeoutStillRetries() = runBlocking {
        // Regression guard: the OTHER callers (default predicate) keep the old behavior — a SocketTimeout
        // is retried up to maxAttempts. This proves the generation-local change is neutral to them.
        var calls = 0
        var thrown: Throwable? = null
        try {
            withAiRetry(maxAttempts = 2) {
                calls++
                throw SocketTimeoutException("transient")
            }
        } catch (t: Throwable) {
            thrown = t
        }
        assertEquals(2, calls)
        assertTrue(thrown is SocketTimeoutException)
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, okhttp3.ResponseBody.create(null, "")))
}
