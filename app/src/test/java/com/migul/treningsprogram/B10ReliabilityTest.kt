package com.migul.treningsprogram

import com.migul.treningsprogram.data.api.model.ClaudeResponse
import com.migul.treningsprogram.data.repository.extractJsonOrNull
import com.migul.treningsprogram.data.repository.extractJsonOrThrow
import com.migul.treningsprogram.data.repository.isLikelyTruncated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B10: AI prose-instead-of-JSON — recover (retry) + prevent.
 *
 * Exercises the REAL production seam the generation loop now routes through:
 *  - [extractJsonOrNull] — non-throwing extraction; returns null on a no-JSON / unrecoverable
 *    response so the loop can treat it as a RETRYABLE rejected attempt instead of an escaping throw.
 *  - [isLikelyTruncated] — the truncation signal (API stop_reason == "max_tokens", OR a JSON object
 *    that started but never balanced/closed) the loop uses to mark a cut-off response retryable.
 *  - [ClaudeResponse.hitTokenLimit] / stop_reason parsing — the API-level truncation flag.
 *
 * These are the pure-logic pieces; the loop wiring (no-JSON/truncated → rejected attempt → retry →
 * clear failure after MAX_GENERATION_ATTEMPTS) is built on top of them.
 */
class B10ReliabilityTest {

    // ── extractJsonOrNull: no-JSON / truncated map to "null" (→ retryable rejection) ────────────────

    @Test
    fun `prose-only response with no JSON returns null`() {
        val prose = """
            I'll work through this systematically before generating the JSON. First, let me consider
            the user's goal of hypertrophy and their three training days. I want to balance the push,
            pull and legs across the week while respecting the injury constraints and...
        """.trimIndent()
        assertNull(extractJsonOrNull(prose))
    }

    @Test
    fun `empty response returns null`() {
        assertNull(extractJsonOrNull(""))
        assertNull(extractJsonOrNull("   \n  "))
    }

    @Test
    fun `truncated JSON that never closes returns null`() {
        // Opening brace present, but cut off mid-object — no balanced span, no last '}' after start.
        val truncated = """
            Here is your plan:
            {"rationale": "Bumped squat load after three progressing sessions", "days": [
              {"dayOfWeek": 1, "name": "Push", "exercises": [
                {"name": "Bench Press", "sets": 4, "targetReps": "6-8
        """.trimIndent()
        assertNull(extractJsonOrNull(truncated))
    }

    // ── extractJsonOrNull: reasoning-then-JSON still extracts (must NOT reject a usable plan) ────────

    @Test
    fun `reasoning preamble followed by valid JSON still extracts`() {
        val reasoningThenJson = """
            Let me think through this. The user is hypertrophy-focused with 3 days, so I'll spread
            volume across push/pull/legs. Here is the plan:

            {"rationale": "Spread volume across PPL", "days": [
              {"dayOfWeek": 1, "name": "Push", "exercises": [
                {"name": "Bench Press", "sets": 4, "targetReps": "6-8", "targetWeightKg": 80.0,
                 "notes": "RPE 8", "recommendedRestSeconds": 120}
              ]}
            ]}

            That should progress them well.
        """.trimIndent()
        val extracted = extractJsonOrNull(reasoningThenJson)
        // It must extract a balanced object (trailing prose ignored), identical to the throwing path.
        assertEquals(extractJsonOrThrow(reasoningThenJson), extracted)
        assertNotNull(extracted)
        assertTrue(extracted!!.startsWith("{") && extracted.endsWith("}"))
    }

    @Test
    fun `fenced reasoning-then-JSON still extracts`() {
        val fenced = """
            Thinking about the split first…

            ```json
            {"rationale": "PPL split", "days": [{"dayOfWeek": 1, "name": "Push", "exercises": []}]}
            ```
        """.trimIndent()
        val extracted = extractJsonOrNull(fenced)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("\"rationale\""))
    }

    // ── isLikelyTruncated: API stop_reason == "max_tokens" (authoritative) ──────────────────────────

    @Test
    fun `stop_reason max_tokens flags truncation even when no brace present`() {
        // Worst case: wall of planning prose, response cut off before any JSON, API says max_tokens.
        val prose = "I'll plan this carefully, considering recovery, volume, and the injury gating…"
        assertTrue(isLikelyTruncated(prose, "max_tokens"))
    }

    @Test
    fun `stop_reason max_tokens flags truncation even for otherwise-complete-looking text`() {
        val text = """{"rationale": "x", "days": []}"""
        // The API authority wins: max_tokens means it was cut off regardless of structure.
        assertTrue(isLikelyTruncated(text, "max_tokens"))
    }

    // ── isLikelyTruncated: structural backstop (opened-but-never-closed) ────────────────────────────

    @Test
    fun `unbalanced JSON flags truncation when stop_reason absent`() {
        val unbalanced = """{"rationale": "x", "days": [{"dayOfWeek": 1, "name": "Push", "exercises": ["""
        assertTrue(isLikelyTruncated(unbalanced, null))
        assertTrue(isLikelyTruncated(unbalanced, "end_turn"))
    }

    @Test
    fun `complete balanced JSON is NOT flagged as truncated`() {
        val complete = """{"rationale": "x", "days": [{"dayOfWeek": 1, "name": "Push", "exercises": []}]}"""
        assertFalse(isLikelyTruncated(complete, "end_turn"))
        assertFalse(isLikelyTruncated(complete, null))
    }

    @Test
    fun `prose with no brace and normal stop_reason is NOT flagged truncated`() {
        // No '{' at all + not max_tokens ⇒ classified as "no JSON" (extractJsonOrNull → null),
        // NOT as "truncated". Both are retryable, but the distinction drives the user message.
        val prose = "Here are some general thoughts about your training week."
        assertFalse(isLikelyTruncated(prose, "end_turn"))
        assertNull(extractJsonOrNull(prose))
    }

    // ── ClaudeResponse: stop_reason parsing + hitTokenLimit ─────────────────────────────────────────

    @Test
    fun `ClaudeResponse hitTokenLimit reflects stop_reason`() {
        assertTrue(ClaudeResponse(stopReason = "max_tokens").hitTokenLimit())
        assertFalse(ClaudeResponse(stopReason = "end_turn").hitTokenLimit())
        assertFalse(ClaudeResponse(stopReason = null).hitTokenLimit())
    }

    @Test
    fun `ClaudeResponse defaults stop_reason to null`() {
        assertNull(ClaudeResponse().stopReason)
    }
}
