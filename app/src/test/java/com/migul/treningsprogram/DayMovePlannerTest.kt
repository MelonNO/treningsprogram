package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.DayMovePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2: "do another day's workout today". [DayMovePlanner.movedRows] is the pure rule for moving a
 * source day's plan rows into today's slot on completion: performed exercises become logged, the
 * rest stay non-logged, orderInDay is renumbered, and the week rationale is stamped. The repository
 * (commitDayMove) stamps programId/weekStart and persists; this pins the in-memory transform.
 */
class DayMovePlannerTest {

    private var nextId = 1L
    private fun ex(day: Int, order: Int, name: String) = PlannedExercise(
        id = nextId++,
        weekStart = 0L,
        dayOfWeek = day,
        orderInDay = order,
        exerciseName = name,
        sets = 3,
        targetReps = "8-12",
        targetWeightKg = 40f
    )

    @Test fun `moved rows land on the target day with contiguous order`() {
        val source = listOf(ex(3, 2, "B"), ex(3, 0, "A"), ex(3, 1, "C"))  // Wed, out of order
        val moved = DayMovePlanner.movedRows(source, targetDay = 1, performedNames = emptySet(), rationale = "")
        assertTrue("all rows move to the target day", moved.all { it.dayOfWeek == 1 })
        // Sorted by original order then renumbered 0..n.
        assertEquals(listOf("A", "C", "B"), moved.map { it.exerciseName })
        assertEquals(listOf(0, 1, 2), moved.map { it.orderInDay })
    }

    @Test fun `only performed exercises are marked logged`() {
        val source = listOf(ex(3, 0, "Squat"), ex(3, 1, "RDL"), ex(3, 2, "Leg Curl"))
        val moved = DayMovePlanner.movedRows(
            source, targetDay = 1, performedNames = setOf("Squat", "Leg Curl"), rationale = ""
        )
        assertTrue(moved.first { it.exerciseName == "Squat" }.isLogged)
        assertTrue(moved.first { it.exerciseName == "Leg Curl" }.isLogged)
        assertFalse("an exercise the user skipped stays not-logged", moved.first { it.exerciseName == "RDL" }.isLogged)
    }

    @Test fun `the week rationale is stamped on every moved row`() {
        val source = listOf(ex(3, 0, "Squat"), ex(3, 1, "RDL"))
        val moved = DayMovePlanner.movedRows(source, targetDay = 1, performedNames = emptySet(), rationale = "Rebalanced your week.")
        assertTrue(moved.all { it.rationale == "Rebalanced your week." })
    }

    @Test fun `moved rows get a fresh id so they insert as new rows`() {
        val source = listOf(ex(3, 0, "Squat"))
        val moved = DayMovePlanner.movedRows(source, targetDay = 1, performedNames = setOf("Squat"), rationale = "")
        assertEquals(0L, moved.single().id)
        // The prescription (sets/reps/weight) is carried over from the source plan.
        assertEquals(40f, moved.single().targetWeightKg, 0.001f)
    }

    @Test fun `empty source produces no rows`() {
        assertTrue(DayMovePlanner.movedRows(emptyList(), targetDay = 1, performedNames = emptySet(), rationale = "").isEmpty())
    }
}
