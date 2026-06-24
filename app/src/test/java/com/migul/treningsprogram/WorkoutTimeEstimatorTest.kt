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
 * numbers are hand-computed with the formula:
 *   strength sec = sets*(maxReps*3) + (sets-1)*rest + 60
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

        // Bench Press: 4 * (10*3) + (4-1)*120 + 60 = 120 + 360 + 60 = 540
        val bench = planned("Bench Press", sets = 4, targetReps = "8-10", rest = 120)
        // Barbell Squat: 5 * (5*3) + (5-1)*180 + 60 = 75 + 720 + 60 = 855
        val squat = planned("Barbell Squat", sets = 5, targetReps = "5", rest = 180)

        assertEquals(540, WorkoutTimeEstimator.estimateExerciseSeconds(bench))
        assertEquals(855, WorkoutTimeEstimator.estimateExerciseSeconds(squat))

        // Day: (540 + 855 + 30) / 60 = 1425 / 60 = 23
        val expectedMinutes = (540 + 855 + 30) / 60
        assertEquals(23, expectedMinutes)
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
