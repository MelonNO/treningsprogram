package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.ExerciseSessionCount
import com.migul.treningsprogram.domain.ExercisePickerSort
import com.migul.treningsprogram.domain.ProgressDefaultExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Body-progress batch 2026-08-04 (brief 03) — the Progress tab's random default exercise.
 */
class ProgressDefaultExerciseTest {

    private fun ordered(vararg names: String) = names.toList()

    @Test fun `the pool is the 15 most-logged (decision 11)`() {
        val names = (1..40).map { "Ex$it" }
        val pool = ProgressDefaultExercise.pool(names)
        assertEquals(15, pool.size)
        assertEquals(names.take(15), pool)
    }

    @Test fun `fewer than 15 logged exercises means the pool is whatever exists`() {
        val names = ordered("A", "B", "C")
        assertEquals(names, ProgressDefaultExercise.pool(names))
        assertTrue(ProgressDefaultExercise.pick(names) in names)
    }

    @Test fun `nothing logged yields no pick, so the empty state stays`() {
        assertNull(ProgressDefaultExercise.pick(emptyList()))
        assertTrue(ProgressDefaultExercise.pool(emptyList()).isEmpty())
    }

    @Test fun `a single logged exercise is always the pick`() {
        assertEquals("Squat", ProgressDefaultExercise.pick(listOf("Squat")))
    }

    @Test fun `the pick never comes from outside the top 15`() {
        val names = (1..40).map { "Ex$it" }
        val top15 = names.take(15).toSet()
        repeat(300) {
            assertTrue(ProgressDefaultExercise.pick(names) in top15)
        }
    }

    @Test fun `successive launches can yield different defaults`() {
        val names = (1..15).map { "Ex$it" }
        // Two different seeds stand in for two app launches.
        val a = ProgressDefaultExercise.pick(names, Random(1))
        val b = ProgressDefaultExercise.pick(names, Random(2))
        // Not a strict inequality assertion on one pair (two seeds may collide); instead check the
        // choice genuinely varies across many launches rather than being pinned to one exercise.
        val seen = (1..200).map { ProgressDefaultExercise.pick(names, Random(it.toLong())) }.toSet()
        assertTrue("expected varied defaults across launches, got $seen", seen.size > 1)
        assertTrue(a in names && b in names)
    }

    @Test fun `a seeded pick is deterministic`() {
        val names = (1..15).map { "Ex$it" }
        assertEquals(
            ProgressDefaultExercise.pick(names, Random(42)),
            ProgressDefaultExercise.pick(names, Random(42))
        )
    }

    // ── resolve: the once-per-launch / manual-switch lifetime rule (decision 11) ──────────────

    @Test fun `first open of a launch rolls a default from the pool`() {
        val names = (1..20).map { "Ex$it" }
        val chosen = ProgressDefaultExercise.resolve(names, remembered = null, random = Random(7))
        assertTrue(chosen in names.take(15))
    }

    @Test fun `re-entering the tab keeps the current selection`() {
        val names = (1..20).map { "Ex$it" }
        // Whatever is remembered wins — this covers BOTH the rolled default and a manual switch.
        assertEquals("Ex4", ProgressDefaultExercise.resolve(names, remembered = "Ex4"))
        // Even a manual switch to something OUTSIDE the top 15 sticks.
        assertEquals("Ex19", ProgressDefaultExercise.resolve(names, remembered = "Ex19"))
    }

    @Test fun `a remembered name that no longer exists is replaced, not shown`() {
        val names = (1..20).map { "Ex$it" }
        val chosen = ProgressDefaultExercise.resolve(names, remembered = "Deleted Lift")
        assertTrue("must not keep a name with no data", chosen != "Deleted Lift")
        assertTrue(chosen in names.take(15))
    }

    @Test fun `resolve with no logged exercises stays empty`() {
        assertNull(ProgressDefaultExercise.resolve(emptyList(), remembered = null))
        assertNull(ProgressDefaultExercise.resolve(emptyList(), remembered = "Anything"))
    }

    @Test fun `resolve is stable across repeated opens in one session`() {
        val names = (1..20).map { "Ex$it" }
        val first = ProgressDefaultExercise.resolve(names, null, Random(3))!!
        // Simulating the store: the first pick is remembered and fed back on every later open.
        repeat(10) {
            assertEquals(first, ProgressDefaultExercise.resolve(names, first))
        }
    }

    @Test fun `the pool is ranked by distinct session count, alpha tie-break (A2)`() {
        // The pool head must follow ExercisePickerSort — the same ordering the picker already uses,
        // which wraps COUNT(DISTINCT sessionId) over all sets (warm-up or working).
        val counts = listOf(
            ExerciseSessionCount("Zercher Squat", 9),
            ExerciseSessionCount("Bench Press", 20),
            ExerciseSessionCount("Ab Wheel", 9),
            ExerciseSessionCount("Deadlift", 15)
        )
        val ordered = ExercisePickerSort.order(counts)
        assertEquals(listOf("Bench Press", "Deadlift", "Ab Wheel", "Zercher Squat"), ordered)
        assertEquals(ordered, ProgressDefaultExercise.pool(ordered))
    }
}
