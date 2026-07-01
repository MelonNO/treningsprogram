package com.migul.treningsprogram

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.LONG_SESSION_THRESHOLD_MIN
import com.migul.treningsprogram.data.repository.dayDurationFeedback
import com.migul.treningsprogram.data.repository.isLongSession
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer-2 (long-session fix 2026-07) — LONG targets (~100 / ~120 min) must genuinely REACH their
 * duration by being built MULTI-MODAL (sound strength block + a DURATION-sized warm-up/conditioning
 * finisher the deterministic estimator can time), instead of failing the strict ±10-min gate on every
 * attempt (the "generates nothing" wound at the high end).
 *
 * The LLM behaviour (does the model actually build multi-modal?) is live-only, but the DETERMINISTIC
 * seams that make the fix sound ARE unit-testable and are what this guards:
 *  1. [isLongSession] / [LONG_SESSION_THRESHOLD_MIN] — the crisp gate that keeps the multi-modal steering
 *     OFF for short/mid sessions (the #1 regression guard: a 50-min plan must stay lean).
 *  2. [dayDurationFeedback] — a LONG under-time day is steered toward a duration-sized conditioning block,
 *     NOT "add a set/accessory"; a SHORT under-time day keeps the original wording (no regression); the
 *     reject CONDITION (the ±10 window) is byte-for-byte unchanged at every target.
 *  3. [WorkoutTimeEstimator] — a cardio-classified conditioning entry adds its DURATION (targetReps) + 60 s
 *     of clean minutes, so a real multi-modal long day lands in-window with ZERO junk lifting volume; and
 *     the modalities the prompt FORBIDS as the timed entry (rowing/carries) are proven to be mis-timed,
 *     which is exactly WHY they are excluded.
 *
 * Estimator formula (P2 2026-07, per-rep work = 4 s):
 *   strength sec = sets*(maxReps*4) + (sets-1)*rest + 60
 *   cardio   sec = duration_seconds + 60           (duration parsed from targetReps, e.g. "40 min")
 *   day mins     = (sum + 30) / 60
 */
class L2LongSessionStructureTest {

    private fun ex(name: String, sets: Int, reps: String, rest: Int, day: Int = 1, order: Int = 0) =
        PlannedExercise(
            weekStart = 0L,
            dayOfWeek = day,
            orderInDay = order,
            exerciseName = name,
            sets = sets,
            targetReps = reps,
            targetWeightKg = 0f,
            recommendedRestSeconds = rest
        )

    // ── 1. The long-session gate ────────────────────────────────────────────────────────────────────

    @Test fun longSessionThreshold_isExactlyAtNinety() {
        assertEquals(90, LONG_SESSION_THRESHOLD_MIN)
        assertTrue("120 is long", isLongSession(120))
        assertTrue("100 is long", isLongSession(100))
        assertTrue("90 (== threshold) is long", isLongSession(90))
        assertFalse("89 is not long", isLongSession(89))
        assertFalse("80 (mid) is not long", isLongSession(80))
        assertFalse("60 is not long", isLongSession(60))
        assertFalse("50 (the verified-lean case) is not long", isLongSession(50))
        assertFalse("20 is not long", isLongSession(20))
    }

    // ── 2. Direction-aware feedback — LONG under-fill steers MULTI-MODAL ────────────────────────────

    @Test fun longUnderTimeDay_steersMultiModalConditioning_notAddASet() {
        // 120-min target, a day the model built at ~70 min (the observed model ceiling).
        val msg = dayDurationFeedback(day = 2, estimateMinutes = 70, targetMinutes = 120)
        assertNotNull(msg); msg!!
        assertTrue("must say UNDER: $msg", msg.contains("UNDER"))
        assertTrue("must still instruct ADD: $msg", msg.contains("ADD"))
        assertFalse("under-time must NOT issue a TRIM instruction: $msg", msg.contains("TRIM"))
        assertTrue("must steer MULTI-MODAL: $msg", msg.contains("MULTI-MODAL"))
        assertTrue("must name a conditioning finisher: $msg", msg.contains("conditioning", ignoreCase = true))
        assertTrue("must size by DURATION: $msg", msg.contains("DURATION"))
        assertTrue("must name the day and estimate: $msg", msg.contains("Day 2") && msg.contains("70"))
        // Must exclude the estimator TRAPS (rowing/carries) as the timed entry.
        assertTrue("must forbid rowing as the timed entry: $msg", msg.contains("rowing", ignoreCase = true))
        // Must NOT tell a long day to grow strength volume the way a short day does.
        assertFalse("long under-fill must not tell it to add an accessory exercise: $msg",
            msg.contains("add one more accessory"))
    }

    @Test fun shortUnderTimeDay_keepsOriginalWording_noConditioningSteer() {
        // 50-min target (below threshold): the wording must be the original rest→reps→sets→accessory ladder,
        // with NO multi-modal / conditioning steering. This is the primary regression guard.
        val msg = dayDurationFeedback(day = 3, estimateMinutes = 33, targetMinutes = 50)
        assertNotNull(msg); msg!!
        assertTrue("must still ADD/UNDER: $msg", msg.contains("ADD") && msg.contains("UNDER"))
        assertTrue("short wording still names REST as the #1 lever: $msg", msg.contains("REST"))
        assertFalse("short session must NOT be steered multi-modal: $msg", msg.contains("MULTI-MODAL"))
        assertFalse("short session must NOT mention a conditioning finisher: $msg",
            msg.contains("conditioning", ignoreCase = true))
    }

    @Test fun longOverTimeDay_stillTrims_notMultiModal() {
        // Over-time branch is shared and unchanged regardless of target length.
        val msg = dayDurationFeedback(day = 4, estimateMinutes = 140, targetMinutes = 120)
        assertNotNull(msg); msg!!
        assertTrue("over-time must instruct TRIM: $msg", msg.contains("TRIM"))
        assertTrue("over-time must say OVER: $msg", msg.contains("OVER"))
        assertFalse("over-time must not instruct ADD: $msg", msg.contains("ADD"))
        assertFalse("over-time is not multi-modal steering: $msg", msg.contains("MULTI-MODAL"))
    }

    @Test fun rejectCondition_isByteForByteUnchanged_atLongTarget() {
        // The strict ±10 window is untouched at long targets — only the wording changed.
        val target = 120 // window 110–130
        assertNull("centre accepted", dayDurationFeedback(1, target, target))
        assertNull("floor (110) accepted", dayDurationFeedback(1, 110, target))
        assertNull("ceiling (130) accepted", dayDurationFeedback(1, 130, target))
        assertNotNull("109 (one under) rejected", dayDurationFeedback(1, 109, target))
        assertNotNull("131 (one over) rejected", dayDurationFeedback(1, 131, target))
        assertTrue("under → multi-modal ADD", dayDurationFeedback(1, 109, target)!!.contains("ADD"))
        assertTrue("over → TRIM", dayDurationFeedback(1, 131, target)!!.contains("TRIM"))
    }

    // ── 3. Estimator: a cardio conditioning entry adds clean DURATION minutes ───────────────────────

    @Test fun cardioConditioningEntry_isTimedByItsDuration() {
        // A 40-min stationary-bike finisher: 40*60 + 60 s setup = 2460 s (≈ 41 min). This is the clean
        // minutes a long session uses to reach its target without junk lifting volume.
        val bike = ex("Stationary Bike", sets = 1, reps = "40 min", rest = 60)
        assertEquals("Stationary Bike must classify as Cardio", "Cardio",
            MuscleClassifier.displayName("Stationary Bike"))
        assertEquals(2460, WorkoutTimeEstimator.estimateExerciseSeconds(bike))

        // An easy incline-walk warm-up is also cardio-timed.
        val walk = ex("Incline Walk", sets = 1, reps = "12 min", rest = 60)
        assertEquals("Cardio", MuscleClassifier.displayName("Incline Walk"))
        assertEquals(780, WorkoutTimeEstimator.estimateExerciseSeconds(walk))
    }

    @Test fun forbiddenModalities_areNotTimedByDuration_soTheyAreExcluded() {
        // WHY the prompt forbids rowing/carries as the timed entry: the estimator classifies them as
        // strength/core, so a "40 min" targetReps is read as reps (40) and grossly UNDER-timed — a 40-min
        // row would count as ~4 min, silently failing the time budget.
        assertEquals("Back", MuscleClassifier.displayName("Rowing Machine"))
        assertEquals("Core", MuscleClassifier.displayName("Farmer's Carry"))
        val row = ex("Rowing Machine", sets = 1, reps = "40 min", rest = 60)
        assertEquals(220, WorkoutTimeEstimator.estimateExerciseSeconds(row)) // 1*(40*4)+0+60 = 220 s ≈ 4 min
        assertTrue("a 40-min row is mis-timed as ~4 min (NOT ~41) — hence excluded",
            WorkoutTimeEstimator.estimateExerciseSeconds(row) <
                WorkoutTimeEstimator.estimateExerciseSeconds(ex("Stationary Bike", 1, "40 min", 60)) / 5)
    }

    @Test fun multiModal120MinDay_landsInWindow() {
        // A realistic 120-min day: a full 6-exercise strength block (~53 min) + a 12-min warm-up walk +
        // a 45-min conditioning bike ⇒ 112 min, inside [110, 130]. No junk lifting volume needed.
        val day = listOf(
            ex("Incline Walk", 1, "12 min", 60, order = 0),                 // 780 s  (warm-up)
            ex("Barbell Bench Press", 4, "6-8", 180, order = 1),            // 728 s
            ex("Barbell Row", 4, "8-12", 150, order = 2),                   // 702 s
            ex("Dumbbell Overhead Press", 3, "8-10", 150, order = 3),       // 480 s
            ex("Dumbbell Lateral Raise", 3, "12-15", 90, order = 4),        // 420 s
            ex("Triceps Pushdown", 3, "10-15", 90, order = 5),             // 420 s
            ex("Lying Leg Curl", 3, "12-15", 90, order = 6),               // 420 s
            ex("Stationary Bike", 1, "45 min", 60, order = 7)              // 2760 s (conditioning finisher)
        )
        val mins = WorkoutTimeEstimator.estimateDayMinutes(day)
        assertEquals(112, mins)
        assertTrue("multi-modal long day lands in the ±10 window [110,130]: $mins", mins in 110..130)
        // And the deterministic gate accepts it (no feedback returned).
        assertNull("gate accepts the in-window multi-modal day",
            dayDurationFeedback(1, mins, targetMinutes = 120))
    }

    @Test fun leanFiftyMinDay_staysLean_noCardio_inWindow() {
        // Regression guard: a lean 50-min hypertrophy day (6 strength exercises, NO cardio) still estimates
        // in [40,60] and is NOT a long session — nothing about this path changed.
        assertFalse(isLongSession(50))
        val day = listOf(
            ex("Barbell Bench Press", 4, "6-8", 120, order = 0),
            ex("Barbell Row", 4, "8-12", 120, order = 1),
            ex("Dumbbell Overhead Press", 3, "8-10", 90, order = 2),
            ex("Dumbbell Lateral Raise", 3, "12-15", 75, order = 3),
            ex("Triceps Pushdown", 3, "10-15", 75, order = 4),
            ex("Lying Leg Curl", 3, "12-15", 75, order = 5)
        )
        val mins = WorkoutTimeEstimator.estimateDayMinutes(day)
        assertTrue("lean 50-min day estimates in-window [40,60]: $mins", mins in 40..60)
        assertTrue("no cardio entry in a lean hypertrophy day",
            day.none { MuscleClassifier.displayName(it.exerciseName) == "Cardio" })
    }
}
