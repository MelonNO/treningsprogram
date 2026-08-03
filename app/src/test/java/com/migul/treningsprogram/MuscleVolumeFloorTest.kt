package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.MuscleVolumeFloor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 01 (training-data improvements 2026-08-03): per-muscle weekly floor on generated plans.
 *
 * Grounding: on the user's real 5-day Hypertrophy profile, generated weeks allocated Legs to exactly
 * ONE day (a single miss zeroed the muscle for the week) and Shoulders ~3 direct sets — and nothing
 * in the accept path caught either. These tests pin the floor's contract: violating plans are named
 * and rejected, compliant plans pass unchanged, low-frequency/other-goal/deload profiles are exempt
 * (never ungeneratable), and the thresholds live in one adjustable place ([MuscleVolumeFloor]).
 */
class MuscleVolumeFloorTest {

    private fun ex(name: String, day: Int, sets: Int = 3, order: Int = 0) = PlannedExercise(
        weekStart = 0L, dayOfWeek = day, orderInDay = order, exerciseName = name,
        sets = sets, targetReps = "8-10", targetWeightKg = 0f
    )

    /** A 5-day plan a coach would sign off on: every major muscle ≥2 days and ≥6 direct sets. */
    private fun compliantFiveDayPlan() = listOf(
        // Day 1 — upper
        ex("Barbell Bench Press", 1, 4), ex("Barbell Row", 1, 4, 1),
        ex("Lateral Raise", 1, 3, 2), ex("Bicep Curl", 1, 3, 3),
        // Day 2 — lower
        ex("Barbell Squat", 2, 4), ex("Romanian Deadlift", 2, 3, 1), ex("Calf Raise", 2, 3, 2),
        // Day 3 — push
        ex("Incline Dumbbell Press", 3, 4), ex("Overhead Press", 3, 4, 1), ex("Tricep Pushdown", 3, 3, 2),
        // Day 4 — pull
        ex("Lat Pulldown", 4, 4), ex("Face Pull", 4, 3, 1), ex("Hammer Curl", 4, 3, 2),
        // Day 5 — lower 2
        ex("Leg Press", 5, 4), ex("Leg Curl", 5, 3, 1)
    )

    // ── Activation gating ───────────────────────────────────────────────────────────────────────

    @Test fun activeOnlyForHypertrophyAtFourPlusDays() {
        assertTrue(MuscleVolumeFloor.isActive("Hypertrophy", 5))
        assertTrue(MuscleVolumeFloor.isActive("hypertrophy", MuscleVolumeFloor.MIN_PROFILE_DAYS_PER_WEEK))
        assertFalse("below the frequency floor", MuscleVolumeFloor.isActive("Hypertrophy", 3))
        assertFalse("Strength concentrates volume by design", MuscleVolumeFloor.isActive("Strength", 5))
        assertFalse(MuscleVolumeFloor.isActive("Endurance", 5))
        assertFalse(MuscleVolumeFloor.isActive("Weight Loss", 5))
        assertFalse("deload weeks reduce volume deliberately", MuscleVolumeFloor.isActive("Hypertrophy", 5, isDeload = true))
    }

    @Test fun inactiveProfiles_neverViolate_evenOnDegeneratePlans() {
        val lopsided = listOf(ex("Barbell Bench Press", 1, 3)) // 1 day, chest only
        assertNull(MuscleVolumeFloor.violation(lopsided, "Strength", 5))
        assertNull(MuscleVolumeFloor.violation(lopsided, "Hypertrophy", 3))
        assertNull(MuscleVolumeFloor.violation(lopsided, "Hypertrophy", 5, isDeload = true))
    }

    // ── The evidence cases ──────────────────────────────────────────────────────────────────────

    @Test fun singleLegDay_isCaught_andNamesLegs() {
        // The user's real shape: legs concentrated on exactly one day of a 5-day hypertrophy week.
        val plan = compliantFiveDayPlan().filterNot { it.dayOfWeek == 5 } +
            listOf(ex("Push-Up", 5, 3), ex("Overhead Press", 5, 3, 1)) // day 5 no longer trains legs
        val reason = MuscleVolumeFloor.violation(plan, "Hypertrophy", 5)
        assertNotNull(reason); reason!!
        assertTrue("names Legs: $reason", reason.contains("Legs"))
        assertTrue("names the day floor: $reason", reason.contains("1 training day"))
    }

    @Test fun threeSetShoulders_isCaught_onSetFloor() {
        // Shoulders on two days but only 3 direct sets total (the realized 3-sets/week pattern).
        val plan = compliantFiveDayPlan()
            .filterNot { it.exerciseName in setOf("Lateral Raise", "Overhead Press", "Face Pull") } +
            listOf(ex("Lateral Raise", 1, 2, 9), ex("Face Pull", 4, 1, 9))
        val reason = MuscleVolumeFloor.violation(plan, "Hypertrophy", 5)
        assertNotNull(reason); reason!!
        assertTrue("names Shoulders: $reason", reason.contains("Shoulders"))
        assertTrue("names the set floor: $reason", reason.contains("3 direct sets"))
    }

    @Test fun compliantPlan_passesUnchanged() {
        assertNull(MuscleVolumeFloor.violation(compliantFiveDayPlan(), "Hypertrophy", 5))
    }

    @Test fun violationListsEveryFailingMuscle_notJustTheFirst() {
        // Legs single-day AND shoulders starved — both must be named so one retry can fix both.
        val plan = listOf(
            ex("Barbell Bench Press", 1, 4), ex("Incline Dumbbell Press", 3, 4),
            ex("Barbell Row", 1, 4), ex("Lat Pulldown", 4, 4),
            ex("Bicep Curl", 1, 3), ex("Tricep Pushdown", 3, 3),
            ex("Barbell Squat", 2, 4), ex("Leg Curl", 2, 3, 1), // legs: one day only
            ex("Lateral Raise", 3, 3, 2) // shoulders: one day, 3 sets
        )
        val reason = MuscleVolumeFloor.violation(plan, "Hypertrophy", 4)
        assertNotNull(reason); reason!!
        assertTrue(reason.contains("Legs"))
        assertTrue(reason.contains("Shoulders"))
    }

    // ── Classification consistency ──────────────────────────────────────────────────────────────

    @Test fun coverage_usesTheAppClassifier_romanianDeadliftIsLegs() {
        val cov = MuscleVolumeFloor.coverage(listOf(ex("Romanian Deadlift", 2, 3)))
        assertEquals(setOf(2), cov.getValue("Legs").first)
        assertEquals(3, cov.getValue("Legs").second)
    }

    @Test fun coverage_ignoresCoreCardioAndUnclassified() {
        val cov = MuscleVolumeFloor.coverage(
            listOf(ex("Plank", 1, 3), ex("Outdoor Run", 2, 1), ex("Ankle Circles", 3, 2))
        )
        // Only the five majors are floored; none of the above contributes to any of them.
        MuscleVolumeFloor.MAJOR_GROUPS.forEach { g ->
            assertEquals("no $g days from core/cardio/rehab", 0, cov.getValue(g).first.size)
        }
    }

    // ── Thresholds live in one place ────────────────────────────────────────────────────────────

    @Test fun feedbackQuotesTheConfiguredFloors() {
        val plan = listOf(ex("Barbell Squat", 2, 3)) // everything else at zero
        val reason = MuscleVolumeFloor.violation(plan, "Hypertrophy", 5)!!
        assertTrue(reason.contains("floor ${MuscleVolumeFloor.FLOOR_DAYS_PER_MUSCLE}"))
        assertTrue(reason.contains("floor ${MuscleVolumeFloor.FLOOR_SETS_PER_MUSCLE}"))
    }

    // ── Prompt guidance block ───────────────────────────────────────────────────────────────────

    @Test fun promptBlock_emptyWhenInactive_populatedWhenActive() {
        assertEquals("", MuscleVolumeFloor.promptBlock("Strength", 5))
        assertEquals("", MuscleVolumeFloor.promptBlock("Hypertrophy", 3))
        assertEquals("", MuscleVolumeFloor.promptBlock("Hypertrophy", 5, isDeload = true))
        val block = MuscleVolumeFloor.promptBlock("Hypertrophy", 5)
        assertTrue(block.contains("PER-MUSCLE WEEKLY FLOORS"))
        assertTrue(block.contains("${MuscleVolumeFloor.FLOOR_DAYS_PER_MUSCLE} DIFFERENT training days"))
        assertTrue(block.contains("${MuscleVolumeFloor.FLOOR_SETS_PER_MUSCLE} direct hard sets"))
        MuscleVolumeFloor.MAJOR_GROUPS.forEach { assertTrue("lists $it", block.contains(it)) }
    }
}
