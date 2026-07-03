package com.migul.treningsprogram

import com.migul.treningsprogram.domain.RecoverySuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B5 — recovery-aware selection of the rest-day suggestion + the card's visibility gate.
 */
class B5RecoverySuggestionsTest {

    private val soreLegs = setOf("Quads", "Hamstrings", "Glutes")

    @Test fun `sore legs never get a leg-stressing suggestion`() {
        // Across a long run of days, no picked suggestion may stress a recovering muscle.
        for (day in 0L..60L) {
            val s = RecoverySuggestions.pick(soreLegs, day)
            assertTrue(
                "day $day picked '${s.title}' which stresses ${s.stresses}",
                s.stresses.intersect(soreLegs).isEmpty()
            )
        }
    }

    @Test fun `brand-new user (nothing recovering) gets a sensible generic walk`() {
        val s = RecoverySuggestions.pick(emptySet(), 5L)
        assertTrue(s.title, s.title.contains("walk", ignoreCase = true) ||
            s.line.contains("walk", ignoreCase = true))
        assertTrue(s.stresses.isEmpty())
    }

    @Test fun `everything sore still yields a safe suggestion, never nothing`() {
        val everything = setOf(
            "Quads", "Hamstrings", "Glutes", "Calves", "Lower Back", "Upper Back",
            "Chest", "Front Delts", "Side Delts", "Rear Delts", "Biceps", "Triceps", "Core"
        )
        val s = RecoverySuggestions.pick(everything, 3L)
        assertTrue(s.stresses.intersect(everything).isEmpty())
    }

    @Test fun `rotation - consecutive days differ`() {
        val a = RecoverySuggestions.pick(emptySet(), 10L)
        val b = RecoverySuggestions.pick(emptySet(), 11L)
        assertTrue(a.title != b.title)
        // Deterministic per day.
        assertEquals(a, RecoverySuggestions.pick(emptySet(), 10L))
    }

    // ── visibility gate ─────────────────────────────────────────────────────────────────────

    @Test fun `shows only on a true rest day`() {
        assertTrue(RecoverySuggestions.shouldShow(
            planEmpty = true, hasActiveSession = false, completedToday = false,
            enabled = true, dismissedEpochDay = -1L, todayEpochDay = 100L
        ))
        // Training day (plan exists) → never.
        assertFalse(RecoverySuggestions.shouldShow(false, false, false, true, -1L, 100L))
        // Session in progress → no.
        assertFalse(RecoverySuggestions.shouldShow(true, true, false, true, -1L, 100L))
        // Already logged something today → no.
        assertFalse(RecoverySuggestions.shouldShow(true, false, true, true, -1L, 100L))
        // Permanently switched off → no.
        assertFalse(RecoverySuggestions.shouldShow(true, false, false, false, -1L, 100L))
    }

    @Test fun `dismiss hides for THAT day and returns the next rest day`() {
        assertFalse(RecoverySuggestions.shouldShow(true, false, false, true,
            dismissedEpochDay = 100L, todayEpochDay = 100L))
        assertTrue(RecoverySuggestions.shouldShow(true, false, false, true,
            dismissedEpochDay = 100L, todayEpochDay = 101L))
    }
}
