package com.migul.treningsprogram

import com.migul.treningsprogram.data.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the exercise name resolution and normalization used by ExerciseDbResolver.
 * These are pure logic tests — no Android context needed.
 */
class ExerciseDbResolverTest {

    private fun normalize(raw: String) = ExerciseCatalog.normalizeName(raw)

    // --- Alias resolution via normalization ---

    @Test fun aliasSeedKey_hammerCurl_stripsParenthetical() {
        // The seed map key is "dumbbell hammer curl", not the full name with "(Alternating, Standing)"
        val result = normalize("Dumbbell Hammer Curl (Alternating, Standing)")
        assertEquals("dumbbell hammer curl", result)
    }

    @Test fun aliasSeedKey_conventionalDeadlift_normalizes() {
        assertEquals("conventional deadlift", normalize("Conventional Deadlift"))
    }

    @Test fun aliasSeedKey_gobletSquat_normalizes() {
        assertEquals("dumbbell goblet squat", normalize("Dumbbell Goblet Squat"))
    }

    @Test fun aliasSeedKey_pallofPress_normalizes() {
        assertEquals("pallof press", normalize("Pallof Press"))
    }

    @Test fun aliasSeedKey_dbExpandedToFull() {
        assertEquals("dumbbell bench press", normalize("db bench press"))
    }

    @Test fun aliasSeedKey_bbExpandedToFull() {
        assertEquals("barbell row", normalize("BB Row"))
    }

    @Test fun aliasSeedKey_punctuationStripped() {
        assertEquals("romanian deadlift", normalize("Romanian-Deadlift"))
    }

    @Test fun aliasSeedKey_nativeParensKept_ifOutside() {
        // Parenthetical content inside is stripped, outer words remain
        val result = normalize("Plank (Side)")
        assertFalse("Should not contain parenthesis", result.contains("("))
        assertTrue("Should still contain plank", result.contains("plank"))
    }

    @Test fun aliasSeedKey_ankleAlphabet_normalizes() {
        assertEquals("ankle alphabet", normalize("Ankle Alphabet"))
    }

    @Test fun aliasSeedKey_nordicHamstringCurl_normalizes() {
        assertEquals("nordic hamstring curl", normalize("Nordic Hamstring Curl"))
    }
}
