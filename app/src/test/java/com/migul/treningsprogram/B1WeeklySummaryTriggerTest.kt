package com.migul.treningsprogram

import com.migul.treningsprogram.domain.WeeklySummaryTrigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature B1: automatic weekly coach summary.
 *
 * Locks the PURE once-per-week trigger boundary + the empty-data guard in [WeeklySummaryTrigger],
 * so the automatic weekly summary fires EXACTLY once per ISO week and never writes a broken summary
 * when there is too little data. Pure JVM (no Room/Android), matching the repo's test style.
 */
class B1WeeklySummaryTriggerTest {

    private val thisWeek = "2026-W26"
    private val lastWeek = "2026-W25"

    @Test fun generates_whenNewWeek_apiKey_onboarded_andHasData() {
        assertTrue(
            WeeklySummaryTrigger.shouldGenerate(
                lastSummaryWeek = lastWeek,
                currentWeekKey = thisWeek,
                hasApiKey = true,
                onboardingComplete = true,
                completedSessionCount = 3
            )
        )
    }

    @Test fun neverGenerates_twiceInTheSameIsoWeek() {
        // The exact once-per-week boundary: already generated THIS week ⇒ no second generation.
        assertFalse(
            WeeklySummaryTrigger.shouldGenerate(
                lastSummaryWeek = thisWeek,
                currentWeekKey = thisWeek,
                hasApiKey = true,
                onboardingComplete = true,
                completedSessionCount = 5
            )
        )
    }

    @Test fun skips_whenNoApiKey() {
        assertFalse(
            WeeklySummaryTrigger.shouldGenerate(
                lastSummaryWeek = lastWeek,
                currentWeekKey = thisWeek,
                hasApiKey = false,
                onboardingComplete = true,
                completedSessionCount = 3
            )
        )
    }

    @Test fun skips_whenOnboardingNotComplete() {
        assertFalse(
            WeeklySummaryTrigger.shouldGenerate(
                lastSummaryWeek = lastWeek,
                currentWeekKey = thisWeek,
                hasApiKey = true,
                onboardingComplete = false,
                completedSessionCount = 3
            )
        )
    }

    @Test fun skips_whenTooLittleData_zeroCompletedSessions() {
        // Empty-data guard: no completed sessions ⇒ no-op (no broken summary written).
        assertFalse(
            WeeklySummaryTrigger.shouldGenerate(
                lastSummaryWeek = lastWeek,
                currentWeekKey = thisWeek,
                hasApiKey = true,
                onboardingComplete = true,
                completedSessionCount = 0
            )
        )
    }

    @Test fun generates_firstEver_whenLastSummaryWeekIsBlank() {
        // Fresh install that DOES have logged sessions: lastSummaryWeek == "" must not block.
        assertTrue(
            WeeklySummaryTrigger.shouldGenerate(
                lastSummaryWeek = "",
                currentWeekKey = thisWeek,
                hasApiKey = true,
                onboardingComplete = true,
                completedSessionCount = 1
            )
        )
    }
}
