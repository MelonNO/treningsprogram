package com.migul.treningsprogram

import com.migul.treningsprogram.domain.EffortTrend
import com.migul.treningsprogram.domain.EffortTrend.LabelledSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N4 — the per-lift effort signal for the generation prompt, verified off-API on the pure
 * derivation. (Warm-up exclusion is structural: AiRepository collects labels only from the
 * already-warm-up-filtered working sets; these tests pin the derivation itself.)
 */
class N4EffortTrendTest {

    private fun sets(vararg labels: String): List<LabelledSet> =
        labels.mapIndexed { i, l -> LabelledSet(dateMs = 1_000L * (i + 1), rpeLabel = l) }

    // ── line derivation ──────────────────────────────────────────────────────────────────────

    @Test fun `no labelled sets - no line`() {
        assertNull(EffortTrend.line("Bench Press", emptyList()))
        assertNull(EffortTrend.line("Bench Press", sets("", "", "")))
        assertNull(EffortTrend.line("Bench Press", sets("weird-label")))
    }

    @Test fun `majority label is reported with honest counts`() {
        val line = EffortTrend.line("Bench Press", sets("Hard", "Hard", "Moderate"))!!
        assertTrue(line, line.startsWith("Bench Press: mostly Hard (2 of 3 labelled sets)"))
    }

    @Test fun `ties resolve to the harder label`() {
        val line = EffortTrend.line("Squat", sets("Easy", "Hard"))!!
        assertTrue(line, line.contains("mostly Hard"))
    }

    @Test fun `trending harder when the newer half is heavier effort`() {
        val line = EffortTrend.line("Deadlift", sets("Easy", "Easy", "Hard", "Hard"))!!
        assertTrue(line, line.endsWith("trending harder"))
    }

    @Test fun `trending easier when effort falls off`() {
        val line = EffortTrend.line("Deadlift", sets("Hard", "Hard", "Easy", "Easy"))!!
        assertTrue(line, line.endsWith("trending easier"))
    }

    @Test fun `steady when the halves agree`() {
        val line = EffortTrend.line("Row", sets("Moderate", "Moderate", "Moderate", "Moderate"))!!
        assertTrue(line, line.endsWith("steady"))
    }

    @Test fun `fewer than four labelled sets - no trend suffix`() {
        val line = EffortTrend.line("Row", sets("Hard", "Hard", "Hard"))!!
        assertFalse(line, line.contains("trending"))
        assertFalse(line, line.contains("steady"))
    }

    @Test fun `labels are matched case-insensitively - the UI vocabulary is the ground truth`() {
        // The logging chips write exactly "Easy"/"Moderate"/"Hard"; tolerate case drift anyway.
        val line = EffortTrend.line("Curl", sets("hard", "HARD", "easy"))!!
        assertTrue(line, line.contains("mostly Hard (2 of 3"))
    }

    // ── whole block ──────────────────────────────────────────────────────────────────────────

    @Test fun `zero labelled history yields an EMPTY block - no header, prompt unchanged`() {
        assertEquals("", EffortTrend.promptBlock(emptyMap()))
        assertEquals("", EffortTrend.promptBlock(mapOf("Bench Press" to emptyList())))
        assertEquals("", EffortTrend.promptBlock(mapOf("Bench Press" to sets("", ""))))
    }

    @Test fun `block lists only lifts with real labels, under one header`() {
        val block = EffortTrend.promptBlock(
            mapOf(
                "Bench Press" to sets("Hard", "Hard"),
                "Face Pulls" to emptyList()
            )
        )
        assertTrue(block, block.contains("LOGGED EFFORT"))
        assertTrue(block, block.contains("Bench Press: mostly Hard"))
        assertFalse(block, block.contains("Face Pulls"))
        // Compact: exactly one header + one lift line.
        assertEquals(block, 2, block.trim().lines().count { it.isNotBlank() })
    }

    @Test fun `block is capped and stays compact`() {
        val many = (1..30).associate { "Exercise $it" to sets("Hard", "Moderate", "Hard") }
        val block = EffortTrend.promptBlock(many)
        val liftLines = block.lines().count { it.startsWith("  ") }
        assertTrue("expected at most 12 lift lines, got $liftLines", liftLines <= 12)
    }
}
