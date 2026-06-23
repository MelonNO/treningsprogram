package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.GamificationRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the "no PR on first-ever performance" baseline rule.
 *
 * The first time an exercise is performed it only sets the baseline and must never
 * count as a personal record. A PR is awarded only when a real PRIOR max is beaten.
 * This is the exact decision wired into both GamificationRepository.detectPersonalRecords
 * and WorkoutRepository.buildSessionRecap via [GamificationRepository.isWeightPr].
 */
class PrBaselineTest {

    @Test fun firstEverPerformance_noPreviousMax_isNotPr() {
        // No prior history (null) → baseline only, never a PR.
        assertFalse(GamificationRepository.isWeightPr(currentMax = 100f, previousMax = null))
    }

    @Test fun firstEverPerformance_zeroBodyweightLift_isNotPr() {
        // Even a 0kg first-ever (e.g. bodyweight) lift is the baseline, not a PR.
        assertFalse(GamificationRepository.isWeightPr(currentMax = 0f, previousMax = null))
    }

    @Test fun beatsPreviousMax_isPr() {
        assertTrue(GamificationRepository.isWeightPr(currentMax = 105f, previousMax = 100f))
    }

    @Test fun equalsPreviousMax_isNotPr() {
        assertFalse(GamificationRepository.isWeightPr(currentMax = 100f, previousMax = 100f))
    }

    @Test fun belowPreviousMax_isNotPr() {
        assertFalse(GamificationRepository.isWeightPr(currentMax = 95f, previousMax = 100f))
    }

    @Test fun beatsZeroBaseline_isPr() {
        // A real prior max of 0kg exists; lifting any positive weight beats it → PR.
        assertTrue(GamificationRepository.isWeightPr(currentMax = 20f, previousMax = 0f))
    }
}
