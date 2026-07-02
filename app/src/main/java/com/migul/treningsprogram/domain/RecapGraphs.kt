package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.dao.MuscleVolume

/**
 * Pure (Android-free, Room-free) data-shaping for the aggregate "overview" graphs added to the
 * Recap area (UX1). Each function turns data already exposed by HistoryViewModel into the small
 * point/row shapes the UI renders. All inputs are working-sets-only (warm-ups already excluded by
 * the upstream DAO queries); this object never re-introduces warm-ups and never queries the DB.
 *
 * Kept as a plain object so it is JVM-unit-testable without Android (see UX1RecapGraphsTest).
 *
 * Week bucketing convention: MONDAY-based weeks over LOGICAL epoch-days (Item 7 day boundary),
 * matching the Stats volume heatmap — the old grid (`epochMs / WEEK_MS * WEEK_MS`) floored to
 * THURSDAY-based UTC weeks (the unix epoch was a Thursday), which disagreed with every other
 * Monday-anchored week in the app.
 */
object RecapGraphs {

    const val WEEK_MS: Long = 604_800_000L  // 7 * 86_400_000
    private const val DAY_MS: Long = 86_400_000L

    /** One point on a time-bucketed graph: the week's start (epoch ms) and a value for that week. */
    data class WeekPoint(val weekStartMs: Long, val value: Float)

    /** One categorical row for the per-muscle distribution (a labelled bar). */
    data class MuscleRow(val muscleGroup: String, val sets: Int)

    /**
     * The Monday of the week containing [dayEpoch] (days since the unix epoch).
     * Epoch day 0 was a Thursday, so `(dayEpoch + 3) % 7 == 0` ⇔ Monday.
     */
    fun mondayOfEpochDay(dayEpoch: Long): Long = dayEpoch - (((dayEpoch + 3) % 7 + 7) % 7)

    /**
     * Total working-set count per week, chronological.
     *
     * Input is ONE logical epoch-day per working set (duplicates expected — several sets per day);
     * each entry counts toward its Monday-based week. Empty input → empty list (the chart shows
     * its own empty state).
     */
    fun weeklyVolumePoints(setDayEpochs: List<Long>): List<WeekPoint> =
        bucketByWeek(setDayEpochs)

    /**
     * Sessions-per-week over time, chronological — training-frequency trend.
     *
     * Input is the list of distinct training-DAY epochs (days since the unix epoch, as returned by
     * `getTrainingDayEpochs()`). Each day counts once; days are bucketed to the same Monday grid as
     * the volume graph so the two time charts share an x-axis framing. Empty input → empty list.
     */
    fun weeklyFrequencyPoints(trainingDayEpochs: List<Long>): List<WeekPoint> =
        bucketByWeek(trainingDayEpochs.distinct())

    private fun bucketByWeek(dayEpochs: List<Long>): List<WeekPoint> {
        if (dayEpochs.isEmpty()) return emptyList()
        val byWeek = sortedMapOf<Long, Int>()
        for (dayEpoch in dayEpochs) {
            val weekStartMs = mondayOfEpochDay(dayEpoch) * DAY_MS
            byWeek[weekStartMs] = (byWeek[weekStartMs] ?: 0) + 1
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
