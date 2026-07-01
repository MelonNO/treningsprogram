package com.migul.treningsprogram

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.domain.MuscleRecovery
import com.migul.treningsprogram.domain.MuscleRecovery.RecoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 12 — per-muscle base recovery times, scaled by the session's logged effort (RPE/RIR),
 * deterministic + on-device (no AI/API). Guards:
 *   1. Different muscle groups have DIFFERENT base recovery windows (bigger → longer).
 *   2. Logged effort lengthens (Hard) / shortens (Easy) recovery; Moderate + blank leave it
 *      unchanged ([12a]: blank effort == medium).
 *   3. Scaling is driven ONLY by the effort level, not by set count / volume / load.
 */
class Item12PerMuscleRecoveryTest {

    private val now = 1_000_000_000_000L
    private val hour = 60L * 60L * 1000L
    private fun ago(ms: Long) = now - ms

    private fun record(
        elapsedH: Long,
        weight: Float = 1.0f,
        effort: Int = MuscleRecovery.EFFORT_MODERATE
    ) = MuscleRecovery.ExerciseStimulusRecord(
        sessionId = 1L,
        sessionDateMs = ago(elapsedH * hour),
        exerciseName = "x",
        weight = weight,
        effortLevel = effort
    )

    // ── 1. Per-muscle base table ([12b] fine-grain) ──────────────────────────────

    @Test fun biggerMusclesHaveLongerBaseRecovery() {
        val quads = MuscleRecovery.baseRecoveryMsFor("Quads")
        val chest = MuscleRecovery.baseRecoveryMsFor("Chest")
        val biceps = MuscleRecovery.baseRecoveryMsFor("Biceps")
        val cardio = MuscleRecovery.baseRecoveryMsFor("Cardio")
        assertTrue("Quads rest longer than Chest", quads > chest)
        assertTrue("Chest rests longer than Biceps", chest > biceps)
        assertTrue("Biceps rest longer than Cardio", biceps > cardio)
    }

    @Test fun chestKeepsHistorical48hBaseline() {
        assertEquals(48L * hour, MuscleRecovery.baseRecoveryMsFor("Chest"))
    }

    @Test fun notAllMusclesShareOneWindow() {
        val distinct = MuscleClassifier.ALL_FINE_MUSCLES
            .map { MuscleRecovery.baseRecoveryMsFor(it) }.toSet()
        assertTrue("Recovery windows must differ across groups", distinct.size >= 3)
    }

    @Test fun unknownMuscleFallsBackTo48h() {
        assertEquals(48L * hour, MuscleRecovery.baseRecoveryMsFor("Underwater Basket Weaving"))
    }

    // ── 2. Effort mapping + multiplier ([12a] fallback) ──────────────────────────

    @Test fun effortLevelFromLabel_mapsRpeLabels() {
        assertEquals(MuscleRecovery.EFFORT_EASY, MuscleRecovery.effortLevelFromLabel("Easy"))
        assertEquals(MuscleRecovery.EFFORT_MODERATE, MuscleRecovery.effortLevelFromLabel("Moderate"))
        assertEquals(MuscleRecovery.EFFORT_HARD, MuscleRecovery.effortLevelFromLabel("Hard"))
        assertEquals(MuscleRecovery.EFFORT_UNSPECIFIED, MuscleRecovery.effortLevelFromLabel(""))
        assertEquals(MuscleRecovery.EFFORT_UNSPECIFIED, MuscleRecovery.effortLevelFromLabel("whatever"))
    }

    @Test fun effortMultiplier_hardLonger_easyShorter_moderateAndBlankNeutral() {
        assertTrue(MuscleRecovery.effortMultiplier(MuscleRecovery.EFFORT_HARD) > 1f)
        assertTrue(MuscleRecovery.effortMultiplier(MuscleRecovery.EFFORT_EASY) < 1f)
        assertEquals(1.0f, MuscleRecovery.effortMultiplier(MuscleRecovery.EFFORT_MODERATE), 0.0001f)
        // [12a]: blank/unspecified effort is treated as medium (neutral, factor 1.0).
        assertEquals(1.0f, MuscleRecovery.effortMultiplier(MuscleRecovery.EFFORT_UNSPECIFIED), 0.0001f)
    }

    // ── 3. Effort scales recovery through computeRecovery ────────────────────────

    @Test fun hardEffortLengthensRecovery_easyShortensIt() {
        val chestBase = MuscleRecovery.baseRecoveryMsFor("Chest") // 48h
        // Chest trained 40h ago at weight 1.0 — right around the base window.
        val moderate = MuscleRecovery.computeRecovery(
            listOf(record(40, effort = MuscleRecovery.EFFORT_MODERATE)), now, chestBase
        )!!
        val easy = MuscleRecovery.computeRecovery(
            listOf(record(40, effort = MuscleRecovery.EFFORT_EASY)), now, chestBase
        )!!
        val hard = MuscleRecovery.computeRecovery(
            listOf(record(40, effort = MuscleRecovery.EFFORT_HARD)), now, chestBase
        )!!
        // Moderate: 40h < 48h window → still recovering.
        assertEquals(RecoveryState.RECOVERING, moderate.state)
        // Easy (×0.75 → 36h window): 40h > 36h → already recovered.
        assertEquals(RecoveryState.READY, easy.state)
        // Hard (×1.30 → 62.4h window): still recovering, with MORE time remaining than Moderate.
        assertEquals(RecoveryState.RECOVERING, hard.state)
        assertTrue("Hard leaves more recovery remaining than Moderate",
            hard.remainingMs > moderate.remainingMs)
    }

    @Test fun blankEffortBehavesLikeModerate() {
        val base = MuscleRecovery.baseRecoveryMsFor("Chest")
        val blank = MuscleRecovery.computeRecovery(
            listOf(record(30, effort = MuscleRecovery.EFFORT_UNSPECIFIED)), now, base
        )!!
        val moderate = MuscleRecovery.computeRecovery(
            listOf(record(30, effort = MuscleRecovery.EFFORT_MODERATE)), now, base
        )!!
        assertEquals(moderate.state, blank.state)
        assertEquals(moderate.remainingMs, blank.remainingMs)
    }

    @Test fun biggerMuscleStillRecoveringWhileSmallerIsReady_sameElapsedAndEffort() {
        // Both trained 50h ago, same (Moderate) effort, same weight 1.0.
        val quads = MuscleRecovery.computeRecovery(
            listOf(record(50)), now, MuscleRecovery.baseRecoveryMsFor("Quads")   // 72h base
        )!!
        val biceps = MuscleRecovery.computeRecovery(
            listOf(record(50)), now, MuscleRecovery.baseRecoveryMsFor("Biceps")  // 36h base
        )!!
        assertEquals("Quads (72h base) still recovering at 50h", RecoveryState.RECOVERING, quads.state)
        assertEquals("Biceps (36h base) recovered by 50h", RecoveryState.READY, biceps.state)
    }
}
