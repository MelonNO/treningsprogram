package com.migul.treningsprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the extractJson logic mirrored from AiRepository.extractJson (private).
 * Logic: prefer ```json...``` fences; else find first '{' to last '}'; else throw.
 */
class ExtractJsonTest {

    // Mirror of AiRepository.extractJson
    private fun extractJson(text: String): String {
        val fenceMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
        if (fenceMatch != null) return fenceMatch.groupValues[1].trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start != -1 && end > start) return text.substring(start, end + 1)
        throw IllegalStateException("No JSON found in AI response")
    }

    @Test fun extractJson_plainJson_returnedAsIs() {
        val input = """{"days":[]}"""
        assertEquals("""{"days":[]}""", extractJson(input))
    }

    @Test fun extractJson_jsonFence_extractsInner() {
        val input = "```json\n{\"days\":[]}\n```"
        assertEquals("""{"days":[]}""", extractJson(input))
    }

    @Test fun extractJson_plainFence_extractsInner() {
        val input = "```\n{\"days\":[]}\n```"
        assertEquals("""{"days":[]}""", extractJson(input))
    }

    @Test fun extractJson_proseBeforeAndAfter_stripsToJson() {
        val input = "Here is your plan:\n{\"days\":[]}\nEnd of plan."
        assertEquals("""{"days":[]}""", extractJson(input))
    }

    @Test fun extractJson_fencePreferredOverBraceFallback() {
        // If fences present, use them even if bare braces also exist
        val input = "extra { junk } ```json\n{\"days\":[]}\n``` trailing {}"
        assertEquals("""{"days":[]}""", extractJson(input))
    }

    @Test fun extractJson_multilineJson_preservedInsideFence() {
        val json = "{\n  \"days\": [\n    {\"dayOfWeek\": 1}\n  ]\n}"
        val input = "```json\n$json\n```"
        assertEquals(json, extractJson(input))
    }

    @Test fun extractJson_noJson_throwsIllegalState() {
        var thrown = false
        try {
            extractJson("No JSON here at all")
        } catch (e: IllegalStateException) {
            thrown = true
            assertTrue(e.message?.contains("No JSON") == true)
        }
        assertTrue("Expected IllegalStateException", thrown)
    }

    @Test fun extractJson_emptyString_throwsIllegalState() {
        var thrown = false
        try {
            extractJson("")
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue("Expected IllegalStateException on empty input", thrown)
    }

    @Test fun extractJson_onlyOpenBrace_throwsIllegalState() {
        // end <= start so no valid JSON window
        var thrown = false
        try {
            extractJson("{no closing brace")
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue("Expected exception when no closing brace", thrown)
    }

    @Test fun extractJson_nestedJson_takesOutermostBraces() {
        val input = """{"a":{"b":1}}"""
        assertEquals("""{"a":{"b":1}}""", extractJson(input))
    }

    @Test fun extractJson_fence_leadingWhitespaceStripped() {
        val input = "```json\n   {\"days\":[]}   \n```"
        assertEquals("""{"days":[]}""", extractJson(input))
    }
}
