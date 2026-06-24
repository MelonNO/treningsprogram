package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.data.repository.MesocycleContext
import com.migul.treningsprogram.domain.StallDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E2 (decision L1): the generation prompt is mesocycle/deload AWARE.
 *
 * Locks the pure [MesocycleContext.promptBlock] wording: a plain program injects NOTHING (so the
 * pre-E2 prompt is unchanged), a block conveys "week N of M" + adaptive (not fixed-ramp)
 * progression, and a deload week conveys a genuine deload directive plus the stalled lifts driving
 * it. Also a guard that the B3 STALLED-LIFTS seam (StallDetector) is unaffected by E2 — the deload
 * reuses it, it isn't reimplemented.
 */
class E2MesocyclePromptTest {

    @Test fun plainProgram_injectsNothing() {
        // No block, no deload → empty block → byte-for-byte the pre-E2 prompt for non-block users.
        assertEquals("", MesocycleContext.NONE.promptBlock())
        assertEquals("", MesocycleContext(mesocycleWeeks = 0, isDeload = false).promptBlock())
    }

    @Test fun blockContext_conveysWeekNofM_andAdaptiveProgression() {
        val block = MesocycleContext(mesocycleWeeks = 6, weekInBlock = 3).promptBlock()
        assertTrue(block.contains("MESOCYCLE"))
        assertTrue("must state week N of M", block.contains("WEEK 3 of 6"))
        // L1: adaptive weekly progression, explicitly NOT a fixed ramp.
        assertTrue(block.contains("NOT a fixed deterministic ramp"))
        // A pure block (no deload) must NOT carry deload phrasing.
        assertFalse(block.contains("DELOAD"))
    }

    @Test fun deloadContext_conveysDeloadDirective_andNamesStalledLifts() {
        val block = MesocycleContext(
            mesocycleWeeks = 6, weekInBlock = 4, isDeload = true,
            stalledLifts = listOf("Squat", "Bench Press")
        ).promptBlock()
        assertTrue(block.contains("THIS WEEK IS A DELOAD"))
        assertTrue(block.contains("Reduce overall volume and intensity"))
        // M2: the plateaued lifts driving the deload are named for the model.
        assertTrue(block.contains("Squat"))
        assertTrue(block.contains("Bench Press"))
    }

    @Test fun deloadWithoutBlock_stillEmitsDeloadDirective() {
        // A plain (non-block) program can still enter a stall-triggered deload.
        val block = MesocycleContext(mesocycleWeeks = 0, isDeload = true).promptBlock()
        assertTrue(block.contains("THIS WEEK IS A DELOAD"))
        // No block phrasing when it isn't a block.
        assertFalse(block.contains("mesocycle BLOCK"))
    }

    // ---- B3 seam guard: StallDetector still drives the deload, unchanged by E2 ----

    @Test fun b3StallDetectorSeam_stillFunctions() {
        val DAY = 86_400_000L
        fun p(day: Int, w: Float, r: Int) = StrengthPoint(day * DAY, w, r)
        // Flat e1RM over the window is still detected as a stall (B3 untouched).
        assertTrue(StallDetector.isStalled(listOf(p(1, 100f, 5), p(2, 100f, 5), p(3, 100f, 5))))
        // Progressing is still not stalled.
        assertFalse(StallDetector.isStalled(listOf(p(1, 90f, 5), p(2, 95f, 5), p(3, 100f, 5))))
    }
}
