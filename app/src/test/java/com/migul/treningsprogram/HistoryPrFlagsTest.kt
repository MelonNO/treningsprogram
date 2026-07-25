package com.migul.treningsprogram

import com.migul.treningsprogram.domain.HistoryPrFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QoL item 04 — PR/baseline flags for the History week-browser.
 *
 * Locks the ratified project rule: a first-ever lift is a BASELINE, never a PR; a PR needs a
 * real prior best, strictly beaten (same rule as GamificationRepository.isWeightPr /
 * BeatTarget.isPr). Granularity: at most one PR per exercise per session.
 */
class HistoryPrFlagsTest {

    private fun row(name: String, session: Long, dateMs: Long, max: Float) =
        HistoryPrFlags.ExerciseSessionMax(name, session, dateMs, max)

    @Test
    fun `first-ever lift is baseline, not PR`() {
        val flags = HistoryPrFlags.flag(listOf(row("Squat", 1, 1000, 100f)))
        val f = flags.getValue("Squat" to 1L)
        assertFalse(f.isPr)
        assertNull(f.priorMaxKg)
    }

    @Test
    fun `strictly heavier later session is a PR and carries the prior max`() {
        val flags = HistoryPrFlags.flag(
            listOf(row("Squat", 1, 1000, 100f), row("Squat", 2, 2000, 105f))
        )
        val f = flags.getValue("Squat" to 2L)
        assertTrue(f.isPr)
        assertEquals(100f, f.priorMaxKg)
    }

    @Test
    fun `equalling the previous best is not a PR`() {
        val flags = HistoryPrFlags.flag(
            listOf(row("Squat", 1, 1000, 100f), row("Squat", 2, 2000, 100f))
        )
        assertFalse(flags.getValue("Squat" to 2L).isPr)
    }

    @Test
    fun `lighter later session is not a PR and the running max is retained`() {
        val flags = HistoryPrFlags.flag(
            listOf(
                row("Squat", 1, 1000, 100f),
                row("Squat", 2, 2000, 90f),
                row("Squat", 3, 3000, 101f)
            )
        )
        assertFalse(flags.getValue("Squat" to 2L).isPr)
        assertEquals(100f, flags.getValue("Squat" to 2L).priorMaxKg)
        // Session 3 must beat the ALL-TIME prior best (100), not just session 2's 90.
        assertTrue(flags.getValue("Squat" to 3L).isPr)
        assertEquals(100f, flags.getValue("Squat" to 3L).priorMaxKg)
    }

    @Test
    fun `input order does not matter`() {
        val shuffled = listOf(
            row("Squat", 3, 3000, 110f),
            row("Squat", 1, 1000, 100f),
            row("Squat", 2, 2000, 105f)
        )
        val flags = HistoryPrFlags.flag(shuffled)
        assertFalse(flags.getValue("Squat" to 1L).isPr)
        assertTrue(flags.getValue("Squat" to 2L).isPr)
        assertTrue(flags.getValue("Squat" to 3L).isPr)
    }

    @Test
    fun `same dateMs ties break by sessionId`() {
        val flags = HistoryPrFlags.flag(
            listOf(row("Squat", 2, 1000, 105f), row("Squat", 1, 1000, 100f))
        )
        assertFalse(flags.getValue("Squat" to 1L).isPr)   // session 1 first → baseline
        assertTrue(flags.getValue("Squat" to 2L).isPr)
    }

    @Test
    fun `exercises are independent`() {
        val flags = HistoryPrFlags.flag(
            listOf(
                row("Squat", 1, 1000, 100f),
                row("Bench Press", 1, 1000, 60f),
                row("Bench Press", 2, 2000, 62.5f)
            )
        )
        assertFalse(flags.getValue("Squat" to 1L).isPr)
        assertTrue(flags.getValue("Bench Press" to 2L).isPr)
    }

    // ── prSetIndex ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `prSetIndex marks the first working set that reaches the session max`() {
        val idx = HistoryPrFlags.prSetIndex(
            weights = floatArrayOf(60f, 105f, 105f, 100f),
            warmups = booleanArrayOf(true, false, false, false),
            priorMaxKg = 100f
        )
        assertEquals(1, idx)
    }

    @Test
    fun `prSetIndex returns -1 for a first-ever exercise (baseline)`() {
        val idx = HistoryPrFlags.prSetIndex(
            weights = floatArrayOf(100f),
            warmups = booleanArrayOf(false),
            priorMaxKg = null
        )
        assertEquals(-1, idx)
    }

    @Test
    fun `prSetIndex never marks a warm-up even when it is the heaviest set`() {
        val idx = HistoryPrFlags.prSetIndex(
            weights = floatArrayOf(120f, 100f),
            warmups = booleanArrayOf(true, false),
            priorMaxKg = 110f
        )
        assertEquals(-1, idx)   // working max 100 does not beat 110; warm-up 120 must not count
    }

    @Test
    fun `prSetIndex returns -1 when the session did not beat the prior best`() {
        val idx = HistoryPrFlags.prSetIndex(
            weights = floatArrayOf(95f, 100f),
            warmups = booleanArrayOf(false, false),
            priorMaxKg = 100f
        )
        assertEquals(-1, idx)
    }

    @Test
    fun `prSetIndex returns -1 for bodyweight (zero-weight) sets`() {
        val idx = HistoryPrFlags.prSetIndex(
            weights = floatArrayOf(0f, 0f),
            warmups = booleanArrayOf(false, false),
            priorMaxKg = null
        )
        assertEquals(-1, idx)
    }
}
