package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.XpEventBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U2: tests the pure XP-event itemization helper ([XpEventBuilder]).
 *
 * HARD CONSTRAINT under test: capture is observation only. The KEY invariant is that the SUM of
 * the emitted events' amounts equals the total xpEarned the caller already computed — the helper
 * must never invent, drop, or alter XP. (The actual XP/level math lives in GamificationRepository
 * and is unchanged by U2; these tests only verify the log faithfully reflects the award.)
 */
class U2XpLogTest {

    @Test fun base_only_emitsSingleWorkoutCompletedRow_sumMatches() {
        val events = XpEventBuilder.buildWorkoutEvents(
            timestampMs = 1000L, sessionId = 7L,
            baseXp = 50, setXp = 0, setCount = 0, prXp = 0, prCount = 0, bonusChallengeXp = 0
        )
        assertEquals(1, events.size)
        assertEquals("Workout completed", events[0].reason)
        assertEquals(50, events.sumOf { it.amount })
        assertEquals(7L, events[0].sessionId)
        assertEquals(1000L, events[0].timestampMs)
    }

    @Test fun allComponents_itemized_sumEqualsTotalXpEarned() {
        val baseXp = 50; val setXp = 40; val prXp = 60; val bonus = 25
        val events = XpEventBuilder.buildWorkoutEvents(
            timestampMs = 2000L, sessionId = 3L,
            baseXp = baseXp, setXp = setXp, setCount = 8, prXp = prXp, prCount = 2,
            bonusChallengeXp = bonus, challengeNames = listOf("8 sets")
        )
        // One row per non-zero component.
        assertEquals(4, events.size)
        // Critical invariant: the log totals exactly the awarded XP — nothing added or lost.
        assertEquals(baseXp + setXp + prXp + bonus, events.sumOf { it.amount })
    }

    @Test fun zeroValueComponents_areSkipped_butBaseAlwaysPresent() {
        val events = XpEventBuilder.buildWorkoutEvents(
            timestampMs = 5L, sessionId = null,
            baseXp = 50, setXp = 0, setCount = 0, prXp = 30, prCount = 1, bonusChallengeXp = 0
        )
        val reasons = events.map { it.reason }
        assertTrue(reasons.any { it == "Workout completed" })
        assertTrue(reasons.any { it.startsWith("Personal record") })
        // No set / challenge rows when those components are zero.
        assertTrue(reasons.none { it.contains("logged") })
        assertTrue(reasons.none { it.contains("Daily challenge") })
        assertEquals(80, events.sumOf { it.amount })
    }

    @Test fun reasonStrings_areHumanReadable_withCountsAndPluralization() {
        val single = XpEventBuilder.buildWorkoutEvents(
            timestampMs = 1L, sessionId = null,
            baseXp = 50, setXp = 5, setCount = 1, prXp = 0, prCount = 0, bonusChallengeXp = 0
        )
        assertTrue(single.any { it.reason == "1 set logged" })

        val many = XpEventBuilder.buildWorkoutEvents(
            timestampMs = 1L, sessionId = null,
            baseXp = 50, setXp = 15, setCount = 3, prXp = 90, prCount = 3, bonusChallengeXp = 0
        )
        assertTrue(many.any { it.reason == "3 sets logged" })
        assertTrue(many.any { it.reason == "Personal record ×3" })
    }

    @Test fun challengeNames_includedInReason_whenPresent() {
        val events = XpEventBuilder.buildWorkoutEvents(
            timestampMs = 1L, sessionId = null,
            baseXp = 50, setXp = 0, setCount = 0, prXp = 0, prCount = 0,
            bonusChallengeXp = 20, challengeNames = listOf("Volume King", "PR Hunter")
        )
        assertTrue(events.any { it.reason == "Daily challenge: Volume King, PR Hunter" })
    }

    @Test fun challengeBonus_withoutNames_fallsBackToGenericReason() {
        val events = XpEventBuilder.buildWorkoutEvents(
            timestampMs = 1L, sessionId = null,
            baseXp = 50, setXp = 0, setCount = 0, prXp = 0, prCount = 0,
            bonusChallengeXp = 20, challengeNames = emptyList()
        )
        assertTrue(events.any { it.reason == "Daily challenge bonus" })
        assertEquals(70, events.sumOf { it.amount })
    }

    @Test fun timestampAndSession_propagatedToEveryRow() {
        val ts = 9_999L
        val events = XpEventBuilder.buildWorkoutEvents(
            timestampMs = ts, sessionId = 42L,
            baseXp = 50, setXp = 10, setCount = 2, prXp = 30, prCount = 1,
            bonusChallengeXp = 15, challengeNames = listOf("x")
        )
        assertTrue(events.all { it.timestampMs == ts })
        assertTrue(events.all { it.sessionId == 42L })
    }
}
