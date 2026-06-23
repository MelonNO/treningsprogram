package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.ui.log.LogWorkoutViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Item 1 (persistence): resuming a workout must land on the exercise the user was
 * actually working on, so already-logged sets — especially those on exercise 1 —
 * stay visible instead of being silently skipped (the old "first un-logged" bug).
 */
class ResumeIndexTest {

    private fun plan(vararg names: String): List<PlannedExercise> =
        names.mapIndexed { i, name ->
            PlannedExercise(
                id = (i + 1).toLong(), weekStart = 0L, dayOfWeek = 1, orderInDay = i,
                exerciseName = name, sets = 3, targetReps = "8-12", targetWeightKg = 0f
            )
        }

    private fun set(name: String, loggedAtMs: Long): WorkoutSet =
        WorkoutSet(
            sessionId = 1L, exerciseName = name, setNumber = 1,
            reps = 8, weightKg = 20f, loggedAtMs = loggedAtMs
        )

    @Test fun noLoggedSets_startsAtFirstExercise() {
        val p = plan("Squat", "Bench", "Row")
        assertEquals(0, LogWorkoutViewModel.resumeIndexFor(p, emptyList()))
    }

    @Test fun loggedOnExerciseOne_resumesOnExerciseOne_notSkipped() {
        // The core regression: sets exist only on exercise 1 → resume must stay on index 0.
        val p = plan("Squat", "Bench", "Row")
        val sets = listOf(set("Squat", 1000L), set("Squat", 2000L))
        assertEquals(0, LogWorkoutViewModel.resumeIndexFor(p, sets))
    }

    @Test fun loggedOnLaterExercise_resumesThere() {
        val p = plan("Squat", "Bench", "Row")
        val sets = listOf(
            set("Squat", 1000L), set("Squat", 2000L),
            set("Bench", 3000L)  // most recent
        )
        assertEquals(1, LogWorkoutViewModel.resumeIndexFor(p, sets))
    }

    @Test fun resumesOnMostRecentlyLogged_evenIfEarlierExerciseLoggedAfterReturning() {
        // User went forward to Bench, then went back and added another Squat set last.
        val p = plan("Squat", "Bench", "Row")
        val sets = listOf(
            set("Squat", 1000L),
            set("Bench", 2000L),
            set("Squat", 5000L)  // most recent is back on Squat
        )
        assertEquals(0, LogWorkoutViewModel.resumeIndexFor(p, sets))
    }

    @Test fun mostRecentExerciseNotInPlan_fallsBackToFirstUnlogged() {
        // e.g. a freestyle/added set whose name isn't in today's guided plan.
        val p = plan("Squat", "Bench", "Row")
        val sets = listOf(set("Squat", 1000L), set("Mystery Lift", 9000L))
        // Squat is logged; first plan exercise with no logged set is Bench (index 1).
        assertEquals(1, LogWorkoutViewModel.resumeIndexFor(p, sets))
    }

    @Test fun emptyPlan_returnsZero() {
        assertEquals(0, LogWorkoutViewModel.resumeIndexFor(emptyList(), listOf(set("Squat", 1L))))
    }
}
