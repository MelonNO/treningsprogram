package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.dao.MuscleVolume
import com.migul.treningsprogram.data.db.dao.WeekVolume

/**
 * Pure (Android-free, Room-free) data-shaping for the aggregate "overview" graphs added to the
 * Recap area (UX1). Each function turns data already exposed by HistoryViewModel into the small
 * point/row shapes the UI renders. All inputs are working-sets-only (warm-ups already excluded by
 * the upstream DAO queries — `getWeeklyVolume`, `getMuscleGroupVolume`); this object never
 * re-introduces warm-ups and never queries the DB.
 *
 * Kept as a plain object so it is JVM-unit-testable without Android (see UX1RecapGraphsTest).
 *
 * Week bucketing convention: weeks are aligned to the same epoch grid the DAO already uses for
 * per-exercise weekly volume — `weekStart = epochMs / WEEK_MS * WEEK_MS` (UTC-midnight-aligned
 * 7-day buckets). We reuse that grid here (rather than locale week-of-year) so the totals line up
 * exactly with the per-exercise weekly volume the rest of the app shows.
 */
object RecapGraphs {

    const val WEEK_MS: Long = 604_800_000L  // 7 * 86_400_000

    /** One point on a time-bucketed graph: the week's start (epoch ms) and a value for that week. */
    data class WeekPoint(val weekStartMs: Long, val value: Float)

    /** One categorical row for the per-muscle distribution (a labelled bar). */
    data class MuscleRow(val muscleGroup: String, val sets: Int)

    /**
     * Total working-set count per week across ALL exercises, chronological.
     *
     * Input is the per-exercise weekly volume lists (one list per exercise, each already bucketed
     * to the DAO's week grid). We merge them by `weekStart`, summing `totalSets`. Deriving the
     * total this way reuses the existing `getWeeklyVolume(name)` query instead of adding a new DAO
     * query (UX1 constraint). Empty input → empty list (the chart shows its own empty state).
     */
    fun weeklyVolumePoints(perExercise: List<List<WeekVolume>>): List<WeekPoint> {
        val byWeek = sortedMapOf<Long, Int>()
        for (list in perExercise) {
            for (wv in list) {
                byWeek[wv.weekStart] = (byWeek[wv.weekStart] ?: 0) + wv.totalSets
            }
        }
        return byWeek.map { (week, sets) -> WeekPoint(week, sets.toFloat()) }
    }

    /**
     * Sessions-per-week over time, chronological — training-frequency trend.
     *
     * Input is the list of distinct training-DAY epochs (days since the unix epoch, as returned by
     * `getTrainingDayEpochs()`). Each day counts once; days are bucketed to the same week grid as
     * the volume graph so the two time charts share an x-axis framing. Empty input → empty list.
     */
    fun weeklyFrequencyPoints(trainingDayEpochs: List<Long>): List<WeekPoint> {
        if (trainingDayEpochs.isEmpty()) return emptyList()
        val byWeek = sortedMapOf<Long, Int>()
        for (dayEpoch in trainingDayEpochs.distinct()) {
            val weekStart = (dayEpoch * 86_400_000L) / WEEK_MS * WEEK_MS
            byWeek[weekStart] = (byWeek[weekStart] ?: 0) + 1
        }
        return byWeek.map { (week, count) -> WeekPoint(week, count.toFloat()) }
    }

    /**
     * Per-muscle-group working-set distribution, descending by set count.
     *
     * Pass-through/normalisation of `getMuscleGroupVolume()` into a UI row shape (drops blank
     * group names defensively; the DAO already excludes them, but this keeps the function total).
     * Rendered as a labelled bar list rather than a line chart because the data is categorical, not
     * a time series. Empty input → empty list.
     */
    fun muscleRows(muscleVolume: List<MuscleVolume>): List<MuscleRow> =
        muscleVolume
            .filter { it.muscleGroup.isNotBlank() && it.totalSets > 0 }
            .sortedByDescending { it.totalSets }
            .map { MuscleRow(it.muscleGroup, it.totalSets) }
}
