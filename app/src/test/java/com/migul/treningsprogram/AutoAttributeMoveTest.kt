package com.migul.treningsprogram

import com.migul.treningsprogram.ui.log.LogWorkoutViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Item 10 — finishing ANY non-current-day workout auto-attributes to today. Guards the pure
 * move-source resolution that converges both entry paths (the explicit "Do this workout today"
 * button and the direct "Start Day Workout"), so a completed other-day workout logs against today
 * and rebalances the week silently.
 */
class AutoAttributeMoveTest {

    private val today = 3 // Wednesday, say

    @Test fun explicitMoveButton_usesItsSourceDay() {
        // P2 button: session attributed to today (3), moveFromDay = Monday (1).
        assertEquals(1, LogWorkoutViewModel.resolveMoveSource(moveFromDay = 1, sessionDay = today, today = today))
    }

    @Test fun directStartOtherDay_impliesMoveFromThatDay() {
        // "Start Day Workout" on Friday (5) while today is Wednesday (3) → implicit move source = 5.
        assertEquals(5, LogWorkoutViewModel.resolveMoveSource(moveFromDay = 0, sessionDay = 5, today = today))
    }

    @Test fun workoutBelongingToToday_noMove() {
        assertEquals(0, LogWorkoutViewModel.resolveMoveSource(moveFromDay = 0, sessionDay = today, today = today))
    }

    @Test fun freestyleNoDay_noMove() {
        assertEquals(0, LogWorkoutViewModel.resolveMoveSource(moveFromDay = 0, sessionDay = 0, today = today))
    }

    @Test fun explicitMoveTakesPrecedenceOverImplicit() {
        // Even if sessionDay differs from today, an explicit moveFromDay wins (its source is used).
        assertEquals(2, LogWorkoutViewModel.resolveMoveSource(moveFromDay = 2, sessionDay = 5, today = today))
    }

    @Test fun everyOtherWeekdayTriggersImplicitMove() {
        for (d in 1..7) {
            val expected = if (d == today) 0 else d
            assertEquals("day $d", expected, LogWorkoutViewModel.resolveMoveSource(0, d, today))
        }
    }

    // ── QoL item 10 (2026-07-25): moved-workout finish celebrates like a normal finish ────────
    // A committed move is attributed to TODAY (the source day is vacated by commitDayMove), so
    // the celebration must bounce today's chip — never the stored (source) workout day.

    @Test fun movedFinish_celebratesToday_notSourceDay() {
        // Direct "start Friday's workout" on Wednesday: workoutDay = 5 (source), move committed.
        assertEquals(today, LogWorkoutViewModel.celebrationDay(moveCommitted = true, workoutDay = 5, today = today))
    }

    @Test fun explicitMoveFinish_celebratesToday() {
        // P2 button path: workoutDay already today; committed move still celebrates today.
        assertEquals(today, LogWorkoutViewModel.celebrationDay(moveCommitted = true, workoutDay = today, today = today))
    }

    @Test fun normalFinish_celebratesItsOwnDay() {
        assertEquals(5, LogWorkoutViewModel.celebrationDay(moveCommitted = false, workoutDay = 5, today = today))
    }

    @Test fun normalFinish_missingDay_fallsBackToToday() {
        assertEquals(today, LogWorkoutViewModel.celebrationDay(moveCommitted = false, workoutDay = 0, today = today))
        assertEquals(today, LogWorkoutViewModel.celebrationDay(moveCommitted = false, workoutDay = 8, today = today))
    }
}
