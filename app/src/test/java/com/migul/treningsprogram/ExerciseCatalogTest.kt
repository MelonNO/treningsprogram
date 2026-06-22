package com.migul.treningsprogram

import com.migul.treningsprogram.data.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseCatalogTest {

    @Test fun normalizeName_lowercasesAndTrims() {
        assertEquals("bench press", ExerciseCatalog.normalizeName("  Bench Press  "))
    }

    @Test fun normalizeName_stripsParenthetical() {
        // "(Alternating, Standing)" should be removed before other processing
        assertEquals("dumbbell hammer curl", ExerciseCatalog.normalizeName("Dumbbell Hammer Curl (Alternating, Standing)"))
    }

    @Test fun normalizeName_expandsDbAbbreviation() {
        assertEquals("dumbbell curl", ExerciseCatalog.normalizeName("db curl"))
    }

    @Test fun normalizeName_expandsBbAbbreviation() {
        assertEquals("barbell row", ExerciseCatalog.normalizeName("BB row"))
    }

    @Test fun normalizeName_stripsPunctuationExceptSpaces() {
        assertEquals("romanian deadlift", ExerciseCatalog.normalizeName("Romanian-Deadlift"))
    }

    @Test fun normalizeName_stemPlurals() {
        // "curls" → "curl", "rows" → "row"
        val normalized = ExerciseCatalog.normalizeName("Hammer Curls")
        // should end without trailing 's' after stemming
        assert(!normalized.endsWith("s")) { "Expected plural to be stemmed, got: $normalized" }
    }

    @Test fun normalizeName_collapsesWhitespace() {
        val result = ExerciseCatalog.normalizeName("Bench   Press")
        assert(!result.contains("  ")) { "Expected no double spaces, got: $result" }
    }

    @Test fun normalizeName_doubleSpaces_collapsed() {
        val result = ExerciseCatalog.normalizeName("Dumbbell  Bench  Press")
        assertEquals("dumbbell bench press", result)
    }

    @Test fun normalizeName_allCaps_lowercased() {
        assertEquals("bench press", ExerciseCatalog.normalizeName("BENCH PRESS"))
    }

    @Test fun normalizeName_mixedCase_lowercased() {
        assertEquals("barbell squat", ExerciseCatalog.normalizeName("Barbell Squat"))
    }

    @Test fun normalizeName_dbNotSubstring_onlyWordBoundary() {
        // "adbs" should NOT expand; only standalone \bdb\b should expand
        val result = ExerciseCatalog.normalizeName("adbs curl")
        assert(!result.contains("dumbbell")) { "Should not expand 'adbs' as 'db', got: $result" }
    }

    @Test fun normalizeName_unknownInput_doesNotThrow() {
        // Should not throw for inputs with no matching aliases
        val result = ExerciseCatalog.normalizeName("xyzzyx plorp flibble")
        assertEquals("xyzzyx plorp flibble", result)
    }

    @Test fun normalizeName_idempotent() {
        val inputs = listOf(
            "Dumbbell Bench Press",
            "BB Row",
            "Hammer Curls (Alternating)",
            "Romanian-Deadlift",
            "BENCH PRESS"
        )
        for (input in inputs) {
            val once = ExerciseCatalog.normalizeName(input)
            val twice = ExerciseCatalog.normalizeName(once)
            assertEquals("normalize() must be idempotent for: $input", once, twice)
        }
    }

    @Test fun normalizeName_emptyString_returnsEmpty() {
        assertEquals("", ExerciseCatalog.normalizeName(""))
    }

    @Test fun normalizeName_ohpAbbreviation_expandsToOverheadPress() {
        val result = ExerciseCatalog.normalizeName("ohp")
        assertEquals("overhead press", result)
    }

    @Test fun normalizeName_kbAbbreviation_expandsToKettlebell() {
        val result = ExerciseCatalog.normalizeName("kb swing")
        assertEquals("kettlebell swing", result)
    }

    @Test fun normalizeName_resistanceBandSynonym_normalizedToBand() {
        val result = ExerciseCatalog.normalizeName("Resistance Band Row")
        assertEquals("band row", result)
    }

    @Test fun normalizeName_bodyWeightSynonym_normalizedToBodyweight() {
        val result = ExerciseCatalog.normalizeName("Body Weight Squat")
        assertEquals("bodyweight squat", result)
    }

    @Test fun normalizeName_pluralStemming_onlyForLongTokens() {
        // Token < 5 chars: "rows" → not stemmed (4 chars); "curls" → not stemmed (5 chars — exactly 5, IS stemmed)
        val rows = ExerciseCatalog.normalizeName("rows")
        // "rows" has 4 chars — below threshold, NOT stemmed
        assertEquals("rows", rows)
        val curls = ExerciseCatalog.normalizeName("curls")
        // "curls" has 5 chars AND ends in 's' but not 'ss' → stemmed to "curl"
        assertEquals("curl", curls)
    }

    @Test fun normalizeName_doubleS_notStemmed() {
        // "press" ends with "ss" → must not be stemmed
        val result = ExerciseCatalog.normalizeName("Bench Press")
        assertEquals("bench press", result)
        assert(!result.endsWith("pre")) { "Should not stem 'press': $result" }
    }
}
