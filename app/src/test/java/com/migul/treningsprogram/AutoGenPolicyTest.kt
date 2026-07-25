package com.migul.treningsprogram

import com.migul.treningsprogram.domain.AutoGenPolicy
import com.migul.treningsprogram.domain.AutoGenPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 06: the weekly auto-generation trigger/guard evaluation, extracted from MainActivity into
 * [AutoGenPolicy]. These tests pin the guard semantics the briefs require preserved: each guard
 * blocks, an existing-plan week is never regenerated, and the failure cap writes the week off
 * after MAX tries (resetting across weeks).
 */
class AutoGenPolicyTest {

    private val week = "wk-1753657200000"
    private val otherWeek = "wk-1753052400000"

    private fun evaluate(
        lastDone: String = otherWeek,
        apiKeyBlank: Boolean = false,
        onboardingComplete: Boolean = true,
        frozen: Boolean = false,
        hasRows: Boolean = false,
    ) = AutoGenPolicy.evaluate(
        weekKey = week,
        lastAutoGenerateWeek = lastDone,
        apiKeyBlank = apiKeyBlank,
        onboardingComplete = onboardingComplete,
        programFrozen = frozen,
        weekHasPlanRows = hasRows,
    )

    // ── guard matrix ────────────────────────────────────────────────────────────────────────────

    @Test fun newWeek_allGuardsClear_generates() {
        assertEquals(Decision.GENERATE, evaluate())
    }

    @Test fun weekAlreadyHandled_skips() {
        assertEquals(Decision.SKIP_ALREADY_DONE, evaluate(lastDone = week))
    }

    @Test fun blankApiKey_skips() {
        assertEquals(Decision.SKIP_NO_API_KEY, evaluate(apiKeyBlank = true))
    }

    @Test fun onboardingIncomplete_skips() {
        assertEquals(Decision.SKIP_ONBOARDING_INCOMPLETE, evaluate(onboardingComplete = false))
    }

    @Test fun frozenProgram_skips() {
        assertEquals(Decision.SKIP_FROZEN, evaluate(frozen = true))
    }

    @Test fun weekWithExistingPlanRows_isMarkedDone_neverRegenerated() {
        assertEquals(Decision.MARK_DONE_EXISTING_PLAN, evaluate(hasRows = true))
    }

    // ── guard precedence (same order as the old inline MainActivity block) ─────────────────────

    @Test fun handledWeek_winsOverEveryOtherGuard() {
        assertEquals(
            Decision.SKIP_ALREADY_DONE,
            evaluate(lastDone = week, apiKeyBlank = true, onboardingComplete = false, frozen = true, hasRows = true)
        )
    }

    @Test fun frozen_winsOverExistingRows_soFrozenWeekIsNotMarkedDone() {
        // Frozen skips WITHOUT stamping the week, so unfreezing later re-enables auto-gen.
        assertEquals(Decision.SKIP_FROZEN, evaluate(frozen = true, hasRows = true))
    }

    @Test fun blankKey_beforeOnboarding() {
        assertEquals(Decision.SKIP_NO_API_KEY, evaluate(apiKeyBlank = true, onboardingComplete = false))
    }

    // ── failure cap ─────────────────────────────────────────────────────────────────────────────

    @Test fun firstFailureOfWeek_countsOne_notWrittenOff() {
        val u = AutoGenPolicy.onFailure(week, prevFailWeek = otherWeek, prevFailCount = 5)
        assertEquals(week, u.failWeek)
        assertEquals(1, u.failCount)   // stale other-week count is discarded
        assertFalse(u.weekWrittenOff)
    }

    @Test fun secondFailureSameWeek_countsTwo_notWrittenOff() {
        val u = AutoGenPolicy.onFailure(week, prevFailWeek = week, prevFailCount = 1)
        assertEquals(2, u.failCount)
        assertFalse(u.weekWrittenOff)
    }

    @Test fun thirdFailureSameWeek_hitsCap_weekWrittenOff() {
        val u = AutoGenPolicy.onFailure(week, prevFailWeek = week, prevFailCount = 2)
        assertEquals(3, u.failCount)
        assertTrue(u.weekWrittenOff)
    }

    @Test fun capMatchesLegacyConstant() {
        assertEquals(3, AutoGenPolicy.MAX_AUTO_GEN_TRIES)
    }

    @Test fun newWeek_resetsTheCounter() {
        // Capped out last week; a failure in the fresh week starts back at 1.
        val u = AutoGenPolicy.onFailure(week, prevFailWeek = otherWeek, prevFailCount = 3)
        assertEquals(1, u.failCount)
        assertFalse(u.weekWrittenOff)
    }

    @Test fun writtenOffWeek_blocksFurtherAttempts_viaGuardOne() {
        // The write-off is expressed by stamping lastAutoGenerateWeek — guard 1 then blocks.
        val u = AutoGenPolicy.onFailure(week, prevFailWeek = week, prevFailCount = 2)
        assertTrue(u.weekWrittenOff)
        assertEquals(Decision.SKIP_ALREADY_DONE, evaluate(lastDone = week))
    }
}
