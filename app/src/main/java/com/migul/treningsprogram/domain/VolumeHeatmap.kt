package com.migul.treningsprogram.domain

import java.util.Calendar

/**
 * F5 — pure grid math for the weekly per-muscle volume heatmap (History → Stats).
 *
 * Buckets completed working sets into Monday-based local weeks (unlike the SQL
 * epoch-week floor, which lands on Thursdays) and returns a muscle × week matrix
 * of set counts. Muscles are ordered by total volume, capped at [maxMuscles] so
 * the card stays readable. Plain object → JVM-unit-testable without Android.
 */
object VolumeHeatmap {

    data class Grid(
        /** Row labels, highest total volume first. */
        val muscles: List<String>,
        /** Column week-start (Monday 00:00 local) timestamps, oldest first. */
        val weekStarts: List<Long>,
        /** sets[row][col] = working sets for muscles[row] in weekStarts[col]. */
        val sets: List<List<Int>>,
        /** Highest single cell — drives the color ramp. */
        val maxSets: Int,
    )

    /** Monday 00:00 (local) of the week containing [ms]. */
    fun mondayOf(ms: Long): Long = Calendar.getInstance().run {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        // roll back to Monday (Calendar.DAY_OF_WEEK: SUN=1 … SAT=7)
        val back = (get(Calendar.DAY_OF_WEEK) + 5) % 7
        add(Calendar.DAY_OF_MONTH, -back)
        timeInMillis
    }

    /**
     * @param setDays  (muscleGroup, sessionDateMs) — one entry per completed working set.
     * @param weeks    number of week columns, ending at the week containing [nowMs].
     */
    fun build(
        setDays: List<Pair<String, Long>>,
        weeks: Int,
        nowMs: Long = System.currentTimeMillis(),
        maxMuscles: Int = 8,
    ): Grid {
        val thisMonday = mondayOf(nowMs)
        val weekStarts = (weeks - 1 downTo 0).map { back ->
            Calendar.getInstance().run {
                timeInMillis = thisMonday
                add(Calendar.WEEK_OF_YEAR, -back)
                timeInMillis
            }
        }
        val colFor = weekStarts.withIndex().associate { (i, w) -> w to i }

        val counts = HashMap<String, IntArray>()
        for ((muscle, dateMs) in setDays) {
            val col = colFor[mondayOf(dateMs)] ?: continue
            counts.getOrPut(muscle) { IntArray(weeks) }[col]++
        }

        val muscles = counts.entries
            .sortedByDescending { it.value.sum() }
            .take(maxMuscles)
            .map { it.key }

        val sets = muscles.map { counts.getValue(it).toList() }
        return Grid(
            muscles = muscles,
            weekStarts = weekStarts,
            sets = sets,
            maxSets = sets.flatten().maxOrNull() ?: 0,
        )
    }
}
