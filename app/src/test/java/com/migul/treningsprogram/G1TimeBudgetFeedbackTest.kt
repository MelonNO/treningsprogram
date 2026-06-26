package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.dayDurationFeedback
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G1 — strict per-day time-budget gate kept; the prompt-side fix is the DIRECTION-AWARE retry
 * feedback ([dayDurationFeedback]) plus the canonical failing fixture's day estimates.
 *
 * Two seams are guarded:
 *  1. [dayDurationFeedback] is a pure function whose reject CONDITION is byte-for-byte the old gate
 *     (`est < target-10 || est > target+10`) but whose WORDING is now directional: a day under the
 *     floor is told to ADD work; a day over the ceiling is told to TRIM. The old feedback said
 *     "Trim…" for both, which drove under-time days even shorter so retries never converged.
 *  2. The canonical repro (`docs/intake/generation-timebudget-fix-2026-06/repro-failing-generation.md`)
 *     — a complete, rule-compliant 5-day plan the app discarded — really does land 3 of its 5 days
 *     UNDER the 40-min floor for a 50-min target, so the strict gate rejects the whole plan and the
 *     fixed feedback steers those days the correct direction (ADD).
 *
 * Estimates are hand-computed with the authoritative formula in [WorkoutTimeEstimator]:
 *   strength sec = sets*(maxReps*3) + (sets-1)*rest + 60
 *   cardio   sec = duration + 60      ("20-25" with no min/km unit → 30-min fallback = 1800)
 *   day mins     = (sum + 30) / 60
 */
class G1TimeBudgetFeedbackTest {

    private val target = 50  // repro's session target; window is 40–60.

    private fun ex(name: String, sets: Int, reps: String, rest: Int, day: Int = 1) =
        PlannedExercise(
            weekStart = 0L,
            dayOfWeek = day,
            orderInDay = 0,
            exerciseName = name,
            sets = sets,
            targetReps = reps,
            targetWeightKg = 0f,
            recommendedRestSeconds = rest
        )

    // ── 1. Direction-aware feedback ──────────────────────────────────────────────────────────────

    @Test fun underTimeDay_tellsModelToAdd_notTrim() {
        val msg = dayDurationFeedback(day = 3, estimateMinutes = 33, targetMinutes = target)
        requireNotNull(msg)
        assertTrue("under-time feedback must instruct ADD: $msg", msg.contains("ADD"))
        assertTrue("under-time feedback must say it is UNDER: $msg", msg.contains("UNDER"))
        // The old wording was the bug: an under-time day must NOT be told to TRIM (the imperative).
        assertFalse("under-time feedback must not issue a TRIM instruction: $msg", msg.contains("TRIM"))
        assertTrue("feedback should name the day and estimate", msg.contains("Day 3") && msg.contains("33"))
    }

    @Test fun overTimeDay_tellsModelToTrim_notAdd() {
        val msg = dayDurationFeedback(day = 4, estimateMinutes = 70, targetMinutes = target)
        requireNotNull(msg)
        assertTrue("over-time feedback must instruct TRIM: $msg", msg.contains("TRIM"))
        assertTrue("over-time feedback must say it is OVER: $msg", msg.contains("OVER"))
        assertFalse("over-time feedback must not issue an ADD instruction: $msg", msg.contains("ADD"))
    }

    @Test fun inWindowDay_givesNoFeedback() {
        assertNull("centre of window accepted", dayDurationFeedback(1, target, target))
        assertNull("floor is in-window", dayDurationFeedback(1, target - 10, target))   // 40
        assertNull("ceiling is in-window", dayDurationFeedback(1, target + 10, target)) // 60
    }

    /**
     * The reject CONDITION must be byte-for-byte the pre-fix gate: out-of-window ⇔ non-null, and the
     * boundary is inclusive (40 and 60 accepted; 39 and 61 rejected). This proves the strict gate's
     * width is unchanged and only the wording differs.
     */
    @Test fun gateBoundaryIsPreservedExactly() {
        assertNull(dayDurationFeedback(1, 40, target))                 // == floor → accept
        assertNull(dayDurationFeedback(1, 60, target))                 // == ceiling → accept
        requireNotNull(dayDurationFeedback(1, 39, target))             // one under → reject (ADD)
        requireNotNull(dayDurationFeedback(1, 61, target))             // one over → reject (TRIM)
        assertTrue(dayDurationFeedback(1, 39, target)!!.contains("ADD"))
        assertTrue(dayDurationFeedback(1, 61, target)!!.contains("TRIM"))
    }

    // ── 2. Canonical failing fixture ─────────────────────────────────────────────────────────────

    private val tuesday = listOf(
        ex("Barbell Bent-Over Row (Underhand Grip)", 4, "6-8", 120, 2),
        ex("Chest-Supported Dumbbell Row (Incline Bench)", 4, "8-12", 90, 2),
        ex("Dumbbell Hammer Curl", 4, "10-12", 60, 2),
        ex("Dumbbell Spider Curl (Chest on Incline Bench)", 3, "10-13", 60, 2),
        ex("Standing Dumbbell Calf Raise (Bilateral, Flat Floor)", 3, "15-20", 60, 2),
        ex("Hand-Supported Single-Leg Balance Hold (Wall Touch)", 3, "30-40 s", 45, 2)
    )
    private val wednesday = listOf(
        ex("Barbell Floor Press", 4, "6-8", 120, 3),
        ex("Dumbbell Overhead Press (Seated, Pronated Grip)", 3, "8-10", 90, 3),
        ex("Dumbbell Lateral Raise", 4, "12-15", 60, 3),
        ex("Dumbbell Skull Crusher (Flat Bench)", 4, "10-12", 60, 3),
        ex("Dumbbell Bent-Over Rear Delt Fly", 3, "12-15", 60, 3)
    )
    private val friday = listOf(
        ex("Barbell Deadlift", 4, "5-8", 120, 5),
        ex("Dumbbell Goblet Squat", 4, "8-10", 90, 5),
        ex("Barbell Hip Thrust (Floor/Bench-Supported)", 4, "12-15", 90, 5),
        ex("Dumbbell Romanian Deadlift", 3, "10-12", 90, 5),
        ex("Tibialis Raise (Seated, Heels on Floor, Toes Lift)", 3, "15-20", 45, 5)
    )
    private val saturday = listOf(
        ex("Barbell Curl", 4, "8-10", 90, 6),
        ex("Dumbbell Overhead Tricep Extension (Seated, Two-Hand)", 4, "10-12", 90, 6),
        ex("Dumbbell Concentration Curl", 3, "10-13", 60, 6),
        ex("Diamond Push-Up (Close-Grip, Bodyweight)", 3, "10-15", 60, 6),
        ex("Dumbbell Reverse Fly (Lying Face-Down on Flat Bench)", 3, "12-15", 60, 6)
    )
    private val sunday = listOf(
        ex("Dumbbell Flat Bench Press (Pronated Grip)", 4, "8-10", 120, 7),
        ex("Dumbbell Chest-Supported Rear Delt Row (Wide Elbows)", 4, "8-12", 90, 7),
        ex("Ab Roller Rollout (Kneeling)", 3, "8-12", 60, 7),
        ex("Dumbbell Incline Lateral Raise", 3, "12-15", 60, 7),
        // NOTE: "Slow Tempo" makes this classify as Cardio (MuscleClassifier keys "tempo"), so it is
        // estimated as a 30-min cardio fallback → Sunday ≈ 58 min (in-window). This is why the
        // relayed lead's "Sun ~33 / 4-of-5-under" differs from reality: the true fixture is 3-of-5.
        ex("Standing Bilateral Calf Raise (Bodyweight, Slow Tempo)", 3, "20-25", 45, 7)
    )

    @Test fun fixtureDayEstimates_matchAuthoritativeFormula() {
        assertEquals(42, WorkoutTimeEstimator.estimateDayMinutes(tuesday))   // in-window
        assertEquals(33, WorkoutTimeEstimator.estimateDayMinutes(wednesday)) // UNDER floor
        assertEquals(36, WorkoutTimeEstimator.estimateDayMinutes(friday))    // UNDER floor
        assertEquals(31, WorkoutTimeEstimator.estimateDayMinutes(saturday))  // UNDER floor
        assertEquals(58, WorkoutTimeEstimator.estimateDayMinutes(sunday))    // in-window (Cardio quirk)
    }

    /**
     * The fixture really is rejected by the strict gate: 3 of its 5 days fall under the 40-min floor,
     * and the fixed feedback steers each of those the correct direction (ADD), while the two in-window
     * days produce no feedback. This is the regression the fix targets.
     */
    @Test fun fixturePlan_isRejected_andUnderDaysGetAddGuidance() {
        val days = mapOf(
            2 to tuesday, 3 to wednesday, 5 to friday, 6 to saturday, 7 to sunday
        )
        val feedback = days
            .toSortedMap()
            .mapValues { (day, exs) ->
                dayDurationFeedback(day, WorkoutTimeEstimator.estimateDayMinutes(exs), target)
            }

        val rejectedDays = feedback.filterValues { it != null }
        assertEquals("exactly the 3 under-floor days are rejected", setOf(3, 5, 6), rejectedDays.keys)
        rejectedDays.forEach { (day, msg) ->
            assertTrue("Day $day under-floor feedback must say ADD: $msg", msg!!.contains("ADD"))
        }
        // In-window days produce no rejection feedback.
        assertNull(feedback[2])
        assertNull(feedback[7])

        // Whole-plan outcome: any out-of-window day makes the deterministic durationReason non-empty,
        // which is exactly what the generation loop rejects on (and short-circuits the LLM review).
        val durationReason = feedback.values.filterNotNull().joinToString(" ")
        assertTrue("plan must be rejected on the time budget", durationReason.isNotBlank())
    }
}
