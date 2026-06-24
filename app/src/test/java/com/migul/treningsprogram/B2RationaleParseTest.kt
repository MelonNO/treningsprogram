package com.migul.treningsprogram

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature B2: "Why did the program change?" rationale.
 *
 * Locks the PURE rationale-parsing contract used by AiRepository.parseRationale: the model emits a
 * top-level "rationale" string as a SIBLING of "days"; when present it is extracted (and trimmed),
 * and when absent or blank the result is "" (the neutral state the Program tab uses to HIDE the
 * card). Mirrors the private ProgramJson shape + the parseRationale helper so this stays pure JVM
 * (no Room / Android), matching the repo's existing test style (see ProgramJsonParsingTest).
 */
class B2RationaleParseTest {

    private val gson = Gson()

    // Mirror of the private ProgramJson in AiRepository (rationale is a sibling of days).
    private data class DayJson(val dayOfWeek: Int = 1, val name: String = "")
    private data class ProgramJson(val days: List<DayJson> = emptyList(), val rationale: String = "")

    // Mirror of AiRepository.parseRationale.
    private fun parseRationale(cleanJson: String): String =
        runCatching { gson.fromJson(cleanJson, ProgramJson::class.java).rationale.trim() }
            .getOrDefault("")

    @Test fun rationale_present_isExtracted() {
        val json = """
            {
              "rationale": "Added posterior-chain volume because your hamstrings lagged; bumped squat load after three progressing sessions.",
              "days": [ {"dayOfWeek":1,"name":"Monday"} ]
            }
        """.trimIndent()
        assertEquals(
            "Added posterior-chain volume because your hamstrings lagged; bumped squat load after three progressing sessions.",
            parseRationale(json)
        )
    }

    @Test fun rationale_absent_isNeutralEmpty() {
        // Old-style response (and any response the model forgot the field on): no "rationale" key.
        val json = """{"days":[{"dayOfWeek":1,"name":"Monday"}]}"""
        assertEquals("", parseRationale(json))
        assertTrue(parseRationale(json).isBlank())
    }

    @Test fun rationale_blankOrWhitespace_normalizedToEmpty() {
        val json = """{"rationale":"   ","days":[]}"""
        assertEquals("", parseRationale(json))
    }

    @Test fun rationale_surroundingWhitespace_isTrimmed() {
        val json = """{"rationale":"  Swapped barbell bench for dumbbell to ease the flagged shoulder.  ","days":[]}"""
        assertEquals("Swapped barbell bench for dumbbell to ease the flagged shoulder.", parseRationale(json))
    }

    @Test fun rationale_malformedJson_doesNotThrow_returnsEmpty() {
        // parseRationale must be defensive: a non-JSON / garbage payload yields "" not a crash.
        assertEquals("", parseRationale("not json at all"))
    }
}
