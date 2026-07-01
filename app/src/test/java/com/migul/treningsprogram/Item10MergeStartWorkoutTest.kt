package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.DayMovePlanner
import com.migul.treningsprogram.ui.log.LogWorkoutViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 10 — merge "Do this workout today" into "Start Workout" (with append-into-logged-session).
 * The plan-level move rule ([DayMovePlanner.applyMoveToTarget]) must:
 *   • REPLACE today's planned workout when today has no logged activity (empty or planned-only);
 *   • APPEND the moved workout after today's existing rows when today is already logged, KEEPING the
 *     logged rows (their isLogged flag + actuals) and renumbering contiguously → one continuous day;
 *   • never lose, duplicate, or reorder-away the existing logged rows.
 * The move-source resolution (which day is the source) is pinned by [AutoAttributeMoveTest].
 */
class Item10MergeStartWorkoutTest {

    private var nextId = 1L
    private fun ex(day: Int, order: Int, name: String, logged: Boolean = false) = PlannedExercise(
        id = nextId++,
        weekStart = 0L,
        dayOfWeek = day,
        orderInDay = order,
        exerciseName = name,
        sets = 3,
        targetReps = "8-12",
        targetWeightKg = 40f,
        isLogged = logged,
        actualWeightKg = if (logged) 42.5f else 0f,
        actualReps = if (logged) "10" else "",
        actualSets = if (logged) 3 else 0
    )

    private val today = 3      // Wednesday
    private val source = 5     // Friday (the other day being moved in)

    // ── REPLACE cases (today has NO logged activity) ─────────────────────────────

    @Test fun emptyToday_replace_movedRowsBecomeTheDay() {
        val src = listOf(ex(source, 0, "Squat"), ex(source, 1, "RDL"))
        val result = DayMovePlanner.applyMoveToTarget(
            targetRows = emptyList(), sourceRows = src, targetDay = today,
            performedNames = setOf("Squat", "RDL"), rationale = "r"
        )
        assertEquals(listOf("Squat", "RDL"), result.map { it.exerciseName })
        assertTrue("all rows attributed to today", result.all { it.dayOfWeek == today })
        assertEquals(listOf(0, 1), result.map { it.orderInDay })
        assertTrue("performed rows logged", result.all { it.isLogged })
    }

    @Test fun todayPlannedOnly_notLogged_replaceDiscardsOldPlan() {
        val target = listOf(ex(today, 0, "Bench"), ex(today, 1, "Fly"))   // planned, none logged
        val src = listOf(ex(source, 0, "Squat"))
        val result = DayMovePlanner.applyMoveToTarget(
            targetRows = target, sourceRows = src, targetDay = today,
            performedNames = setOf("Squat"), rationale = "r"
        )
        assertEquals("old (unlogged) plan is replaced", listOf("Squat"), result.map { it.exerciseName })
    }

    // ── APPEND cases (today ALREADY has logged activity) ─────────────────────────

    @Test fun todayLogged_append_keepsLoggedRowsThenMovedRows() {
        val target = listOf(
            ex(today, 0, "Bench", logged = true),
            ex(today, 1, "Incline DB Press", logged = true)
        )
        val src = listOf(ex(source, 0, "Squat"), ex(source, 1, "Leg Curl"))
        val result = DayMovePlanner.applyMoveToTarget(
            targetRows = target, sourceRows = src, targetDay = today,
            performedNames = setOf("Squat", "Leg Curl"), rationale = "r"
        )
        // Existing logged rows come first, in order, still logged, actuals intact.
        assertEquals(
            listOf("Bench", "Incline DB Press", "Squat", "Leg Curl"),
            result.map { it.exerciseName }
        )
        assertEquals("contiguous order across the whole day", listOf(0, 1, 2, 3), result.map { it.orderInDay })
        assertTrue("existing Bench stays logged", result.first { it.exerciseName == "Bench" }.isLogged)
        assertEquals("existing actuals preserved", 42.5f,
            result.first { it.exerciseName == "Bench" }.actualWeightKg, 0.001f)
        assertTrue("all target rows attributed to today", result.all { it.dayOfWeek == today })
    }

    @Test fun todayLogged_append_existingRowsAreNotDuplicatedOrDropped() {
        val target = listOf(ex(today, 0, "Bench", logged = true))
        val src = listOf(ex(source, 0, "Squat"))
        val result = DayMovePlanner.applyMoveToTarget(
            targetRows = target, sourceRows = src, targetDay = today,
            performedNames = setOf("Squat"), rationale = "r"
        )
        assertEquals("exactly the 1 existing + 1 moved row", 2, result.size)
        assertEquals(1, result.count { it.exerciseName == "Bench" })
    }

    @Test fun append_onlyPerformedMovedRowsAreLogged_existingUnaffected() {
        val target = listOf(ex(today, 0, "Bench", logged = true))
        val src = listOf(ex(source, 0, "Squat"), ex(source, 1, "RDL"))
        val result = DayMovePlanner.applyMoveToTarget(
            targetRows = target, sourceRows = src, targetDay = today,
            performedNames = setOf("Squat"), rationale = "r"   // RDL skipped
        )
        assertTrue(result.first { it.exerciseName == "Squat" }.isLogged)
        assertFalse(result.first { it.exerciseName == "RDL" }.isLogged)
    }

    @Test fun allResultRowsGetFreshIdAndRationale() {
        val target = listOf(ex(today, 0, "Bench", logged = true))
        val src = listOf(ex(source, 0, "Squat"))
        val result = DayMovePlanner.applyMoveToTarget(
            targetRows = target, sourceRows = src, targetDay = today,
            performedNames = setOf("Squat"), rationale = "Rebalanced your week."
        )
        assertTrue("fresh ids so they insert as new rows", result.all { it.id == 0L })
        assertTrue("week rationale stamped on every row", result.all { it.rationale == "Rebalanced your week." })
    }

    // ── move-source resolution (which day is the source) ─────────────────────────

    @Test fun startWorkoutOnAnotherDay_movesThatDayIntoToday() {
        assertEquals(source, LogWorkoutViewModel.resolveMoveSource(moveFromDay = 0, sessionDay = source, today = today))
    }

    @Test fun startWorkoutOnTodaysOwnDay_isNotAMove() {
        assertEquals(0, LogWorkoutViewModel.resolveMoveSource(moveFromDay = 0, sessionDay = today, today = today))
    }
}
