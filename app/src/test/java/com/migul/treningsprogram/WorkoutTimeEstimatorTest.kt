package com.migul.treningsprogram

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Guards the shared time-estimate formula that drives both the Program screen's "~Xm"
 * labels and the deterministic ±10 min duration enforcement in AiRepository. Expected
 * numbers are hand-computed with the formula (P2 2026-07: per-rep work is 4 s, not 3 s):
 *   strength sec = sets*(maxReps*4) + (sets-1)*rest + 60
 *   cardio   sec = duration + 60        (duration: "30 min" → 1800, "5 km" → 1500)
 *   day mins     = (sum of exercise seconds + 30) / 60   (round to nearest)
 */
class WorkoutTimeEstimatorTest {

    private fun planned(
        name: String,
        sets: Int,
        targetReps: String,
        rest: Int = 90
    ) = PlannedExercise(
        weekStart = 0L,
        dayOfWeek = 1,
        orderInDay = 0,
        exerciseName = name,
        sets = sets,
        targetReps = targetReps,
        targetWeightKg = 0f,
        recommendedRestSeconds = rest
    )

    @Test fun strengthDay_estimateMatchesHandComputation() {
        // Names must classify as NON-cardio so the strength branch runs.
        assertNotEquals("Cardio", MuscleClassifier.displayName("Bench Press"))
        assertNotEquals("Cardio", MuscleClassifier.displayName("Barbell Squat"))

        // Bench Press: 4 * (10*4) + (4-1)*120 + 60 = 160 + 360 + 60 = 580
        val bench = planned("Bench Press", sets = 4, targetReps = "8-10", rest = 120)
        // Barbell Squat: 5 * (5*4) + (5-1)*180 + 60 = 100 + 720 + 60 = 880
        val squat = planned("Barbell Squat", sets = 5, targetReps = "5", rest = 180)

        assertEquals(580, WorkoutTimeEstimator.estimateExerciseSeconds(bench))
        assertEquals(880, WorkoutTimeEstimator.estimateExerciseSeconds(squat))

        // Day: (580 + 880 + 30) / 60 = 1490 / 60 = 24
        val expectedMinutes = (580 + 880 + 30) / 60
        assertEquals(24, expectedMinutes)
        assertEquals(expectedMinutes, WorkoutTimeEstimator.estimateDayMinutes(listOf(bench, squat)))
    }

    @Test fun cardioEntry_durationPlusSetup() {
        assertEquals("Cardio", MuscleClassifier.displayName("Easy Jog"))

        // "30 min" → 30*60 + 60 = 1860
        val jog = planned("Easy Jog", sets = 1, targetReps = "30 min", rest = 60)
        assertEquals(1860, WorkoutTimeEstimator.estimateExerciseSeconds(jog))
        // Day with just it: (1860 + 30) / 60 = 31
        assertEquals(31, WorkoutTimeEstimator.estimateDayMinutes(listOf(jog)))

        // "5 km" → 5*5*60 + 60 = 1500 + 60 = 1560
        val run5k = planned("Easy Jog", sets = 1, targetReps = "5 km", rest = 60)
        assertEquals(1560, WorkoutTimeEstimator.estimateExerciseSeconds(run5k))
    }
}
