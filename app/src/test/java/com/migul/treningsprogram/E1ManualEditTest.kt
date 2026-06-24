package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.DayPlanEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature E1: manual program editing.
 *
 * Locks the PURE list re-keying behind add / delete / reorder in [DayPlanEditor]. The persistence
 * contract is that `WorkoutRepository.saveDayPlan` re-keys orderInDay by the ROW VALUES handed to
 * it, so every list we pass must already carry orderInDay = index. These tests assert exactly that
 * invariant (and the field-edit copy) without any Android/Room dependency or DB mock.
 */
class E1ManualEditTest {

    private fun ex(id: Long, name: String, order: Int, day: Int = 1) = PlannedExercise(
        id = id,
        weekStart = 0L,
        dayOfWeek = day,
        orderInDay = order,
        exerciseName = name,
        sets = 3,
        targetReps = "8-12",
        targetWeightKg = 50f
    )

    private fun day() = listOf(
        ex(1, "Squat", 0),
        ex(2, "Bench", 1),
        ex(3, "Row", 2)
    )

    /** orderInDay must equal the list index on every row after any transform. */
    private fun assertContiguousOrder(list: List<PlannedExercise>) {
        list.forEachIndexed { index, e ->
            assertEquals("orderInDay must equal index for ${e.exerciseName}", index, e.orderInDay)
        }
    }

    // ---- reindex ----

    @Test fun reindex_assignsContiguousZeroBasedOrder() {
        val messy = listOf(ex(1, "A", 5), ex(2, "B", 9), ex(3, "C", 1))
        val out = DayPlanEditor.reindex(messy)
        assertContiguousOrder(out)
        assertEquals(listOf("A", "B", "C"), out.map { it.exerciseName })
    }

    @Test fun reindex_keepsAlreadyCorrectRowsUntouched() {
        val good = day()
        val out = DayPlanEditor.reindex(good)
        // Same identities, already-correct rows are returned as-is.
        assertTrue(out[0] === good[0])
        assertContiguousOrder(out)
    }

    // ---- delete ----

    @Test fun remove_dropsRowAndReindexesRemainder() {
        val out = DayPlanEditor.remove(day(), day()[1]) // remove "Bench"
        assertEquals(listOf("Squat", "Row"), out.map { it.exerciseName })
        assertContiguousOrder(out)
    }

    @Test fun remove_unknownRowIsNoOpButStillReindexed() {
        val out = DayPlanEditor.remove(day(), ex(99, "Ghost", 0))
        assertEquals(listOf("Squat", "Bench", "Row"), out.map { it.exerciseName })
        assertContiguousOrder(out)
    }

    // ---- add ----

    @Test fun add_appendsAtEndWithCorrectOrder() {
        val newRow = ex(4, "Curl", 0) // wrong incoming order on purpose
        val out = DayPlanEditor.add(day(), newRow)
        assertEquals(listOf("Squat", "Bench", "Row", "Curl"), out.map { it.exerciseName })
        assertContiguousOrder(out)
        assertEquals(3, out.last().orderInDay)
    }

    @Test fun add_toEmptyDayGivesOrderZero() {
        val out = DayPlanEditor.add(emptyList(), ex(1, "Plank", 7))
        assertEquals(1, out.size)
        assertEquals(0, out[0].orderInDay)
    }

    // ---- move ----

    @Test fun move_up_swapsWithPreviousAndReindexes() {
        val out = DayPlanEditor.move(day(), day()[1], up = true) // move "Bench" up
        assertEquals(listOf("Bench", "Squat", "Row"), out.map { it.exerciseName })
        assertContiguousOrder(out)
    }

    @Test fun move_down_swapsWithNextAndReindexes() {
        val out = DayPlanEditor.move(day(), day()[1], up = false) // move "Bench" down
        assertEquals(listOf("Squat", "Row", "Bench"), out.map { it.exerciseName })
        assertContiguousOrder(out)
    }

    @Test fun move_up_atTopIsNoOp() {
        val out = DayPlanEditor.move(day(), day()[0], up = true)
        assertEquals(listOf("Squat", "Bench", "Row"), out.map { it.exerciseName })
        assertContiguousOrder(out)
    }

    @Test fun move_down_atBottomIsNoOp() {
        val out = DayPlanEditor.move(day(), day()[2], up = false)
        assertEquals(listOf("Squat", "Bench", "Row"), out.map { it.exerciseName })
        assertContiguousOrder(out)
    }

    @Test fun move_unknownRowIsNoOp() {
        val out = DayPlanEditor.move(day(), ex(99, "Ghost", 0), up = true)
        assertEquals(listOf("Squat", "Bench", "Row"), out.map { it.exerciseName })
        assertContiguousOrder(out)
    }

    // ---- field edit copy (mirrors ProgramViewModel.editExercise) ----

    @Test fun editCopy_changesTargetsButNotIdentityOrLoggedState() {
        val original = day()[0].copy(isLogged = true, actualWeightKg = 60f, actualReps = "5", actualSets = 3)
        val edited = original.copy(sets = 5, targetReps = "5-8", targetWeightKg = 70f, notes = "paused")

        assertEquals(5, edited.sets)
        assertEquals("5-8", edited.targetReps)
        assertEquals(70f, edited.targetWeightKg, 0f)
        assertEquals("paused", edited.notes)
        // Identity + keying preserved (in-place @Update).
        assertEquals(original.id, edited.id)
        assertEquals(original.orderInDay, edited.orderInDay)
        assertEquals(original.dayOfWeek, edited.dayOfWeek)
        // E1 decision: editing the target does NOT clear an existing log.
        assertTrue(edited.isLogged)
        assertEquals(60f, edited.actualWeightKg, 0f)
    }
}
