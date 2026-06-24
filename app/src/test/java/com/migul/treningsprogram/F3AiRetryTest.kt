package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.friendlyAiErrorMessage
import com.migul.treningsprogram.data.repository.isTransientAiError
import com.migul.treningsprogram.data.repository.withAiRetry
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
 * F3 — AI timeout / retry resilience.
 *
 * Tests the pure, package-level helpers extracted from AiRepository so the retry
 * and transient-classification contract is locked independently of the repository.
 *
 * Covered:
 *   - [isTransientAiError]: which exception types trigger a retry
 *   - [withAiRetry]: retry count, fast-fail on non-transient errors, success on first attempt
 *   - [friendlyAiErrorMessage]: user-facing string for each failure class
 */
class F3AiRetryTest {

    // ── isTransientAiError ────────────────────────────────────────────────────────────────────────

    @Test fun transient_socketTimeout_isTransient() {
        assertTrue(isTransientAiError(SocketTimeoutException("read timed out")))
    }

    @Test fun transient_ioException_isTransient() {
        assertTrue(isTransientAiError(IOException("connection reset")))
    }

    @Test fun transient_socketTimeoutIsSubclassOfIOException() {
        // SocketTimeoutException IS-A IOException — both code paths must be transient
        val st = SocketTimeoutException()
        assertTrue(isTransientAiError(st as IOException))
    }

    @Test fun transient_http500_isTransient() {
        val e = httpException(500)
        assertTrue(isTransientAiError(e))
    }

    @Test fun transient_http503_isTransient() {
        assertTrue(isTransientAiError(httpException(503)))
    }

    @Test fun transient_http429_isTransient() {
        assertTrue(isTransientAiError(httpException(429)))
    }

    @Test fun nonTransient_http401_isNotTransient() {
        assertFalse(isTransientAiError(httpException(401)))
    }

    @Test fun nonTransient_http400_isNotTransient() {
        assertFalse(isTransientAiError(httpException(400)))
    }

    @Test fun nonTransient_http403_isNotTransient() {
        assertFalse(isTransientAiError(httpException(403)))
    }

    @Test fun nonTransient_http404_isNotTransient() {
        assertFalse(isTransientAiError(httpException(404)))
    }

    @Test fun nonTransient_illegalState_isNotTransient() {
        assertFalse(isTransientAiError(IllegalStateException("Program rejected")))
    }

    @Test fun nonTransient_illegalArgument_isNotTransient() {
        assertFalse(isTransientAiError(IllegalArgumentException("bad input")))
    }

    // ── withAiRetry ───────────────────────────────────────────────────────────────────────────────

    @Test fun retry_successOnFirstAttempt_returnsValue() = runBlocking {
        var calls = 0
        val result = withAiRetry(maxAttempts = 2) {
            calls++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test fun retry_transientFailureThenSuccess_retriesOnce() = runBlocking {
        var calls = 0
        val result = withAiRetry(maxAttempts = 2) {
            calls++
            if (calls == 1) throw SocketTimeoutException("first attempt times out")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(2, calls)
    }

    @Test fun retry_persistentTransientFailure_throwsAfterMaxAttempts() = runBlocking {
        var calls = 0
        var thrown: Throwable? = null
        try {
            withAiRetry(maxAttempts = 2) {
                calls++
                throw SocketTimeoutException("always times out")
            }
        } catch (t: Throwable) {
            thrown = t
        }
        assertEquals(2, calls)
        assertTrue("expected SocketTimeoutException", thrown is SocketTimeoutException)
    }

    @Test fun retry_nonTransientFailure_failsImmediatelyWithoutRetry() = runBlocking {
        var calls = 0
        var thrown: Throwable? = null
        try {
            withAiRetry(maxAttempts = 2) {
                calls++
                throw httpException(401)
            }
        } catch (t: Throwable) {
            thrown = t
        }
        assertEquals("should not retry 401", 1, calls)
        assertTrue("expected HttpException", thrown is HttpException)
        assertEquals(401, (thrown as HttpException).code())
    }

    @Test fun retry_http500ThenSuccess_retriesOnce() = runBlocking {
        var calls = 0
        val result = withAiRetry(maxAttempts = 2) {
            calls++
            if (calls == 1) throw httpException(500)
            "server recovered"
        }
        assertEquals("server recovered", result)
        assertEquals(2, calls)
    }

    @Test fun retry_maxAttemptsOne_noRetryOnTransient() = runBlocking {
        var calls = 0
        var thrown: Throwable? = null
        try {
            withAiRetry(maxAttempts = 1) {
                calls++
                throw SocketTimeoutException("timeout")
            }
        } catch (t: Throwable) {
            thrown = t
        }
        assertEquals(1, calls)
        assertTrue(thrown is SocketTimeoutException)
    }

    // ── friendlyAiErrorMessage ────────────────────────────────────────────────────────────────────

    @Test fun friendlyMessage_socketTimeout_mentionsTimeout() {
        val msg = friendlyAiErrorMessage(SocketTimeoutException("read timed out"))
        assertTrue("message should mention timeout: $msg", msg.contains("timed out", ignoreCase = true))
        assertTrue("message should mention 'try again': $msg", msg.contains("try again", ignoreCase = true))
    }

    @Test fun friendlyMessage_ioException_mentionsNetwork() {
        val msg = friendlyAiErrorMessage(IOException("connection reset"))
        assertTrue("message should mention network: $msg", msg.contains("Network", ignoreCase = true))
        assertTrue("message should mention 'try again': $msg", msg.contains("try again", ignoreCase = true))
    }

    @Test fun friendlyMessage_http429_mentionsRateLimit() {
        val msg = friendlyAiErrorMessage(httpException(429))
        assertTrue("message should mention rate limit: $msg",
            msg.contains("rate limit", ignoreCase = true) || msg.contains("429"))
    }

    @Test fun friendlyMessage_http401_mentionsApiKey() {
        val msg = friendlyAiErrorMessage(httpException(401))
        assertTrue("message should mention API key: $msg", msg.contains("API key", ignoreCase = true))
    }

    @Test fun friendlyMessage_http503_mentionsServerError() {
        val msg = friendlyAiErrorMessage(httpException(503))
        assertTrue("message should mention server error: $msg",
            msg.contains("server error", ignoreCase = true) || msg.contains("503"))
    }

    @Test fun friendlyMessage_illegalState_fallsBackToExceptionMessage() {
        val e = IllegalStateException("Program rejected after 3 attempts.\nAttempt 1: foo")
        val msg = friendlyAiErrorMessage(e)
        assertTrue("fallback should contain exception message: $msg", msg.contains("Program rejected"))
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────

    /** Creates an [HttpException] for [code] without requiring a real Retrofit response body. */
    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, okhttp3.ResponseBody.create(null, "")))
}
