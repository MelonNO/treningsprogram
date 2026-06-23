package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.ui.log.LogWorkoutViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Item 6 (quick-access "Add exercise"): a new exercise must be inserted immediately
 * after the current one and the whole list renumbered so the X/Y counter stays correct.
 */
class InsertExerciseTest {

    private fun plan(vararg names: String): List<PlannedExercise> =
        names.mapIndexed { i, name ->
            PlannedExercise(
                id = (i + 1).toLong(), weekStart = 0L, dayOfWeek = 1, orderInDay = i,
                exerciseName = name, sets = 3, targetReps = "8-12", targetWeightKg = 0f
            )
        }

    private fun added(name: String) = PlannedExercise(
        id = 0L, weekStart = 0L, dayOfWeek = 1, orderInDay = 0,
        exerciseName = name, sets = 0, targetReps = "", targetWeightKg = 0f
    )

    @Test fun insertsImmediatelyAfterCurrent() {
        // On exercise 2 (index 1): new becomes exercise 3; old 3 -> 4.
        val p = plan("Squat", "Bench", "Row")
        val result = LogWorkoutViewModel.insertAfter(p, currentIndex = 1, added = added("Curl"))
        assertEquals(listOf("Squat", "Bench", "Curl", "Row"), result.map { it.exerciseName })
    }

    @Test fun renumbersOrderInDayContiguously() {
        val p = plan("Squat", "Bench", "Row")
        val result = LogWorkoutViewModel.insertAfter(p, currentIndex = 1, added = added("Curl"))
        assertEquals(listOf(0, 1, 2, 3), result.map { it.orderInDay })
        // X/Y count: now 4 exercises.
        assertEquals(4, result.size)
    }

    @Test fun insertAfterLast_appendsAtEnd() {
        val p = plan("Squat", "Bench")
        val result = LogWorkoutViewModel.insertAfter(p, currentIndex = 1, added = added("Row"))
        assertEquals(listOf("Squat", "Bench", "Row"), result.map { it.exerciseName })
        assertEquals(listOf(0, 1, 2), result.map { it.orderInDay })
    }

    @Test fun insertAfterFirst_putsItSecond() {
        val p = plan("Squat", "Bench", "Row")
        val result = LogWorkoutViewModel.insertAfter(p, currentIndex = 0, added = added("Lunge"))
        assertEquals(listOf("Squat", "Lunge", "Bench", "Row"), result.map { it.exerciseName })
    }

    @Test fun addedExercise_hasNoAiTarget() {
        val p = plan("Squat")
        val result = LogWorkoutViewModel.insertAfter(p, currentIndex = 0, added = added("Custom Lift"))
        val custom = result.first { it.exerciseName == "Custom Lift" }
        assertEquals(0, custom.sets)
        assertEquals("", custom.targetReps)
        assertEquals(0f, custom.targetWeightKg, 0f)
    }
}
