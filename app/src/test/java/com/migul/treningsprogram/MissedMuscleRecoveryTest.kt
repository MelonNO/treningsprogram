package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.MissedMuscleRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 02 (training-data improvements 2026-08-03): missed-day muscle recovery within the week.
 *
 * Grounding: the user's plan held ALL leg work on one day; that day was missed ⇒ an entire week with
 * zero leg sets, invisible unless Stats was studied. These tests pin the detection contract: a miss
 * whose muscles are still covered produces NO offer (no nagging); a miss that zeroes a muscle group
 * produces one offer shaped for the remaining week (move to a free day preferred, append capped
 * key exercises as fallback); logged days are never proposed as targets.
 */
class MissedMuscleRecoveryTest {

    private val weekStart = 123_000L

    private fun ex(name: String, day: Int, order: Int = 0) = PlannedExercise(
        weekStart = weekStart, dayOfWeek = day, orderInDay = order, exerciseName = name,
        sets = 3, targetReps = "8-10", targetWeightKg = 0f
    )

    /** Mon=chest, Tue=legs (the only leg day), Thu=back, Fri=arms; Wed/Sat/Sun free. */
    private fun weekPlan() = listOf(
        ex("Barbell Bench Press", 1), ex("Incline Dumbbell Press", 1, 1),
        ex("Barbell Squat", 2), ex("Leg Curl", 2, 1),
        ex("Barbell Row", 4), ex("Lat Pulldown", 4, 1),
        ex("Bicep Curl", 5), ex("Tricep Pushdown", 5, 1)
    )

    @Test fun missedOnlyLegDay_producesOffer_withMoveAndAppendTargets() {
        val offer = MissedMuscleRecovery.detect(
            weekStart, weekPlan(),
            missedDays = setOf(2), trainedDays = setOf(1), todayWeekday = 3
        )
        assertNotNull(offer); offer!!
        assertEquals(2, offer.missedDay)
        assertEquals(listOf("Legs"), offer.muscles)
        assertEquals("first free remaining day", 3, offer.moveTargetDay)
        assertEquals("last remaining planned day", 5, offer.appendTargetDay)
        assertEquals(listOf("Barbell Squat", "Leg Curl"), offer.appendRows.map { it.exerciseName })
        assertEquals("$weekStart:2", offer.dismissKey)
    }

    @Test fun missCoveredByALaterPlannedDay_noOffer() {
        // Legs also planned Friday ⇒ the muscle survives the miss ⇒ silence.
        val plan = weekPlan() + ex("Romanian Deadlift", 5, 2)
        assertNull(
            MissedMuscleRecovery.detect(
                weekStart, plan, missedDays = setOf(2), trainedDays = setOf(1), todayWeekday = 3
            )
        )
    }

    @Test fun missCoveredByAnAlreadyTrainedDay_noOffer() {
        // Monday trained legs (planned there and completed) ⇒ Tuesday's miss zeroes nothing.
        val plan = weekPlan() + ex("Leg Press", 1, 2)
        assertNull(
            MissedMuscleRecovery.detect(
                weekStart, plan, missedDays = setOf(2), trainedDays = setOf(1), todayWeekday = 3
            )
        )
    }

    @Test fun laterMissedDay_doesNotCountAsCoverage() {
        // Legs planned Tue AND Sat, but BOTH are missed ⇒ Sat can't cover Tue's loss.
        val plan = weekPlan() + ex("Romanian Deadlift", 6, 0)
        val offer = MissedMuscleRecovery.detect(
            weekStart, plan, missedDays = setOf(2, 6), trainedDays = setOf(1), todayWeekday = 7
        )
        assertNotNull(offer); offer!!
        assertEquals(listOf("Legs"), offer.muscles)
    }

    @Test fun noMissedDays_orEmptyPlan_noOffer() {
        assertNull(MissedMuscleRecovery.detect(weekStart, weekPlan(), emptySet(), setOf(1), 3))
        assertNull(MissedMuscleRecovery.detect(weekStart, emptyList(), setOf(2), emptySet(), 3))
    }

    @Test fun weekOver_noRemainingTarget_noOffer() {
        // Sunday, every other planned day trained or missed, no free day left after today.
        val plan = listOf(
            ex("Barbell Bench Press", 5), ex("Barbell Squat", 6),
            ex("Barbell Row", 7)
        )
        val offer = MissedMuscleRecovery.detect(
            weekStart, plan, missedDays = setOf(6), trainedDays = setOf(5, 7), todayWeekday = 7
        )
        assertNull(offer)
    }

    @Test fun trainedDaysAreNeverTargets() {
        // Only remaining slot is Sunday (7); Friday already trained must not be proposed.
        val plan = listOf(
            ex("Barbell Bench Press", 1), ex("Barbell Squat", 2), ex("Leg Curl", 2, 1),
            ex("Barbell Row", 5)
        )
        val offer = MissedMuscleRecovery.detect(
            weekStart, plan, missedDays = setOf(2), trainedDays = setOf(1, 5), todayWeekday = 6
        )
        assertNotNull(offer); offer!!
        assertEquals(6, offer.moveTargetDay)
        assertNull("no untrained planned day remains to append to", offer.appendTargetDay)
    }

    @Test fun appendRows_carryOnlyUncoveredMuscles_cappedAndInOrder() {
        // Missed day mixes legs + shoulders; shoulders survive via Friday ⇒ append only the leg work,
        // primary-first, capped at APPEND_CAP.
        val plan = listOf(
            ex("Barbell Squat", 2, 0), ex("Overhead Press", 2, 1), ex("Leg Press", 2, 2),
            ex("Leg Curl", 2, 3), ex("Calf Raise", 2, 4),
            ex("Lateral Raise", 5, 0), ex("Bicep Curl", 5, 1)
        )
        val offer = MissedMuscleRecovery.detect(
            weekStart, plan, missedDays = setOf(2), trainedDays = emptySet(), todayWeekday = 3
        )
        assertNotNull(offer); offer!!
        assertEquals(listOf("Legs"), offer.muscles)
        assertEquals(MissedMuscleRecovery.APPEND_CAP, offer.appendRows.size)
        assertEquals(
            listOf("Barbell Squat", "Leg Press", "Leg Curl"),
            offer.appendRows.map { it.exerciseName }
        )
        assertTrue(offer.appendRows.none { it.exerciseName == "Overhead Press" })
    }

    @Test fun handledMiss_isSkipped_soALaterUncoveredMissStillSurfaces() {
        // Tue (legs) was declined earlier; Thu (back) is also missed and uncovered — the offer must
        // move on to Thursday instead of re-prompting (or masking everything behind) Tuesday.
        val offer = MissedMuscleRecovery.detect(
            weekStart, weekPlan(), missedDays = setOf(2, 4), trainedDays = setOf(1),
            todayWeekday = 5, handledKeys = setOf("$weekStart:2")
        )
        assertNotNull(offer); offer!!
        assertEquals(4, offer.missedDay)
        assertEquals(listOf("Back"), offer.muscles)
        // Both handled ⇒ silence.
        assertNull(
            MissedMuscleRecovery.detect(
                weekStart, weekPlan(), missedDays = setOf(2, 4), trainedDays = setOf(1),
                todayWeekday = 5, handledKeys = setOf("$weekStart:2", "$weekStart:4")
            )
        )
    }

    @Test fun earliestUncoveredMiss_wins_whenSeveralMissed() {
        // Both Tue (legs) and Thu (back) missed and uncovered ⇒ the offer targets Tuesday first.
        val offer = MissedMuscleRecovery.detect(
            weekStart, weekPlan(), missedDays = setOf(2, 4), trainedDays = setOf(1), todayWeekday = 5
        )
        assertNotNull(offer); offer!!
        assertEquals(2, offer.missedDay)
    }

    @Test fun todayCountsAsRemaining_whenNotTrainedOrMissed() {
        // Today (Wed=3) is free ⇒ it is the move target; a today already trained must be skipped.
        val plan = listOf(ex("Barbell Squat", 2), ex("Barbell Bench Press", 5))
        val free = MissedMuscleRecovery.detect(
            weekStart, plan, missedDays = setOf(2), trainedDays = emptySet(), todayWeekday = 3
        )
        assertEquals(3, free?.moveTargetDay)
        val trainedToday = MissedMuscleRecovery.detect(
            weekStart, plan, missedDays = setOf(2), trainedDays = setOf(3), todayWeekday = 3
        )
        assertEquals(4, trainedToday?.moveTargetDay)
    }
}
