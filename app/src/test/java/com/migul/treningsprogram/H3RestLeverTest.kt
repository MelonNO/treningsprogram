package com.migul.treningsprogram

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.dayDurationFeedback
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H3 — REST is the primary in-cap time lever for clearing the strict ≥40-min floor.
 *
 * The fix is prompt-side (steer the model to raise inter-set rest toward the goal's band max before
 * concluding a day is done, then reps, then a set, then an accessory). The model's behavior can't be
 * unit-tested without a live API call, but two structural guards CAN be:
 *  1. The estimator-contract math: a representative under-time hypertrophy day really does cross the
 *     40-min floor when its rests are raised toward 120 s — WITHOUT adding any volume. This proves the
 *     "rest is the #1 lever" reasoning is grounded in the authoritative [WorkoutTimeEstimator].
 *  2. The retry feedback now NAMES the rest lever for under-time days (while the reject CONDITION and the
 *     over-time branch are unchanged — see G1).
 *
 * Estimates use the authoritative formula:
 *   strength sec = sets*(maxReps*3) + (sets-1)*rest + 60 ;  day mins = (sum + 30) / 60
 */
class H3RestLeverTest {

    private val target = 50   // window 40–60
    private val floor = 40

    private fun ex(name: String, sets: Int, reps: String, rest: Int) =
        PlannedExercise(
            weekStart = 0L,
            dayOfWeek = 1,
            orderInDay = 0,
            exerciseName = name,
            sets = sets,
            targetReps = reps,
            targetWeightKg = 0f,
            recommendedRestSeconds = rest
        )

    // A realistic 6-exercise hypertrophy day with conventional "short rest for accessories/isolation"
    // (45–90 s). It lands UNDER the 40-min floor — exactly the repro class.
    private val underTimeDay = listOf(
        ex("Barbell Bench Press", 4, "6-8", 90),
        ex("Dumbbell Row", 4, "8-12", 75),
        ex("Dumbbell Lateral Raise", 3, "12-15", 60),
        ex("Triceps Pushdown", 3, "10-15", 60),
        ex("Dumbbell Curl", 3, "10-12", 60),
        ex("Leg Curl", 3, "12-15", 60)
    )

    // The SAME plan (same exercises, sets, reps) with inter-set rest raised toward the hypertrophy band
    // maximum (120 s). No volume added — only the rest lever.
    private val restRaisedDay = underTimeDay.map { it.copy(recommendedRestSeconds = 120) }

    @Test fun fixtureNamesAreNonCardio() {
        underTimeDay.forEach {
            assertNotEquals("${it.exerciseName} must estimate as strength, not cardio",
                "Cardio", MuscleClassifier.displayName(it.exerciseName))
        }
    }

    @Test fun underTimeDay_isBelowFloor() {
        val mins = WorkoutTimeEstimator.estimateDayMinutes(underTimeDay)
        assertEquals(35, mins)
        assertTrue("the day is under the 40-min floor → strict gate rejects it", mins < floor)
        // And the strict gate fires (direction-aware feedback returned).
        assertTrue(dayDurationFeedback(1, mins, target) != null)
    }

    @Test fun raisingRestTo120_clearsFloor_withoutAddingVolume() {
        val mins = WorkoutTimeEstimator.estimateDayMinutes(restRaisedDay)
        assertEquals(47, mins)
        // Rest ALONE moved the day from 35 → 47, inside the strict window — no extra sets/exercises.
        assertTrue("rest lever alone clears the floor", mins in floor..(target + 10))
        assertNull("the rest-raised day is now in-window → no rejection feedback",
            dayDurationFeedback(1, mins, target))
        // Same total working sets before and after — only rest changed.
        assertEquals(underTimeDay.sumOf { it.sets }, restRaisedDay.sumOf { it.sets })
    }

    @Test fun underTimeFeedback_namesRestLever_andStillSaysAdd() {
        val msg = dayDurationFeedback(day = 3, estimateMinutes = 35, targetMinutes = target)
        requireNotNull(msg)
        assertTrue("under-time feedback must name REST as a lever: $msg", msg.contains("REST", ignoreCase = true))
        // The G1 contract is preserved: it still instructs ADD / says UNDER and never issues TRIM.
        assertTrue("must still instruct ADD: $msg", msg.contains("ADD"))
        assertTrue("must still say UNDER: $msg", msg.contains("UNDER"))
        assertFalse("under-time feedback must not issue a TRIM instruction: $msg", msg.contains("TRIM"))
    }
}
