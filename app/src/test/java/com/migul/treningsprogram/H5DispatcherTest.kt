package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.consumeClaudeStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * H5 (v1.10.5) — the streaming body is consumed OFF the calling thread.
 *
 * v1.10.4 added [com.migul.treningsprogram.data.repository.parseClaudeStream] but read the `@Streaming`
 * okhttp body with a BLOCKING `.string()` that ran on the suspend caller's dispatcher. Generation is
 * launched from `viewModelScope` (Dispatchers.Main), so the ~60–180 s read blocked the main thread → ANR
 * (the app died; in-app logs on the frozen main thread captured nothing). The fix routes consumption
 * through [consumeClaudeStream], which runs the blocking read on the supplied IO dispatcher and closes the
 * body. This locks that dispatcher contract WITHOUT a network call, mirroring the F3 / H4 package-level-
 * helper test pattern.
 */
class H5DispatcherTest {

    /** A minimal, valid SSE payload: one text_delta + a terminal message_delta stop_reason. */
    private val sse = """
        event: content_block_delta
        data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"{\"ok\":true}"}}

        event: message_delta
        data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}
    """.trimIndent()

    /**
     * A fake [ResponseBody] over [sse] that records the thread its content is read on (so we can prove the
     * blocking read happened on the IO dispatcher, not the caller) and whether it was closed.
     */
    private class RecordingBody(
        private val text: String,
        val readThread: AtomicReference<Thread?> = AtomicReference(null),
        val closed: AtomicBoolean = AtomicBoolean(false),
    ) : ResponseBody() {
        override fun contentType(): MediaType? = "text/event-stream".toMediaTypeOrNull()
        override fun contentLength(): Long = -1L
        override fun source(): BufferedSource {
            // string() reads via source(); this synchronous read runs on the consuming dispatcher's thread.
            readThread.set(Thread.currentThread())
            return Buffer().writeUtf8(text)
        }
        override fun close() {
            closed.set(true)
            super.close()
        }
    }

    /**
     * With a controlled single-thread IO dispatcher: the body is parsed correctly AND the read runs on the
     * dispatcher's thread — a DIFFERENT thread from the caller (runBlocking) — and the body is closed.
     */
    @Test fun consumesOnSuppliedDispatcherThread_notCallerThread_andClosesBody() {
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "h5-io-worker") }
        val ioDispatcher = executor.asCoroutineDispatcher()
        val body = RecordingBody(sse)
        try {
            val callerThread = AtomicReference<Thread?>(null)
            val resp = runBlocking {
                callerThread.set(Thread.currentThread())
                consumeClaudeStream(body, ioDispatcher)
            }

            // (a) parsed correctly
            assertEquals("text accumulated from the single text_delta", "{\"ok\":true}", resp.text())
            assertEquals("terminal stop_reason captured", "end_turn", resp.stopReason)

            // (b) the blocking read ran OFF the caller thread, ON the supplied IO dispatcher's thread
            val readThread = body.readThread.get()
            assertTrue("the body must have been read", readThread != null)
            assertEquals("read must run on the supplied IO dispatcher's thread", "h5-io-worker", readThread!!.name)
            assertNotEquals(
                "read must NOT run on the calling (would-be main) thread",
                callerThread.get()!!.name,
                readThread.name,
            )

            // the body is closed (connection released) even on the happy path
            assertTrue("consumeClaudeStream must close the body via use {}", body.closed.get())
        } finally {
            ioDispatcher.close()
            executor.shutdownNow()
        }
    }

    /**
     * With the real [Dispatchers.IO] (production's `io`): consumption still moves off the caller thread.
     */
    @Test fun consumesOnDispatchersIo_offCallerThread() {
        val body = RecordingBody(sse)
        val callerThreadName = AtomicReference<String?>(null)
        val resp = runBlocking {
            callerThreadName.set(Thread.currentThread().name)
            consumeClaudeStream(body, Dispatchers.IO)
        }
        assertEquals("{\"ok\":true}", resp.text())
        assertEquals("end_turn", resp.stopReason)
        val readName = body.readThread.get()!!.name
        assertNotEquals(
            "Dispatchers.IO must move the blocking read off the caller thread",
            callerThreadName.get(),
            readName,
        )
        assertTrue("the body is closed after consumption", body.closed.get())
    }
}
