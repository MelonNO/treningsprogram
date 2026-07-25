package com.migul.treningsprogram

import com.migul.treningsprogram.domain.GymExclusions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 02 (per-gym "exercises to avoid"): parsing, serialization, and — most importantly — the
 * name-variant matching contract that backs the HARD exclusion guarantee. The brief's own
 * examples are pinned verbatim: the Chest-Supported Dumbbell Row exclusion must catch its
 * phrasing/setup variants while seal rows, plain dumbbell rows, and chest-supported CURLS
 * (different movements) all stay allowed.
 */
class GymExclusionsTest {

    private val homeAvoid = listOf("Chest-Supported Dumbbell Row")

    // ── parse / toJson ──────────────────────────────────────────────────────────────────────────

    @Test fun parse_nullBlankMalformed_giveEmpty() {
        assertTrue(GymExclusions.parse(null).isEmpty())
        assertTrue(GymExclusions.parse("").isEmpty())
        assertTrue(GymExclusions.parse("  ").isEmpty())
        assertTrue(GymExclusions.parse("not json").isEmpty())
        assertTrue(GymExclusions.parse("{\"a\":1}").isEmpty())
    }

    @Test fun parse_defaultHomeGymJson_yieldsTheConfirmedExclusion() {
        assertEquals(homeAvoid, GymExclusions.parse(GymExclusions.HOME_GYM_DEFAULT_JSON))
    }

    @Test fun parse_dropsBlankEntries_andTrims() {
        assertEquals(listOf("Bench Press"), GymExclusions.parse("""["  Bench Press  ", "", "   "]"""))
    }

    @Test fun toJson_roundTrips_andEmptyBecomesNull() {
        assertNull(GymExclusions.toJson(emptyList()))
        assertNull(GymExclusions.toJson(listOf(" ", "")))
        assertEquals(homeAvoid, GymExclusions.parse(GymExclusions.toJson(homeAvoid)))
    }

    // ── matching: the brief's variants MUST match ───────────────────────────────────────────────

    @Test fun exactName_matches() {
        assertTrue(GymExclusions.matches("Chest-Supported Dumbbell Row", homeAvoid))
    }

    @Test fun caseAndPunctuation_insensitive() {
        assertTrue(GymExclusions.matches("chest supported dumbbell row", homeAvoid))
        assertTrue(GymExclusions.matches("CHEST-SUPPORTED DUMBBELL ROW", homeAvoid))
    }

    @Test fun wordOrderVariant_matches() {
        assertTrue(GymExclusions.matches("Dumbbell Chest-Supported Row", homeAvoid))
    }

    @Test fun equipmentOmittedVariant_matches() {
        assertTrue(GymExclusions.matches("Chest Supported Row", homeAvoid))
    }

    @Test fun inclineBenchPhrasing_matches() {
        assertTrue(GymExclusions.matches("Dumbbell Incline Bench Chest Supported Row", homeAvoid))
        assertTrue(GymExclusions.matches("Incline Chest-Supported Dumbbell Row", homeAvoid))
    }

    @Test fun pluralRows_match() {
        assertTrue(GymExclusions.matches("Chest-Supported Dumbbell Rows", homeAvoid))
    }

    // ── matching: different movements MUST stay allowed (brief scope) ───────────────────────────

    @Test fun plainDumbbellRow_staysAllowed() {
        assertFalse(GymExclusions.matches("Dumbbell Row", homeAvoid))
        assertFalse(GymExclusions.matches("Single-Arm Dumbbell Row", homeAvoid))
    }

    @Test fun sealRow_staysAllowed() {
        assertFalse(GymExclusions.matches("Seal Row", homeAvoid))
    }

    @Test fun chestSupportedCurl_staysAllowed() {
        assertFalse(GymExclusions.matches("Chest-Supported Dumbbell Curl", homeAvoid))
    }

    @Test fun unrelatedExercises_stayAllowed() {
        assertFalse(GymExclusions.matches("Bench Press", homeAvoid))
        assertFalse(GymExclusions.matches("Barbell Row", homeAvoid))
        assertFalse(GymExclusions.matches("Chest Fly", homeAvoid))
    }

    // ── matching: no cross-movement bleed through shared modifier words ─────────────────────────

    @Test fun benchPressExclusion_neverCatchesLegPress() {
        val avoid = listOf("Bench Press")
        assertTrue(GymExclusions.matches("Bench Press", avoid))
        assertTrue(GymExclusions.matches("Dumbbell Bench Press", avoid))
        assertFalse("different movement", GymExclusions.matches("Leg Press", avoid))
        assertFalse("different movement", GymExclusions.matches("Shoulder Press", avoid))
        assertFalse("different movement", GymExclusions.matches("Overhead Press", avoid))
    }

    @Test fun squatExclusion_doesNotCatchFrontSquat() {
        assertFalse(GymExclusions.matches("Front Squat", listOf("Squat")))
        assertTrue(GymExclusions.matches("Squat", listOf("Squat")))
    }

    @Test fun emptyExclusions_matchNothing() {
        assertFalse(GymExclusions.matches("Chest-Supported Dumbbell Row", emptyList()))
    }

    // ── filter: the deterministic enforcement used on every parsed plan ─────────────────────────

    @Test fun filter_stripsOnlyMatchingExercises() {
        val plan = listOf("Bench Press", "Chest Supported Row", "Squat", "Dumbbell Chest-Supported Row")
        val kept = GymExclusions.filter(plan, homeAvoid) { it }
        assertEquals(listOf("Bench Press", "Squat"), kept)
    }

    @Test fun filter_noExclusions_isIdentity() {
        val plan = listOf("Bench Press", "Squat")
        assertEquals(plan, GymExclusions.filter(plan, emptyList()) { it })
    }

    // ── prompt line ─────────────────────────────────────────────────────────────────────────────

    @Test fun promptLine_emptyForNoExclusions_hardWordingOtherwise() {
        assertEquals("", GymExclusions.promptLine(emptyList()))
        val line = GymExclusions.promptLine(homeAvoid)
        assertTrue(line.contains("NEVER include"))
        assertTrue(line.contains("Chest-Supported Dumbbell Row"))
    }
}
