package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.dao.StrengthPoint
import kotlin.math.abs

/**
 * N3 — relative strength: estimated 1RM ÷ body weight over time ("am I getting stronger, or
 * just heavier?").
 *
 * Rules:
 *  - e1RM per session via [Epley.estimate] (the app's single formula); warm-ups are already
 *    excluded upstream (getStrengthHistory); bodyweight-only sessions (0 kg) yield no e1RM and
 *    are skipped (A-R2 — the view is for weighted lifts).
 *  - Body weight for a point is the NEAREST weigh-in to that session's date, but only within
 *    ±[MAX_WEIGHIN_GAP_DAYS]; sessions in periods with no weigh-ins get NO point — never a
 *    fabricated one.
 *  - Milestones (0.5× … 2× BW) are CHART-ONLY reference lines (A-R1 — no achievements, no XP).
 *
 * Pure + Android-free so every value is hand-checkable in JVM tests.
 */
object RelativeStrength {

    /** One chart point: the session date and its e1RM as a multiple of body weight. */
    data class Point(val dateMs: Long, val ratio: Float)

    /** A weigh-in sample (dateMs, kg) — deliberately a plain pair-shape for purity. */
    data class WeighIn(val dateMs: Long, val weightKg: Float)

    /** How far a weigh-in may sit from a session and still define its body weight. */
    const val MAX_WEIGHIN_GAP_DAYS = 14L
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Classic strength-standard ratios drawn as reference lines when they fall in range. */
    val MILESTONES = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    /**
     * The relative-strength series, chronological. Sessions without a meaningful e1RM (0 kg /
     * 0 reps) or without a near-enough weigh-in produce no point.
     */
    fun series(history: List<StrengthPoint>, weighIns: List<WeighIn>): List<Point> {
        if (weighIns.isEmpty()) return emptyList()
        val validWeighIns = weighIns.filter { it.weightKg > 0f }
        if (validWeighIns.isEmpty()) return emptyList()
        return history
            .sortedBy { it.dateMs }
            .mapNotNull { p ->
                val e1rm = Epley.estimate(p.maxWeight, p.bestReps)
                if (e1rm <= 0.0) return@mapNotNull null
                val nearest = validWeighIns.minByOrNull { abs(it.dateMs - p.dateMs) }!!
                if (abs(nearest.dateMs - p.dateMs) > MAX_WEIGHIN_GAP_DAYS * DAY_MS) return@mapNotNull null
                Point(p.dateMs, (e1rm / nearest.weightKg).toFloat())
            }
    }

    /** The readout under the chart, e.g. "Currently 1.12× body weight" — null when no points. */
    fun currentLine(points: List<Point>): String? {
        val last = points.lastOrNull() ?: return null
        return "Currently %.2f× body weight".format(java.util.Locale.US, last.ratio)
    }

    /** Milestone ratios worth drawing for this series: those within (or just above) its range. */
    fun milestonesInRange(points: List<Point>): List<Float> {
        if (points.isEmpty()) return emptyList()
        val min = points.minOf { it.ratio }
        val max = points.maxOf { it.ratio }
        return MILESTONES.filter { it in min..max }
    }
}
