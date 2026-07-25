package com.migul.treningsprogram.domain

/**
 * Item 06: pure decision logic for the weekly automatic program generation, extracted from the old
 * MainActivity.checkAndAutoGenerateWeeklyPlan() so the trigger/guard semantics are unit-testable
 * and shared verbatim by every trigger path (app open/resume AND the scheduled background run).
 *
 * Guard order and meaning are IDENTICAL to the previous inline implementation:
 *  1. week already handled (generated, manually planned, or written off after the failure cap)
 *  2. no API key
 *  3. onboarding incomplete
 *  4. active program frozen (skip WITHOUT marking the week done, so unfreezing re-enables auto-gen)
 *  5. week already has plan rows → mark done, never regenerate
 *  6. otherwise → generate
 */
object AutoGenPolicy {

    /** Failed attempts an automatic weekly generation may burn before the week is written off. */
    const val MAX_AUTO_GEN_TRIES = 3

    enum class Decision {
        SKIP_ALREADY_DONE,
        SKIP_NO_API_KEY,
        SKIP_ONBOARDING_INCOMPLETE,
        SKIP_FROZEN,
        MARK_DONE_EXISTING_PLAN,
        GENERATE,
    }

    fun evaluate(
        weekKey: String,
        lastAutoGenerateWeek: String,
        apiKeyBlank: Boolean,
        onboardingComplete: Boolean,
        programFrozen: Boolean,
        weekHasPlanRows: Boolean,
    ): Decision = when {
        lastAutoGenerateWeek == weekKey -> Decision.SKIP_ALREADY_DONE
        apiKeyBlank -> Decision.SKIP_NO_API_KEY
        !onboardingComplete -> Decision.SKIP_ONBOARDING_INCOMPLETE
        programFrozen -> Decision.SKIP_FROZEN
        weekHasPlanRows -> Decision.MARK_DONE_EXISTING_PLAN
        else -> Decision.GENERATE
    }

    /**
     * State update after a failed generation attempt. Mirrors the old inline logic exactly:
     * the counter restarts at 1 in a new week, increments within the same week, and once it
     * reaches [MAX_AUTO_GEN_TRIES] the week is written off ([weekWrittenOff] → caller stamps
     * lastAutoGenerateWeek so guard 1 blocks further attempts until next week).
     */
    data class FailureUpdate(val failWeek: String, val failCount: Int, val weekWrittenOff: Boolean)

    fun onFailure(
        weekKey: String,
        prevFailWeek: String,
        prevFailCount: Int,
        maxTries: Int = MAX_AUTO_GEN_TRIES,
    ): FailureUpdate {
        val failures = 1 + if (prevFailWeek == weekKey) prevFailCount else 0
        return FailureUpdate(weekKey, failures, failures >= maxTries)
    }
}
