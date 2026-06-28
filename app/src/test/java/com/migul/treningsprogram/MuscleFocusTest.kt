package com.migul.treningsprogram

import com.migul.treningsprogram.domain.MuscleFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1: the auto-rebalance trigger fires only on a genuine PRIMARY-MUSCLE-FOCUS change. [MuscleFocus]
 * is the pure decision: the dominant muscle group of a day, and whether it changed between two
 * exercise lists. These names map through [com.migul.treningsprogram.data.MuscleClassifier]
 * (Bench Press → Chest, Squat → Legs, etc.), so the test doubles as a guard that a focus flip is
 * detected while a same-focus edit is not.
 */
class MuscleFocusTest {

    @Test fun `dominant is the most common muscle group`() {
        // 2 chest movements ("bench", "fly"), 1 arms (triceps) → Chest dominates.
        assertEquals("Chest", MuscleFocus.dominant(listOf("Barbell Bench Press", "Cable Fly", "Tricep Pushdown")))
    }

    @Test fun `empty day has no focus`() {
        assertEquals("", MuscleFocus.dominant(emptyList()))
    }

    @Test fun `a real focus change (legs to chest) is detected`() {
        val before = listOf("Back Squat", "Leg Press", "Leg Curl")
        val after = listOf("Barbell Bench Press", "Incline Dumbbell Press", "Cable Fly")
        assertTrue(MuscleFocus.changed(before, after))
    }

    @Test fun `a same-focus swap is NOT a change`() {
        // Swapping one chest exercise for another keeps the day chest-dominant.
        val before = listOf("Barbell Bench Press", "Incline Dumbbell Press", "Cable Fly")
        val after = listOf("Dumbbell Bench Press", "Incline Dumbbell Press", "Cable Fly")
        assertFalse(MuscleFocus.changed(before, after))
    }

    @Test fun `clearing a day to nothing does not trigger a change`() {
        val before = listOf("Back Squat", "Leg Press")
        assertFalse(MuscleFocus.changed(before, emptyList()))
    }

    @Test fun `turning a rest day into a focused day counts as a change`() {
        assertTrue(MuscleFocus.changed(emptyList(), listOf("Barbell Bench Press", "Cable Fly")))
    }

    @Test fun `dominant is deterministic on a tie`() {
        // One legs ("squat"), one chest ("bench") — a 1-1 tie. The result must be STABLE across calls
        // (what P1 needs); the tie-break itself is the alphabetically-last group ("Legs" > "Chest").
        val input = listOf("Back Squat", "Barbell Bench Press")
        val first = MuscleFocus.dominant(input)
        assertEquals(first, MuscleFocus.dominant(input))
        assertEquals("Legs", first)
    }
}
