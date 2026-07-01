package com.migul.treningsprogram

import com.migul.treningsprogram.data.PromptLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Item 1 — "Copy all" renders every entry's prompt AND response into one paste-ready block. */
class PromptLogCopyAllTest {

    private fun ts(ms: Long) = "T$ms"

    @Test fun emptyReturnsBlank() {
        assertEquals("", PromptLog.formatAll(emptyList(), ::ts))
    }

    @Test fun includesEveryEntryPromptAndResponse() {
        val entries = listOf(
            PromptLog.Entry(1000L, "generate_program", "PROMPT-A", "RESPONSE-A"),
            PromptLog.Entry(2000L, "verification", "PROMPT-B", "RESPONSE-B")
        )
        val out = PromptLog.formatAll(entries, ::ts)
        assertTrue(out.contains("PROMPT-A"))
        assertTrue(out.contains("RESPONSE-A"))
        assertTrue(out.contains("PROMPT-B"))
        assertTrue(out.contains("RESPONSE-B"))
        // Both entries are labelled/distinguishable.
        assertTrue(out.contains("Entry 1 of 2"))
        assertTrue(out.contains("Entry 2 of 2"))
        assertTrue(out.contains("GENERATE PROGRAM"))
        assertTrue(out.contains("--- PROMPT ---"))
        assertTrue(out.contains("--- AI RESPONSE ---"))
    }

    @Test fun ordersEntriesAsGiven() {
        val entries = listOf(
            PromptLog.Entry(1000L, "a", "first", "r1"),
            PromptLog.Entry(2000L, "b", "second", "r2")
        )
        val out = PromptLog.formatAll(entries, ::ts)
        assertTrue(out.indexOf("first") < out.indexOf("second"))
    }
}
