package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.BodyMetric

/**
 * Body-progress batch 2026-08-04 (brief 02) — turns the two stored series (body weight and girths)
 * plus the profile into the five chart series the Body tab draws.
 *
 * Pure object so every rule below is JVM-unit-tested rather than trusted to a fragment:
 *  - each series is INDEPENDENT and sparse — nothing is interpolated or carried across fields, so
 *    a weight logged daily and a waist logged monthly each plot only their own real points (the
 *    brief's presentation note);
 *  - the body-fat series only gets a point for an entry that actually qualifies (waist + neck, plus
 *    hip for women — A4) AND only once the profile has height + sex (A5). Missing profile ⇒ empty
 *    body-fat series, never a fabricated number;
 *  - the hip series is only ever populated for a female profile (decision 2).
 *
 * The time window is applied through the shared [DateRangeFilter] so it evaluates logical days
 * exactly like the History and Progress tabs.
 */
object BodyProgressCharts {

    data class Point(val dateMs: Long, val value: Float)

    data class Series(
        val weight: List<Point> = emptyList(),
        val bodyFat: List<Point> = emptyList(),
        val waist: List<Point> = emptyList(),
        val neck: List<Point> = emptyList(),
        val hip: List<Point> = emptyList()
    ) {
        val isEmpty: Boolean
            get() = weight.isEmpty() && bodyFat.isEmpty() && waist.isEmpty() &&
                neck.isEmpty() && hip.isEmpty()
    }

    /**
     * One row of the "recent entries" list inside the logging section — the only place a mistyped
     * measurement can be removed again. [measurementId] / [metricId] are the two underlying rows a
     * single user-facing entry may span (either can be absent).
     */
    data class EntryRow(
        val dateMs: Long,
        val weightKg: Float?,
        val waistCm: Float?,
        val neckCm: Float?,
        val hipCm: Float?,
        val bodyFatPercent: Float?,
        val measurementId: Long?,
        val metricId: Long?
    ) {
        /** This row has a body-weight row behind it (from the Body tab OR the Home quick-add). */
        val hasWeight: Boolean get() = measurementId != null

        /** This row has a girth row behind it. */
        val hasMetrics: Boolean get() = metricId != null

        /**
         * A single save that wrote to BOTH tables. Deleting one of these is the case that needs a
         * choice rather than an all-or-nothing confirm — see [DeletePart].
         */
        val isCombined: Boolean get() = hasWeight && hasMetrics
    }

    /**
     * Which half of an [EntryRow] a delete should remove.
     *
     * A combined entry spans two tables, so "delete" is ambiguous: mistyping the waist should not
     * cost the user the weight they logged in the same breath. [ALL] is offered for the common case,
     * the two partial options for the precise one. Single-kind rows only ever use [ALL].
     */
    enum class DeletePart {
        ALL, WEIGHT_ONLY, METRICS_ONLY;

        /** True when this delete should remove the `body_measurements` (weight) row. */
        val removesWeight: Boolean get() = this != METRICS_ONLY

        /** True when this delete should remove the `body_metrics` (girth) row. */
        val removesMetrics: Boolean get() = this != WEIGHT_ONLY
    }

    /**
     * The five series for [range], newest-last (charts draw left-to-right in time order).
     *
     * [sex] and [heightCm] come from the profile; both are required for any body-fat point.
     */
    fun build(
        measurements: List<BodyMeasurement>,
        metrics: List<BodyMetric>,
        range: DateRangeFilter.Range?,
        sex: String?,
        heightCm: Float?
    ): Series {
        val windowedWeights = DateRangeFilter.filter(measurements, range) { it.dateMs }
            .sortedBy { it.dateMs }
        val windowedMetrics = DateRangeFilter.filter(metrics, range) { it.dateMs }
            .sortedBy { it.dateMs }

        val female = BodyComposition.needsHip(sex)

        return Series(
            weight = windowedWeights.map { Point(it.dateMs, it.weightKg) },
            waist = windowedMetrics.mapNotNull { m -> m.waistCm?.let { Point(m.dateMs, it) } },
            neck = windowedMetrics.mapNotNull { m -> m.neckCm?.let { Point(m.dateMs, it) } },
            // Men never see a hip field, so they can never have hip data to plot either.
            hip = if (!female) emptyList()
            else windowedMetrics.mapNotNull { m -> m.hipCm?.let { Point(m.dateMs, it) } },
            bodyFat = windowedMetrics.mapNotNull { m ->
                BodyComposition.estimate(sex, heightCm, m.waistCm, m.neckCm, m.hipCm)
                    ?.let { Point(m.dateMs, it) }
            }
        )
    }

    /** How many entries the collapsed list shows before the user asks to see everything. */
    const val DEFAULT_ENTRY_LIMIT = 10

    /** [recentEntries] limit meaning "the user tapped Show all" — no cap. */
    const val NO_LIMIT = Int.MAX_VALUE

    /**
     * The most recent [limit] logged entries, newest first, for the deletable list in the logging
     * section. BOTH kinds appear here: body-weight rows (whether they came from this tab or the
     * Home quick-add) and girth rows. Entries saved together are paired into ONE row by their
     * identical timestamp, so a user who logged weight + waist + neck in one go sees one line and
     * does not have to hunt in two places; anything unpaired shows on its own.
     *
     * Pass [NO_LIMIT] to reach the entire history — deleting an entry from months ago is exactly
     * what the default cap of [DEFAULT_ENTRY_LIMIT] would otherwise make impossible.
     *
     * Deliberately NOT windowed by the selected chart range: this is an editing aid, and hiding the
     * entry you just mistyped because it fell outside a 1-month view would be actively unhelpful.
     */
    fun recentEntries(
        measurements: List<BodyMeasurement>,
        metrics: List<BodyMetric>,
        sex: String?,
        heightCm: Float?,
        limit: Int = DEFAULT_ENTRY_LIMIT
    ): List<EntryRow> {
        val metricsByDate = metrics.associateBy { it.dateMs }
        val pairedMetricDates = mutableSetOf<Long>()

        val fromWeights = measurements.map { w ->
            val m = metricsByDate[w.dateMs]
            if (m != null) pairedMetricDates.add(m.dateMs)
            EntryRow(
                dateMs = w.dateMs,
                weightKg = w.weightKg,
                waistCm = m?.waistCm,
                neckCm = m?.neckCm,
                hipCm = if (BodyComposition.needsHip(sex)) m?.hipCm else null,
                bodyFatPercent = m?.let {
                    BodyComposition.estimate(sex, heightCm, it.waistCm, it.neckCm, it.hipCm)
                },
                measurementId = w.id,
                metricId = m?.id
            )
        }

        val fromMetrics = metrics.filter { it.dateMs !in pairedMetricDates }.map { m ->
            EntryRow(
                dateMs = m.dateMs,
                weightKg = null,
                waistCm = m.waistCm,
                neckCm = m.neckCm,
                hipCm = if (BodyComposition.needsHip(sex)) m.hipCm else null,
                bodyFatPercent =
                    BodyComposition.estimate(sex, heightCm, m.waistCm, m.neckCm, m.hipCm),
                measurementId = null,
                metricId = m.id
            )
        }

        return (fromWeights + fromMetrics).sortedByDescending { it.dateMs }.take(limit)
    }
}
