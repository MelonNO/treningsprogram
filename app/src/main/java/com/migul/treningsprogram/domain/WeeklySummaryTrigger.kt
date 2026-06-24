package com.migul.treningsprogram.domain

/**
 * B1: pure, testable decision for the automatic weekly coach-summary trigger.
 *
 * Keeps the "should we generate a summary this week?" boundary in one place so it can be unit-tested
 * without Android/Room. Mirrors the plan auto-generation guards (API key present, onboarding done,
 * once-per-ISO-week) and adds the "too little data" guard from the brief: skip with a sensible no-op
 * (rather than writing a broken summary) when there are no completed sessions in the lookback.
 */
object WeeklySummaryTrigger {

    /**
     * @param lastSummaryWeek    the ISO week key stored after the last successful generation ("" if never)
     * @param currentWeekKey     the current ISO week key
     * @param hasApiKey          whether an Anthropic API key is configured
     * @param onboardingComplete whether the user finished onboarding
     * @param completedSessionCount completed sessions available in the lookback window
     */
    fun shouldGenerate(
        lastSummaryWeek: String,
        currentWeekKey: String,
        hasApiKey: Boolean,
        onboardingComplete: Boolean,
        completedSessionCount: Int
    ): Boolean {
        if (!hasApiKey) return false
        if (!onboardingComplete) return false
        if (completedSessionCount <= 0) return false        // too little data → no-op
        if (lastSummaryWeek == currentWeekKey) return false // already generated this ISO week
        return true
    }
}
