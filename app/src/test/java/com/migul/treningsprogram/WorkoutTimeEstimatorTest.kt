package com.migul.treningsprogram

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shared time-estimate formula that drives both the Program screen's "~Xm"
 * labels and the deterministic ±10 min duration enforcement in AiRepository.
 *
 * 2026-08-03 "duration truth" correction (calibrated against the user's real training history —
 * see the [WorkoutTimeEstimator] KDoc for what is measured vs reasoned). Expected numbers are
 * hand-computed with the corrected formula:
 *   strength sec = sets*(maxReps*4 + 35) + sets*rest + 90  [+120 ramp if weight>0 and rest>=120]
 *   cardio   sec = duration + 90        (duration: "30 min" → 1800, "5 km" → 1500)
 *   day mins     = (sum of exercise seconds + 30) / 60   (round to nearest)
 */
class WorkoutTimeEstimatorTest {

    private fun planned(
        name: String,
        sets: Int,
        targetReps: String,
        rest: Int = 90,
        weightKg: Float = 0f
    ) = PlannedExercise(
        weekStart = 0L,
        dayOfWeek = 1,
        orderInDay = 0,
        exerciseName = name,
        sets = sets,
        targetReps = targetReps,
        targetWeightKg = weightKg,
        recommendedRestSeconds = rest
    )

    @Test fun strengthDay_estimateMatchesHandComputation() {
        // Names must classify as NON-cardio so the strength branch runs.
        assertNotEquals("Cardio", MuscleClassifier.displayName("Bench Press"))
        assertNotEquals("Cardio", MuscleClassifier.displayName("Barbell Squat"))

        // Bodyweight fixtures (weight 0) → no ramp allowance.
        // Bench: 4*(10*4 + 35) + 4*120 + 90 = 300 + 480 + 90 = 870
        val bench = planned("Bench Press", sets = 4, targetReps = "8-10", rest = 120)
        // Squat: 5*(5*4 + 35) + 5*180 + 90 = 275 + 900 + 90 = 1265
        val squat = planned("Barbell Squat", sets = 5, targetReps = "5", rest = 180)

        assertEquals(870, WorkoutTimeEstimator.estimateExerciseSeconds(bench))
        assertEquals(1265, WorkoutTimeEstimator.estimateExerciseSeconds(squat))

        // Day: (870 + 1265 + 30) / 60 = 2165 / 60 = 36
        val expectedMinutes = (870 + 1265 + 30) / 60
        assertEquals(36, expectedMinutes)
        assertEquals(expectedMinutes, WorkoutTimeEstimator.estimateDayMinutes(listOf(bench, squat)))
    }

    // ── 2026-08-03: warm-up ramp allowance for loaded, long-rest lifts ──────────────────

    @Test fun loadedLongRestLift_getsRampAllowance() {
        // Real ramp sets are logged in sessions but never planned — the allowance models them.
        // Loaded squat @180s rest: 4*(8*4 + 35) + 4*180 + 90 + 120(ramp) = 268 + 720 + 210 = 1198
        val heavySquat = planned("Barbell Squat", sets = 4, targetReps = "6-8", rest = 180, weightKg = 100f)
        assertEquals(4 * (8 * 4 + 35) + 4 * 180 + 90 + 120,
            WorkoutTimeEstimator.estimateExerciseSeconds(heavySquat))

        // Same lift with weight 0 (bodyweight / P2-sanitized) → NO ramp.
        val bwSquat = planned("Barbell Squat", sets = 4, targetReps = "6-8", rest = 180, weightKg = 0f)
        assertEquals(4 * (8 * 4 + 35) + 4 * 180 + 90,
            WorkoutTimeEstimator.estimateExerciseSeconds(bwSquat))

        // Loaded but SHORT-rest (accessory-style, below the 120 s threshold) → NO ramp.
        val curls = planned("Barbell Curl", sets = 3, targetReps = "12", rest = 60, weightKg = 30f)
        assertEquals(3 * (12 * 4 + 35) + 3 * 60 + 90,
            WorkoutTimeEstimator.estimateExerciseSeconds(curls))
    }

    /**
     * The calibration contract, pinned: for the measured reference day shape (the user's real
     * hypertrophy-style sessions: ~4 exercises × ~3 sets, 90–180 s rests, loaded compounds) the
     * corrected formula must land ~1.5–1.7× the OLD formula's minutes — that ratio IS the measured
     * optimism it corrects (planned-est vs real duration: median 1.62, corrected to 1.02).
     */
    @Test fun correction_reproducesTheMeasuredOptimismFactor() {
        val day = listOf(
            planned("Barbell Squat", sets = 4, targetReps = "6-8", rest = 180, weightKg = 90f),
            planned("Romanian Deadlift", sets = 3, targetReps = "8", rest = 150, weightKg = 80f),
            planned("Leg Press", sets = 3, targetReps = "12-15", rest = 120, weightKg = 160f),
            planned("Standing Calf Raise", sets = 3, targetReps = "15", rest = 60, weightKg = 60f)
        )
        val old = day.sumOf { it.sets * (lastReps(it) * 4) + (it.sets - 1) * it.recommendedRestSeconds + 60 }
        val corrected = day.sumOf { WorkoutTimeEstimator.estimateExerciseSeconds(it) }
        val ratio = corrected.toDouble() / old
        assertTrue("corrected/old = $ratio must sit in the measured 1.4–1.8 band", ratio in 1.4..1.8)
    }

    private fun lastReps(ex: PlannedExercise): Int =
        Regex("\\d+").findAll(ex.targetReps).last().value.toInt()

    @Test fun cardioEntry_durationPlusTransition() {
        assertEquals("Cardio", MuscleClassifier.displayName("Easy Jog"))

        // "30 min" → 30*60 + 90 = 1890
        val jog = planned("Easy Jog", sets = 1, targetReps = "30 min", rest = 60)
        assertEquals(1890, WorkoutTimeEstimator.estimateExerciseSeconds(jog))
        // Day with just it: (1890 + 30) / 60 = 32
        assertEquals(32, WorkoutTimeEstimator.estimateDayMinutes(listOf(jog)))

        // "5 km" → 5*5*60 + 90 = 1500 + 90 = 1590
        val run5k = planned("Easy Jog", sets = 1, targetReps = "5 km", rest = 60)
        assertEquals(1590, WorkoutTimeEstimator.estimateExerciseSeconds(run5k))
    }

    // ── 2026-08 fix: rep-style cardio must NOT hit the 1800 s fallback ─────────────────

    @Test fun repStyleCardio_usesStrengthFormula_notThirtyMinuteFallback() {
        // A Cardio-classified name carrying a pure rep count was falling into
        // parseCardioSeconds' 1800 s fallback → counted as ~31 minutes (live-demonstrated).
        assertEquals("Cardio", MuscleClassifier.displayName("Mountain Climber"))

        // Mountain Climber: 3*(20*4 + 35) + 3*30 + 90 = 345 + 90 + 90 = 525
        val climbers = planned("Mountain Climber", sets = 3, targetReps = "20", rest = 30)
        assertEquals(525, WorkoutTimeEstimator.estimateExerciseSeconds(climbers))
        assertNotEquals(1800 + 90, WorkoutTimeEstimator.estimateExerciseSeconds(climbers))
    }

    @Test fun repRangeCardio_usesStrengthFormula() {
        // A rep RANGE on a cardio-classified name is also sets×reps work.
        assertEquals("Cardio", MuscleClassifier.displayName("Burpees"))
        // Burpees: 3*(20*4 + 35) + 3*45 + 90 = 345 + 135 + 90 = 570
        val burpees = planned("Burpees", sets = 3, targetReps = "15-20", rest = 45)
        assertEquals(570, WorkoutTimeEstimator.estimateExerciseSeconds(burpees))
    }

    @Test fun unparseableIntervalString_keepsThirtyMinuteFallback() {
        // "6×400m" is neither "min"/"km" nor a pure rep scheme → genuine 1800 s fallback.
        assertEquals("Cardio", MuscleClassifier.displayName("Interval Run"))
        val intervals = planned("Interval Run", sets = 1, targetReps = "6×400m", rest = 60)
        assertEquals(1800 + 90, WorkoutTimeEstimator.estimateExerciseSeconds(intervals))
    }
}
