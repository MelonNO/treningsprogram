package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.parseClaudeStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * H4 (v1.10.4) — the SSE-stream parser.
 *
 * Full-program generation is now sent with stream=true so OkHttp's readTimeout becomes an INTER-EVENT
 * stall guard instead of a single time-to-first-byte deadline (the v1.10.3 timeout regression). The pure
 * [parseClaudeStream] reconstructs the SAME [com.migul.treningsprogram.data.api.model.ClaudeResponse] the
 * non-streaming endpoint would have returned, so every downstream consumer (text(), stopReason,
 * extractJsonOrNull, isLikelyTruncated, parseProgram) is unchanged. This guards the parser contract
 * without a network call, mirroring the F3 / S3 / B10 / H1 package-level-helper test pattern.
 */
class H4StreamParserTest {

    private fun loadFixture(name: String): String =
        (this::class.java.getResourceAsStream("/$name")
            ?: error("test fixture /$name not found on the classpath"))
            .bufferedReader().use { it.readText() }

    /**
     * The REAL captured SSE format (app/src/test/resources/sample_stream.sse). Its reconstructed text is
     * intentionally NOT valid JSON (the capture contains a truncated data line); the parser's contract is
     * only to accumulate every text_delta and capture the terminal stop_reason — downstream parsing/
     * truncation detection deals with the JSON itself.
     */
    @Test fun realFixture_accumulatesText_andCapturesEndTurn() {
        val resp = parseClaudeStream(loadFixture("sample_stream.sse"))
        val text = resp.text()
        assertTrue("accumulated text must be non-empty", text.isNotBlank())
        assertTrue("text must start with the first delta '{'", text.trimStart().startsWith("{"))
        assertTrue("text must contain the accumulated rationale content", text.contains("rationale"))
        assertEquals("terminal stop_reason must be captured from message_delta", "end_turn", resp.stopReason)
    }

    /**
     * Hand-built coverage: multiple text_deltas across two content blocks accumulate in order; a `ping`
     * and all non-text structural events are ignored; `[DONE]` and blank lines are tolerated; the
     * message_delta stop_reason is captured.
     */
    @Test fun handBuilt_multiBlockAccumulation_pingIgnored_doneAndBlankTolerated() {
        val sse = """
            event: message_start
            data: {"type":"message_start","message":{"id":"m","content":[]}}

            event: ping
            data: {"type": "ping"}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello, "}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: content_block_start
            data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"world"}}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null}}

            event: message_stop
            data: {"type":"message_stop"}

            data: [DONE]
        """.trimIndent()
        val resp = parseClaudeStream(sse)
        assertEquals("Hello, world", resp.text())
        assertEquals("end_turn", resp.stopReason)
    }

    /** An `error` event must throw an IOException so it flows through the transient/retry + friendly-error path. */
    @Test fun errorEvent_throwsIOException() {
        val sse = """
            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"partial"}}

            event: error
            data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}
        """.trimIndent()
        try {
            parseClaudeStream(sse)
            fail("an error event must throw")
        } catch (e: IOException) {
            assertTrue("the error message should surface the API reason: ${e.message}",
                e.message!!.contains("Overloaded"))
        }
    }

    /**
     * A stream cut off before message_delta → stopReason stays null and the partial text is retained
     * (downstream isLikelyTruncated then flags the no-balanced-JSON case as a retryable rejection).
     */
    @Test fun noMessageDelta_leavesStopReasonNull_andTextAccumulates() {
        val sse = """
            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"{\"days\":["}}
        """.trimIndent()
        val resp = parseClaudeStream(sse)
        assertEquals("{\"days\":[", resp.text())
        assertNull("no message_delta ⇒ stopReason null", resp.stopReason)
    }

    /** A truncated/incomplete `data:` JSON line (a stream cut mid-event) is skipped, not fatal. */
    @Test fun malformedDataLine_isSkipped_notFatal() {
        val sse = """
            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"A"}}

            event: content_block_delta
            data: {"type":"

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"B"}}
        """.trimIndent()
        val resp = parseClaudeStream(sse)
        assertEquals("the malformed middle line is skipped; surrounding deltas still accumulate", "AB", resp.text())
        assertNull(resp.stopReason)
    }

    /** Empty input ⇒ empty text, null stop_reason (no crash). */
    @Test fun emptyStream_yieldsEmptyResponse() {
        val resp = parseClaudeStream("")
        assertEquals("", resp.text())
        assertNull(resp.stopReason)
    }

    /**
     * G2 (Phase 3) — with adaptive thinking ON, the model emits a THINKING content block (thinking_delta +
     * signature_delta) BEFORE the text block that carries the JSON plan. The parser must accumulate ONLY
     * the text-block content (it appends solely `text_delta`), so the model's reasoning is never parsed as
     * the plan or logged as the plan. The terminal stop_reason is still captured from message_delta.
     */
    @Test fun thinkingBlockBeforeText_onlyTextJsonAccumulated_stopReasonCaptured() {
        val sse = """
            event: message_start
            data: {"type":"message_start","message":{"id":"m","content":[]}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Let me plan the 5 days: Day1 push, Day2 pull..."}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"abc123=="}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: content_block_start
            data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"{\"rationale\":\"x\","}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"\"days\":[]}"}}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null}}

            event: message_stop
            data: {"type":"message_stop"}
        """.trimIndent()
        val resp = parseClaudeStream(sse)
        // Only the TEXT block's JSON is returned — none of the thinking content leaks in.
        assertEquals("{\"rationale\":\"x\",\"days\":[]}", resp.text())
        assertTrue("thinking prose must NOT be in the parsed plan", !resp.text().contains("Let me plan"))
        assertTrue("thinking signature must NOT be in the parsed plan", !resp.text().contains("abc123"))
        assertEquals("end_turn", resp.stopReason)
    }
}
