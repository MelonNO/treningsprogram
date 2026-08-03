package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.neverPerformedViolation
import com.migul.treningsprogram.domain.NeverPerformedExercises
import com.migul.treningsprogram.domain.NeverPerformedExercises.PlannedRow
import com.migul.treningsprogram.domain.NeverPerformedExercises.PlannedWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 03 (training-data improvements 2026-08-03, reduced scope — exercise-level only).
 *
 * Grounding: the user's plan carried a shoulder-press slot for weeks with ZERO logged performances
 * while its planned siblings were logged consistently — the plan's only overhead-press pattern
 * existed on paper only. These tests pin the detection semantics (streak of counted weeks; a week
 * counts only when the exercise's planned day HAD activity but not this exercise; whole-day misses
 * are neutral; a performance breaks the streak), the prompt signal, and the deterministic
 * re-inclusion gate ([neverPerformedViolation]).
 */
class NeverPerformedExercisesTest {

    // Week Mondays as epoch-days: w0 oldest … w3 newest; "today" is the Monday after w3.
    private val w0 = 1000L
    private val w1 = 1007L
    private val w2 = 1014L
    private val w3 = 1021L
    private val today = 1028L

    /** A week planning [names] all on [day], plus a sibling "Barbell Bench Press" on the same day. */
    private fun week(start: Long, vararg names: String, day: Int = 3): PlannedWeek =
        PlannedWeek(start, names.map { PlannedRow(day, it) } + PlannedRow(day, "Barbell Bench Press"))

    /** Activity map: on each given epoch-day the user logged [performed] (normalized). */
    private fun activity(vararg entries: Pair<Long, Set<String>>): Map<Long, Set<String>> = entries.toMap()

    private val bench = setOf("barbell bench press")

    @Test fun threeCountedWeeks_zeroPerformances_detects() {
        val weeks = listOf(
            week(w1, "Overhead Press"), week(w2, "Overhead Press"), week(w3, "Overhead Press")
        )
        // Day 3 of each week (epoch = start+2) had activity — bench logged, OHP skipped.
        val act = activity(w1 + 2 to bench, w2 + 2 to bench, w3 + 2 to bench)
        val detected = NeverPerformedExercises.detect(weeks, act, today)
        assertEquals(listOf("Overhead Press"), detected)
    }

    @Test fun onePerformance_breaksTheStreak() {
        val weeks = listOf(
            week(w1, "Overhead Press"), week(w2, "Overhead Press"), week(w3, "Overhead Press")
        )
        // Performed once in the newest week (even on a DIFFERENT day than planned).
        val act = activity(
            w1 + 2 to bench, w2 + 2 to bench,
            w3 + 2 to bench, w3 + 5 to setOf("overhead press")
        )
        assertTrue(NeverPerformedExercises.detect(weeks, act, today).isEmpty())
    }

    @Test fun whollyMissedWeeks_areNeutral_notCounted_notBreaking() {
        // Four planned weeks; w1's planned day had NO activity at all (whole session missed).
        // Counted weeks: w0, w2, w3 = 3 ⇒ still detected (skipping a day is not an opinion
        // about one exercise).
        val weeks = listOf(
            week(w0, "Overhead Press"), week(w1, "Overhead Press"),
            week(w2, "Overhead Press"), week(w3, "Overhead Press")
        )
        val act = activity(w0 + 2 to bench, w2 + 2 to bench, w3 + 2 to bench)
        assertEquals(listOf("Overhead Press"), NeverPerformedExercises.detect(weeks, act, today))
    }

    @Test fun neutralWeeksAlone_doNotReachTheThreshold() {
        // Only two counted weeks (w3, w2) + one wholly-missed neutral (w1) ⇒ streak 2 < 3.
        val weeks = listOf(
            week(w1, "Overhead Press"), week(w2, "Overhead Press"), week(w3, "Overhead Press")
        )
        val act = activity(w2 + 2 to bench, w3 + 2 to bench)
        assertTrue(NeverPerformedExercises.detect(weeks, act, today).isEmpty())
    }

    @Test fun aWeekWithoutTheExercise_breaksConsecutiveness() {
        // Planned w3+w2 (counted), absent in w1, planned w0 — the chain stops at w1 ⇒ streak 2.
        val weeks = listOf(
            week(w0, "Overhead Press"), week(w1), // w1 plans only the bench sibling
            week(w2, "Overhead Press"), week(w3, "Overhead Press")
        )
        val act = activity(w0 + 2 to bench, w1 + 2 to bench, w2 + 2 to bench, w3 + 2 to bench)
        assertTrue(NeverPerformedExercises.detect(weeks, act, today).isEmpty())
    }

    @Test fun notInTheLatestPlan_isNotACandidate() {
        // Skipped for weeks historically but no longer planned ⇒ nothing actionable to surface.
        val weeks = listOf(
            week(w0, "Overhead Press"), week(w1, "Overhead Press"), week(w2, "Overhead Press"),
            week(w3) // latest week does not plan it
        )
        val act = activity(w0 + 2 to bench, w1 + 2 to bench, w2 + 2 to bench, w3 + 2 to bench)
        assertTrue(NeverPerformedExercises.detect(weeks, act, today).isEmpty())
    }

    @Test fun futurePlannedDays_dontCount() {
        // The newest week's planned day hasn't happened yet (today is mid-week w3): only w1+w2
        // count ⇒ streak 2 < 3 even though the day's epoch appears nowhere in the activity map.
        val weeks = listOf(
            week(w1, "Overhead Press"), week(w2, "Overhead Press"), week(w3, "Overhead Press")
        )
        val act = activity(w1 + 2 to bench, w2 + 2 to bench)
        assertTrue(NeverPerformedExercises.detect(weeks, act, w3 + 1).isEmpty())
    }

    @Test fun performedExercises_neverFlagged() {
        val weeks = listOf(week(w1, "Bicep Curl"), week(w2, "Bicep Curl"), week(w3, "Bicep Curl"))
        val act = activity(
            w1 + 2 to setOf("bicep curl", "barbell bench press"),
            w2 + 2 to setOf("bicep curl", "barbell bench press"),
            w3 + 2 to setOf("bicep curl", "barbell bench press")
        )
        assertTrue(NeverPerformedExercises.detect(weeks, act, today).isEmpty())
    }

    @Test fun matchingIsCaseAndWhitespaceInsensitive_displayKeepsPlanCasing() {
        val weeks = listOf(
            PlannedWeek(w1, listOf(PlannedRow(3, "overhead press "), PlannedRow(3, "Bench"))),
            PlannedWeek(w2, listOf(PlannedRow(3, "OVERHEAD PRESS"), PlannedRow(3, "Bench"))),
            PlannedWeek(w3, listOf(PlannedRow(3, "Overhead Press"), PlannedRow(3, "Bench")))
        )
        val act = activity(w1 + 2 to setOf("bench"), w2 + 2 to setOf("bench"), w3 + 2 to setOf("bench"))
        assertEquals(listOf("Overhead Press"), NeverPerformedExercises.detect(weeks, act, today))
    }

    @Test fun adjustableThreshold_isRespected() {
        val weeks = listOf(week(w2, "Overhead Press"), week(w3, "Overhead Press"))
        val act = activity(w2 + 2 to bench, w3 + 2 to bench)
        assertTrue(NeverPerformedExercises.detect(weeks, act, today, minWeeks = 3).isEmpty())
        assertEquals(
            listOf("Overhead Press"),
            NeverPerformedExercises.detect(weeks, act, today, minWeeks = 2)
        )
    }

    // ── Prompt signal ───────────────────────────────────────────────────────────────────────────

    @Test fun promptBlock_emptyWhenNothingFlagged() {
        assertEquals("", NeverPerformedExercises.promptBlock(emptyList()))
    }

    @Test fun promptBlock_forbidsAndAsksForCoveragePreservingReplacement() {
        val block = NeverPerformedExercises.promptBlock(listOf("Overhead Press"))
        assertTrue(block.contains("Overhead Press"))
        assertTrue(block.contains("Do NOT prescribe"))
        assertTrue("replacement must preserve coverage", block.contains("REPLACE"))
        assertTrue(block.contains("never simply delete"))
    }

    // ── Deterministic re-inclusion gate (accept path) ───────────────────────────────────────────

    private fun ex(name: String, day: Int = 1) = PlannedExercise(
        weekStart = 0L, dayOfWeek = day, orderInDay = 0, exerciseName = name,
        sets = 3, targetReps = "8-10", targetWeightKg = 0f
    )

    @Test fun gate_rejectsReincludedName_caseInsensitive() {
        val reason = neverPerformedViolation(
            listOf(ex("Barbell Bench Press"), ex("OVERHEAD PRESS", day = 3)),
            forbiddenNormalized = setOf("overhead press")
        )
        assertNotNull(reason); reason!!
        assertTrue(reason.contains("OVERHEAD PRESS"))
        assertTrue("asks for a replacement, not deletion", reason.contains("Replace"))
    }

    @Test fun gate_passesCleanPlans_andRenamedVariants() {
        // A renamed variant is exactly the desired fix — the gate must not block it.
        assertNull(
            neverPerformedViolation(
                listOf(ex("Machine Shoulder Press"), ex("Landmine Press", day = 3)),
                forbiddenNormalized = setOf("overhead press")
            )
        )
        assertNull(neverPerformedViolation(listOf(ex("Overhead Press")), emptySet()))
    }

    @Test fun gate_exemptsLockedDays() {
        // A logged-history echo may legitimately contain the flagged name.
        assertNull(
            neverPerformedViolation(
                listOf(ex("Overhead Press", day = 2)),
                forbiddenNormalized = setOf("overhead press"),
                lockedDays = setOf(2)
            )
        )
    }
}
