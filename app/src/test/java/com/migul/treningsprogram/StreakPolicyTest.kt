package com.migul.treningsprogram

import com.migul.treningsprogram.domain.StreakPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1 — schedule-aware streak: pure day-walk rules (brief acceptance criteria).
 * Days are logical epoch-days; the policy is shared verbatim by the live award
 * (GamificationRepository) and the backup-merge replay (StatsRecomputer).
 */
class StreakPolicyTest {

    // AC: train Mon, rest Tue (planned), train Wed → streak 2 (rest day is neutral).
    @Test fun plannedRestDayDoesNotBreak() {
        val streak = StreakPolicy.nextStreakOnWorkout(
            currentStreak = 1, lastWorkoutEpochDay = 100L, todayEpochDay = 102L,
            missedEpochDays = emptySet() // Tue was REST, not MISSED
        )
        assertEquals(2, streak)
    }

    // AC: train Mon, MISS planned Tue, train Wed → streak 1 (chain broken by the miss).
    @Test fun missedPlannedDayBreaks() {
        val streak = StreakPolicy.nextStreakOnWorkout(
            currentStreak = 1, lastWorkoutEpochDay = 100L, todayEpochDay = 102L,
            missedEpochDays = setOf(101L)
        )
        assertEquals(1, streak)
    }

    // AC: two workouts on the same logical day count the day once.
    @Test fun sameDaySecondWorkoutUnchanged() {
        val streak = StreakPolicy.nextStreakOnWorkout(
            currentStreak = 5, lastWorkoutEpochDay = 100L, todayEpochDay = 100L,
            missedEpochDays = emptySet()
        )
        assertEquals(5, streak)
    }

    // Plain consecutive training days still increment.
    @Test fun consecutiveDaysIncrement() {
        val streak = StreakPolicy.nextStreakOnWorkout(
            currentStreak = 3, lastWorkoutEpochDay = 100L, todayEpochDay = 101L,
            missedEpochDays = emptySet()
        )
        assertEquals(4, streak)
    }

    // A-R2: a long silent gap (no session rows at all) is neutral — never a break.
    @Test fun silentGapIsNeutral() {
        val streak = StreakPolicy.nextStreakOnWorkout(
            currentStreak = 7, lastWorkoutEpochDay = 100L, todayEpochDay = 130L,
            missedEpochDays = emptySet()
        )
        assertEquals(8, streak)
    }

    // First workout ever.
    @Test fun firstWorkoutStartsAtOne() {
        assertEquals(
            1,
            StreakPolicy.nextStreakOnWorkout(0, null, 100L, emptySet())
        )
    }

    // After a freshness zeroing (missed yesterday), today's workout restarts at 1.
    @Test fun workoutAfterZeroedStreakRestartsAtOne() {
        assertEquals(
            1,
            StreakPolicy.nextStreakOnWorkout(0, 100L, 102L, setOf(101L))
        )
    }

    // A MISSED day OUTSIDE the (last, today) window is irrelevant to the workout walk.
    @Test fun missedDayOutsideWindowIgnored() {
        val streak = StreakPolicy.nextStreakOnWorkout(
            currentStreak = 2, lastWorkoutEpochDay = 100L, todayEpochDay = 101L,
            missedEpochDays = setOf(99L, 101L) // before last workout / today itself
        )
        assertEquals(3, streak)
    }

    // A missed day on the SAME day as a (merged) workout never breaks — training wins.
    @Test fun missedOnBoundaryDaysExcluded() {
        assertFalse(StreakPolicy.isChainBroken(100L, 102L, setOf(100L, 102L)))
        assertTrue(StreakPolicy.isChainBroken(100L, 102L, setOf(101L)))
    }

    // AC: display freshness — "yesterday auto-logged MISSED ⇒ the streak shown today is broken".
    @Test fun freshnessDetectsMissedYesterday() {
        // last workout day 100, today 102, MISSED on 101 ⇒ chain broken before any new workout.
        assertTrue(StreakPolicy.isChainBroken(100L, 102L, setOf(101L)))
        // rest-only gap ⇒ not broken.
        assertFalse(StreakPolicy.isChainBroken(100L, 102L, emptySet()))
    }
}
