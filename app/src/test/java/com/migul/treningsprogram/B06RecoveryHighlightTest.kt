package com.migul.treningsprogram

import com.migul.treningsprogram.ui.history.HistoryRecapFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B06: tapping a recovering muscle on Home opens its last session and highlights every
 * exercise in that session that hit the tapped muscle. This guards the pure attribution
 * step — "given a session's exercise names + a tapped fine-muscle, which exercises get
 * highlighted" — which reuses [com.migul.treningsprogram.data.MuscleClassifier.finerMusclesFor]
 * (the v1.9.0 recovery rework taxonomy). The scroll/tint UI is exercised on-device.
 */
class B06RecoveryHighlightTest {

    private fun hit(names: List<String>, muscle: String?) =
        HistoryRecapFragment.exercisesHittingMuscle(names, muscle)

    @Test fun quads_highlightsAllQuadDominantExercises_notOthers() {
        val session = listOf("Back Squat", "Leg Press", "Bench Press", "Bicep Curl")
        // Squat + leg press both list "Quads"; bench/curl do not.
        assertEquals(setOf("Back Squat", "Leg Press"), hit(session, "Quads"))
    }

    @Test fun frontDelts_picksUpPressesAsSynergist() {
        // Bench press lists Front Delts as a 0.6 synergist; OHP as primary; row/squat do not.
        val session = listOf("Bench Press", "Overhead Press", "Barbell Row", "Back Squat")
        assertEquals(setOf("Bench Press", "Overhead Press"), hit(session, "Front Delts"))
    }

    @Test fun triceps_includesPressesAndIsolation() {
        val session = listOf("Bench Press", "Tricep Pushdown", "Lateral Raise", "Bicep Curl")
        // Bench (synergist) + tricep pushdown (primary); lateral raise & curl miss.
        assertEquals(setOf("Bench Press", "Tricep Pushdown"), hit(session, "Triceps"))
    }

    @Test fun caseInsensitiveMuscleLabel() {
        val session = listOf("Back Squat", "Leg Press")
        assertEquals(setOf("Back Squat", "Leg Press"), hit(session, "quads"))
        assertEquals(setOf("Back Squat", "Leg Press"), hit(session, "QUADS"))
    }

    @Test fun blankOrNullMuscle_highlightsNothing() {
        val session = listOf("Back Squat", "Bench Press")
        assertTrue(hit(session, null).isEmpty())
        assertTrue(hit(session, "").isEmpty())
        assertTrue(hit(session, "   ").isEmpty())
    }

    @Test fun muscleNotPresentInSession_highlightsNothing() {
        val session = listOf("Bench Press", "Tricep Pushdown")
        // No leg work logged → tapping a leg muscle highlights nothing.
        assertTrue(hit(session, "Calves").isEmpty())
        assertTrue(hit(session, "Hamstrings").isEmpty())
    }

    @Test fun deadlift_attributesToLowerBackHamstringsGlutes() {
        val session = listOf("Deadlift", "Lat Pulldown", "Plank")
        // Conventional deadlift hits Lower Back (primary), Hamstrings & Glutes (synergists).
        assertEquals(setOf("Deadlift"), hit(session, "Lower Back"))
        assertEquals(setOf("Deadlift"), hit(session, "Hamstrings"))
        assertEquals(setOf("Deadlift"), hit(session, "Glutes"))
    }

    @Test fun upperBack_groupsPullsTogether() {
        val session = listOf("Lat Pulldown", "Barbell Row", "Bench Press")
        assertEquals(setOf("Lat Pulldown", "Barbell Row"), hit(session, "Upper Back"))
    }

    @Test fun duplicateExerciseNames_collapseToSingleEntry() {
        // A session can log the same exercise twice; the highlight set must be de-duplicated.
        val session = listOf("Back Squat", "Back Squat", "Bench Press")
        assertEquals(setOf("Back Squat"), hit(session, "Quads"))
    }

    @Test fun unclassifiableExercise_neverHighlights() {
        val session = listOf("Mystery Move", "Bench Press")
        // "Mystery Move" classifies to nothing; only bench (Chest) is found for Chest.
        assertEquals(setOf("Bench Press"), hit(session, "Chest"))
        assertTrue(hit(session, "Quads").isEmpty())
    }
}
