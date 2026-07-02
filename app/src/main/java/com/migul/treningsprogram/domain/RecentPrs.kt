package com.migul.treningsprogram.domain

import java.time.ZoneId

/**
 * Stage-3 item 4 — Profile shows only the PRs earned in the rolling last-7-logical-days window.
 *
 * Pure derivation so it is unit-testable off-device. The PR rule mirrors
 * GamificationRepository.isWeightPr via [BeatTarget.isPr]: a PR needs a REAL prior best,
 * strictly beaten; a first-ever performance only establishes the baseline. Warm-ups and
 * zero-weight sets must already be excluded by the caller's query.
 */
object RecentPrs {

    /** Rolling window length in logical days (today plus the six days before it). */
    const val WINDOW_DAYS = 7

    /** One weighted working set from a completed session. */
    data class SetSample(val exerciseName: String, val weightKg: Float, val dateMs: Long)

    /** A PR earned inside the window: the exercise's new record weight and when it landed. */
    data class RecentPr(val exerciseName: String, val weightKg: Float, val dateMs: Long)

    /**
     * Computes the PRs earned within the last [WINDOW_DAYS] logical days.
     *
     * @param sets weighted working sets from completed sessions, fetched from a little BEFORE the
     *   window start (a generous fetch cutoff is fine): samples that fall before the window only
     *   extend the running baseline and are never PR-eligible themselves.
     * @param baselineMaxByExercise per-exercise max working weight from everything before the
     *   fetch cutoff (the historical baseline a window PR must strictly beat).
     * @param todayEpochDay the current logical epoch day (DayBoundary.todayEpochDay()).
     *
     * Returns at most one entry per exercise — the latest PR in the window, which is necessarily
     * also the heaviest (every later PR must beat the previous one) — newest first.
     */
    fun compute(
        sets: List<SetSample>,
        baselineMaxByExercise: Map<String, Float>,
        todayEpochDay: Long,
        cutoffHour: Int = DayBoundary.cutoffHour,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<RecentPr> {
        val windowStartDay = todayEpochDay - (WINDOW_DAYS - 1)
        return sets.groupBy { it.exerciseName }
            .mapNotNull { (name, samples) ->
                var runningMax: Float? = baselineMaxByExercise[name]
                var latestPr: RecentPr? = null
                samples.sortedBy { it.dateMs }.forEach { s ->
                    val day = DayBoundary.logicalEpochDay(s.dateMs, cutoffHour, zone)
                    if (day in windowStartDay..todayEpochDay && BeatTarget.isPr(s.weightKg, runningMax)) {
                        latestPr = RecentPr(name, s.weightKg, s.dateMs)
                    }
                    runningMax = maxOf(runningMax ?: s.weightKg, s.weightKg)
                }
                latestPr
            }
            .sortedByDescending { it.dateMs }
    }
}
