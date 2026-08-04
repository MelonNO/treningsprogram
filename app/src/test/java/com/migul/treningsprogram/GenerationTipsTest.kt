package com.migul.treningsprogram

import com.migul.treningsprogram.ui.common.GenerationTips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * P5 / brief 01 (body-progress batch 2026-08-04): the rotating wait copy.
 *
 *  - [GenerationTips.messages] is the 150-message pool the four wait surfaces share.
 *  - [GenerationTips.tip] still indexes into it and wraps around forever (kept so a caller can
 *    advance a plain integer counter without bounds checks).
 *  - [GenerationTips.rotation] is the random sequencer the wait screens actually use: a shuffle
 *    bag that emits every message once before repeating, reshuffled per wait.
 */
class GenerationTipsTest {

    private companion object {
        const val EXPECTED_POOL_SIZE = 150
    }

    // ── The pool ─────────────────────────────────────────────────────────────────────────────────

    @Test fun `the pool holds exactly 150 messages`() {
        assertEquals(EXPECTED_POOL_SIZE, GenerationTips.messages.size)
    }

    @Test fun `every message is non-blank`() {
        assertTrue(GenerationTips.messages.all { it.isNotBlank() })
    }

    @Test fun `every message is distinct`() {
        assertEquals(GenerationTips.messages.size, GenerationTips.messages.distinct().size)
    }

    // ── tip(index): unchanged contract ───────────────────────────────────────────────────────────

    @Test fun `tip wraps around the message list`() {
        val n = GenerationTips.messages.size
        assertEquals(GenerationTips.messages[0], GenerationTips.tip(0))
        assertEquals(GenerationTips.messages[0], GenerationTips.tip(n))      // wrap forward
        assertEquals(GenerationTips.messages[1 % n], GenerationTips.tip(n + 1))
    }

    @Test fun `negative indices are handled (floorMod, never crashes)`() {
        // floorMod keeps the index in range even if a counter were ever negative.
        assertEquals(GenerationTips.messages.last(), GenerationTips.tip(-1))
    }

    // ── rotation(): the random sequencer ─────────────────────────────────────────────────────────

    @Test fun `a rotation emits every message once before any repeat`() {
        val rotation = GenerationTips.rotation(Random(20260804))
        val emitted = (1..EXPECTED_POOL_SIZE).map { rotation.next() }

        assertEquals(EXPECTED_POOL_SIZE, emitted.size)
        assertEquals(EXPECTED_POOL_SIZE, emitted.distinct().size)              // no repeats
        assertEquals(GenerationTips.messages.toSet(), emitted.toSet())         // a permutation
    }

    @Test fun `a rotation reshuffles and keeps going past the pool size`() {
        val rotation = GenerationTips.rotation(Random(7))
        val emitted = (1..EXPECTED_POOL_SIZE * 3 + 17).map { rotation.next() }

        assertTrue(emitted.all { it in GenerationTips.messages })
        // Each full pass is still a complete permutation.
        emitted.chunked(EXPECTED_POOL_SIZE).filter { it.size == EXPECTED_POOL_SIZE }.forEach {
            assertEquals(EXPECTED_POOL_SIZE, it.distinct().size)
        }
    }

    @Test fun `different seeds produce different sequences`() {
        val a = GenerationTips.rotation(Random(1)).let { r -> (1..20).map { r.next() } }
        val b = GenerationTips.rotation(Random(2)).let { r -> (1..20).map { r.next() } }
        assertNotEquals(a, b)
    }

    @Test fun `the same seed reproduces the same sequence`() {
        val a = GenerationTips.rotation(Random(99)).let { r -> (1..30).map { r.next() } }
        val b = GenerationTips.rotation(Random(99)).let { r -> (1..30).map { r.next() } }
        assertEquals(a, b)
    }

    @Test fun `two default rotations rarely start the same way (sequence differs per wait)`() {
        // Not seeded: this is the real call the wait screens make. With 150 messages the odds of
        // five identical opening messages by chance are ~1 in 150^5, so a failure means the
        // rotation is not actually random.
        val a = GenerationTips.rotation().let { r -> (1..5).map { r.next() } }
        val b = GenerationTips.rotation().let { r -> (1..5).map { r.next() } }
        assertNotEquals(a, b)
    }

    @Test fun `a cycle boundary never repeats the message that just played`() {
        // Two messages: without the boundary guard, "A B" then a reshuffle to "B A" would emit
        // B twice in a row. Checked across many seeds.
        (1..200).forEach { seed ->
            val rotation = GenerationTips.rotationOf(listOf("A", "B"), Random(seed))
            val emitted = (1..12).map { rotation.next() }
            emitted.zipWithNext().forEach { (prev, next) -> assertNotEquals(prev, next) }
        }
    }

    // ── Safety: degenerate pools and unbounded calls ─────────────────────────────────────────────

    @Test fun `an empty pool yields blanks and never crashes`() {
        val rotation = GenerationTips.rotationOf(emptyList())
        repeat(500) { assertEquals("", rotation.next()) }
    }

    @Test fun `a single-message pool repeats that message forever`() {
        val rotation = GenerationTips.rotationOf(listOf("only one"))
        repeat(500) { assertEquals("only one", rotation.next()) }
    }

    @Test fun `unbounded calls on the real pool never crash`() {
        val rotation = GenerationTips.rotation()
        repeat(EXPECTED_POOL_SIZE * 10) { assertTrue(rotation.next().isNotBlank()) }
    }
}
