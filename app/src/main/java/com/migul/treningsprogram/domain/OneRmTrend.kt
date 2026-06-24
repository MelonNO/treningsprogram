package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.dao.StrengthPoint

/**
 * Pure (Android-free, Room-free) derivation of the per-exercise estimated-1RM trend and the
 * PR timeline shown on the Recap & Trends screen (feature C1).
 *
 * All e1RM math goes through [Epley.estimate] so the whole app shares one formula.
 * Warm-up sets are already excluded upstream by `WorkoutSetDao.getStrengthHistory`, so the
 * inputs here are working-set summaries (one [StrengthPoint] per session); this object does
 * not re-filter warm-ups.
 *
 * Kept as a plain object on `List<StrengthPoint>` so it is JVM-unit-testable without Android.
 */
object OneRmTrend {

    /** One point of the estimated-1RM-over-time trend. */
    data class TrendPoint(val dateMs: Long, val e1rm: Double)

    /** A session that set a new estimated-1RM personal record. */
    data class PrRecord(
        val dateMs: Long,
        val weightKg: Float,
        val reps: Int,
        val e1rm: Double
    )

    /**
     * Estimated-1RM trend over time, one point per session, in chronological order.
     *
     * Sessions whose e1RM is not meaningful (non-positive weight or reps → [Epley.estimate]
     * returns 0.0) are dropped so the chart never plots a flat-zero point.
     */
    fun trendPoints(history: List<StrengthPoint>): List<TrendPoint> =
        history
            .asSequence()
            .sortedBy { it.dateMs }
            .map { TrendPoint(it.dateMs, Epley.estimate(it.maxWeight, it.bestReps)) }
            .filter { it.e1rm > 0.0 }
            .toList()

    /**
     * Personal-record timeline tracked by estimated 1RM, in chronological order.
     *
     * Walking sessions oldest→newest, a session is a new PR when its e1RM strictly exceeds the
     * best e1RM of all prior sessions. Because e1RM rises with either more weight or more reps
     * (Epley), this captures both a weight PR and a rep-PR at the same weight; a regression
     * (lighter/fewer reps → lower e1RM) does not register. An exercise with no qualifying
     * sessions yields an empty list (the screen's empty state).
     */
    fun prTimeline(history: List<StrengthPoint>): List<PrRecord> {
        val result = mutableListOf<PrRecord>()
        var best = 0.0
        for (p in history.sortedBy { it.dateMs }) {
            val e1rm = Epley.estimate(p.maxWeight, p.bestReps)
            if (e1rm > best) {
                best = e1rm
                result.add(PrRecord(p.dateMs, p.maxWeight, p.bestReps, e1rm))
            }
        }
        return result
    }
}
