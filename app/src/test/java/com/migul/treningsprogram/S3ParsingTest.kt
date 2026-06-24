package com.migul.treningsprogram

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.migul.treningsprogram.data.repository.balancedJsonSpan
import com.migul.treningsprogram.data.repository.extractJsonOrThrow
import com.migul.treningsprogram.data.repository.stripJsonFences
import com.migul.treningsprogram.data.repository.stripTrailingCommas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * S3: hardened AI-response parsing seam.
 *
 * Unlike the older ExtractJsonTest / ProgramJsonParsingTest (which hand-copied a MIRROR of the logic),
 * this exercises the REAL production functions [extractJsonOrThrow], [stripJsonFences],
 * [balancedJsonSpan] and [stripTrailingCommas] (all package-level `internal`, so visible to this
 * same-module test). The whole-pipeline tests below also run the extracted JSON through Gson against
 * a model that mirrors AiRepository.ProgramJson, to prove that what extraction emits actually parses
 * (or, for genuinely-broken input, that the pipeline still THROWS — never a silent empty success).
 */
class S3ParsingTest {

    private val gson = Gson()

    // Mirror of AiRepository's private ProgramJson/DayJson/ExJson shapes (production keeps them private).
    private data class ExJson(
        val name: String = "",
        val sets: Int = 3,
        @SerializedName("targetReps") val targetReps: String = "8-12",
        @SerializedName("targetWeightKg") val targetWeightKg: Float = 0f,
        val notes: String = "",
        val recommendedRestSeconds: Int = 90
    )
    private data class DayJson(val dayOfWeek: Int = 1, val name: String = "", val exercises: List<ExJson> = emptyList())
    private data class ProgramJson(val days: List<DayJson> = emptyList(), val rationale: String = "")

    /** Mirrors the production parse: extract → strip trailing commas → gson → null-guard. */
    private fun parsePipeline(raw: String): ProgramJson {
        val clean = stripTrailingCommas(extractJsonOrThrow(raw))
        return gson.fromJson(clean, ProgramJson::class.java)
            ?: throw IllegalStateException("AI response did not contain a JSON object")
    }

    // ── extractJsonOrThrow: happy paths ────────────────────────────────────────────────────────

    @Test fun plainJson_returnedAsIs() {
        assertEquals("""{"days":[]}""", extractJsonOrThrow("""{"days":[]}"""))
    }

    @Test fun completeJsonFence_extractsInner() {
        assertEquals("""{"days":[]}""", extractJsonOrThrow("```json\n{\"days\":[]}\n```"))
    }

    @Test fun completePlainFence_extractsInner() {
        assertEquals("""{"days":[]}""", extractJsonOrThrow("```\n{\"days\":[]}\n```"))
    }

    @Test fun nestedObject_takesOutermostBalancedSpan() {
        assertEquals("""{"a":{"b":1}}""", extractJsonOrThrow("""{"a":{"b":1}}"""))
    }

    // ── extractJsonOrThrow: the hardened cases ──────────────────────────────────────────────────

    @Test fun prosePreambleAndTrailer_stripsToJson() {
        val input = "Here is your program:\n{\"days\":[]}\nHope this helps!"
        assertEquals("""{"days":[]}""", extractJsonOrThrow(input))
    }

    @Test fun trailingProseWithBraces_balancedSpanStopsAtRealClose() {
        // Old first-{/last-} fallback would have grabbed through the trailing brace; balanced scan
        // stops at the matching close.
        val input = "{\"days\":[{\"dayOfWeek\":1}]} -- note: {not json}"
        assertEquals("""{"days":[{"dayOfWeek":1}]}""", extractJsonOrThrow(input))
    }

    @Test fun openingFenceOnly_completeJson_recoversViaBalancedSpan() {
        // The common partial-response shape: opening ```json present, closing fence truncated away,
        // but the JSON object itself is complete. Must still recover.
        val input = "```json\n{\"days\":[{\"dayOfWeek\":1,\"name\":\"Mon\"}],\"rationale\":\"ok\"}"
        assertEquals("""{"days":[{"dayOfWeek":1,"name":"Mon"}],"rationale":"ok"}""", extractJsonOrThrow(input))
    }

    @Test fun braceInsideStringValue_ignoredByBalancedScan() {
        val input = "{\"rationale\":\"use a } brace { in text\",\"days\":[]}"
        // Whole object recovered; the braces inside the string don't unbalance the scan.
        assertEquals("{\"rationale\":\"use a } brace { in text\",\"days\":[]}", extractJsonOrThrow(input))
    }

    // ── extractJsonOrThrow: residual-unparseable MUST still throw (no silent success) ────────────

    @Test fun pureProseNoJson_throws() = assertThrowsNoJson { extractJsonOrThrow("No JSON here at all.") }

    @Test fun emptyString_throws() = assertThrowsNoJson { extractJsonOrThrow("") }

    @Test fun openingFenceWithoutAnyBrace_throws() =
        assertThrowsNoJson { extractJsonOrThrow("```json\n(the model started talking but emitted no object)") }

    @Test fun openBraceNoCloseAtAll_throws() {
        // Brief case 1: opening fence + a '{' but the closing brace was ALSO truncated → genuinely
        // unrecoverable. Must surface the clear "No JSON found" error, not a silent empty plan.
        assertThrowsNoJson { extractJsonOrThrow("```json\n{\"days\":[{\"dayOfWeek\":1,\"name\":\"Mon") }
    }

    // ── balancedJsonSpan unit behavior ──────────────────────────────────────────────────────────

    @Test fun balancedSpan_noBrace_returnsNull() = assertNull(balancedJsonSpan("no object here"))

    @Test fun balancedSpan_truncatedNoClose_returnsNull() =
        assertNull(balancedJsonSpan("{\"days\":[{\"dayOfWeek\":1"))

    @Test fun balancedSpan_stopsAtFirstBalancedClose() =
        assertEquals("{\"a\":1}", balancedJsonSpan("prefix {\"a\":1} suffix {\"b\":2}"))

    // ── stripJsonFences ─────────────────────────────────────────────────────────────────────────

    @Test fun stripFences_completeFence_returnsInnerTrimmed() {
        assertEquals("""{"days":[]}""", stripJsonFences("```json\n   {\"days\":[]}   \n```"))
    }

    @Test fun stripFences_noFence_unchanged() {
        assertEquals("""{"days":[]}""", stripJsonFences("""{"days":[]}"""))
    }

    @Test fun stripFences_openingFenceOnly_markerRemoved() {
        val out = stripJsonFences("```json\n{\"days\":[]}")
        assertTrue("opening fence marker should be gone", !out.contains("```"))
        assertTrue(out.contains("{\"days\":[]}"))
    }

    // ── stripTrailingCommas ─────────────────────────────────────────────────────────────────────

    @Test fun trailingComma_beforeObjectClose_removed() {
        assertEquals("""{"a":1}""", stripTrailingCommas("""{"a":1,}"""))
    }

    @Test fun trailingComma_beforeArrayClose_removed() {
        assertEquals("""{"d":[1,2]}""", stripTrailingCommas("""{"d":[1,2,]}"""))
    }

    @Test fun trailingComma_withWhitespace_removed() {
        // Only the comma is dropped (insignificant whitespace is left as-is); the result must contain
        // no comma before the closing brace and must parse.
        val out = stripTrailingCommas("{\"a\":1 ,\n }")
        assertTrue("comma before close should be gone", !out.contains(","))
        assertEquals(1, gson.fromJson(out, com.google.gson.JsonObject::class.java).get("a").asInt)
    }

    @Test fun commaInsideString_preserved() {
        assertEquals("""{"r":"a, b, c"}""", stripTrailingCommas("""{"r":"a, b, c"}"""))
    }

    @Test fun legitimateSeparatorComma_preserved() {
        assertEquals("""{"a":1,"b":2}""", stripTrailingCommas("""{"a":1,"b":2}"""))
    }

    // ── whole pipeline: produces parseable JSON, or throws — never silent empty ──────────────────

    @Test fun pipeline_cleanProgram_parses() {
        val raw = """{"days":[{"dayOfWeek":1,"name":"Mon","exercises":[{"name":"Squat","sets":4}]}],"rationale":"built for strength"}"""
        val p = parsePipeline(raw)
        assertEquals(1, p.days.size)
        assertEquals("Squat", p.days[0].exercises[0].name)
        assertEquals("built for strength", p.rationale)
    }

    @Test fun pipeline_fencedWithTrailingCommaObject_parses() {
        // Trailing comma in an OBJECT is exactly the case Gson rejects without sanitising.
        val raw = "```json\n{\"days\":[],\"rationale\":\"hi\",}\n```"
        val p = parsePipeline(raw)
        assertEquals(0, p.days.size)
        assertEquals("hi", p.rationale)
    }

    @Test fun pipeline_trailingCommaArray_noPhantomDay() {
        // Without sanitising, Gson would materialise a phantom null Day from the trailing comma.
        val raw = """{"days":[{"dayOfWeek":1,"name":"Mon"},]}"""
        val p = parsePipeline(raw)
        assertEquals(1, p.days.size)
        assertEquals("Mon", p.days[0].name)
    }

    @Test fun pipeline_openingFenceOnlyComplete_parses() {
        val raw = "```json\n{\"days\":[{\"dayOfWeek\":2,\"name\":\"Tue\",\"exercises\":[]}],\"rationale\":\"r\"}"
        val p = parsePipeline(raw)
        assertEquals(2, p.days[0].dayOfWeek)
    }

    @Test fun pipeline_pureProse_throws() = assertThrowsNoJson { parsePipeline("Sorry, I can't help with that.") }

    @Test fun pipeline_truncatedMidObject_throwsNotSilent() {
        // Extraction may surface a residual span; Gson then rejects it. The point: it THROWS, it does
        // not return an empty/partial ProgramJson silently.
        var threw = false
        try {
            parsePipeline("{\"days\":[{\"dayOfWeek\":1,\"name\":\"Mon\"},{\"dayOfWeek")
        } catch (e: Throwable) {
            threw = true
        }
        assertTrue("Truncated mid-object must throw, never silently succeed", threw)
    }

    @Test fun pipeline_jsonNullLiteral_throws() {
        // gson.fromJson("null", ...) returns null → the null-guard must convert it to a thrown error.
        var threw = false
        try { parsePipeline("null") } catch (e: Throwable) { threw = true }
        assertTrue("A bare JSON null must not yield a null program object silently", threw)
    }

    // ── helper ──────────────────────────────────────────────────────────────────────────────────

    private inline fun assertThrowsNoJson(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalStateException to be thrown")
        } catch (e: IllegalStateException) {
            assertTrue(
                "message should be the clear No-JSON error, was: ${e.message}",
                e.message?.contains("No JSON") == true
            )
        }
    }
}
