package com.migul.treningsprogram

import com.migul.treningsprogram.ui.log.LogWorkoutViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B02 — switching to a new exercise must not bleed the previously viewed exercise's weight
 * into the new exercise's field. [LogWorkoutViewModel.resolveWeightDefault] never takes the
 * previous exercise's value as an input, so a fresh bodyweight exercise resolves to null
 * ("BW"/empty) while an exercise's own logged/added weight still pre-fills for that exercise.
 *
 * Priority: saved draft > own last-logged weight > AI target (>0) > null (BW).
 */
class B02WeightDefaultTest {

    // --- The bug being fixed: no cross-exercise bleed -----------------------------------

    @Test fun freshBodyweight_noDraft_noHistory_noTarget_isBW() {
        // The previous exercise's weight is NOT an input — fresh BW exercise must clear to BW.
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = null, ownLastLoggedWeight = null, aiTargetWeightKg = 0f
        )
        assertNull("Fresh bodyweight exercise must default to BW (null), not inherit a weight", w)
    }

    @Test fun freshBodyweight_syncPass_beforeHistoryFetch_isBW() {
        // Synchronous render pass: own last-logged not yet known (null). Still must be BW,
        // not the value left in the field by the previously viewed exercise.
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = null, ownLastLoggedWeight = null, aiTargetWeightKg = 0f
        )
        assertNull(w)
    }

    // --- Must preserve: own added-weight bodyweight history -----------------------------

    @Test fun bodyweightWithOwnAddedWeightHistory_prefillsOwnWeight() {
        // Weighted pull-ups: previously logged +20kg → that exercise's own weight pre-fills.
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = null, ownLastLoggedWeight = 20f, aiTargetWeightKg = 0f
        )
        assertEquals(20f, w!!, 0f)
    }

    @Test fun ownHistoryWins_overAiTarget() {
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = null, ownLastLoggedWeight = 32.5f, aiTargetWeightKg = 40f
        )
        assertEquals(32.5f, w!!, 0f)
    }

    @Test fun pureBodyweightOwnHistoryAtZero_resolvesToZero_notNull() {
        // An exercise logged at weight 0 (pure BW) has its OWN history at 0 — that is its
        // own value (shown as "0"), not a bleed and not a missing-history case.
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = null, ownLastLoggedWeight = 0f, aiTargetWeightKg = 0f
        )
        assertEquals(0f, w!!, 0f)
    }

    // --- Must preserve: in-progress typed draft ----------------------------------------

    @Test fun savedDraft_wins_overEverything() {
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = 55f, ownLastLoggedWeight = 20f, aiTargetWeightKg = 40f
        )
        assertEquals("Draft-restore must never be overridden", 55f, w!!, 0f)
    }

    @Test fun savedDraftAtZero_isPreserved() {
        // User deliberately typed 0 for a bodyweight set — keep their 0, don't fall through.
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = 0f, ownLastLoggedWeight = 20f, aiTargetWeightKg = 40f
        )
        assertEquals(0f, w!!, 0f)
    }

    // --- AI target fallback (weighted exercise, no draft, no history) -------------------

    @Test fun aiTarget_usedWhenNoDraftAndNoHistory() {
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = null, ownLastLoggedWeight = null, aiTargetWeightKg = 60f
        )
        assertEquals(60f, w!!, 0f)
    }

    @Test fun aiTargetZero_doesNotPrefill_isBW() {
        // A planned bodyweight exercise (targetWeightKg == 0) with no draft/history → BW.
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = null, ownLastLoggedWeight = null, aiTargetWeightKg = 0f
        )
        assertNull(w)
    }

    // --- Scenario: the exact reported bug path -----------------------------------------

    @Test fun scenario_switchFromBarbellToFreshBodyweight_doesNotInheritBarbellWeight() {
        // Previously viewed Barbell Bench at 80kg; now switching to a fresh Pull-up (no draft,
        // no own history, BW target). The function has no way to even see the 80kg, so the
        // result is unambiguously BW.
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = null, ownLastLoggedWeight = null, aiTargetWeightKg = 0f
        )
        assertNull(w)
    }

    @Test fun scenario_returnToWeightedDip_stillShowsItsOwnAddedWeight() {
        // Returning to Weighted Dips that the user logged earlier this session at +15kg.
        val w = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = null, ownLastLoggedWeight = 15f, aiTargetWeightKg = 0f
        )
        assertEquals(15f, w!!, 0f)
    }
}
