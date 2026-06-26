package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.dao.ExerciseSessionCount

/**
 * Pure (Android-free, Room-free) ordering for the Progress-tab exercise picker (B03).
 *
 * The picker used to be alphabetical; the user wants their most-trained exercises near the top.
 * "Most trained" here means the number of DISTINCT sessions an exercise appears in (not total
 * set count) — matching the per-session data the Progress screen already plots.
 *
 * Ordering rule: session count DESCENDING, ties broken ALPHABETICALLY (case-insensitive, then
 * case-sensitive as a final stable tie-break so equal names keep a deterministic order).
 *
 * This ordering is computed once and then PRESERVED while the user types to filter — see
 * [filterPreservingOrder] — so matches stay most-sessions-first rather than re-alphabetizing.
 *
 * Kept as a plain object so it is JVM-unit-testable without Android (see B03ExercisePickerSortTest).
 */
object ExercisePickerSort {

    /**
     * Returns the exercise names ordered by session count (desc), alpha tie-break.
     * Names are de-duplicated defensively (first occurrence wins after sorting).
     */
    fun order(counts: List<ExerciseSessionCount>): List<String> =
        counts
            .sortedWith(
                compareByDescending<ExerciseSessionCount> { it.sessionCount }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.exerciseName }
                    .thenBy { it.exerciseName }
            )
            .map { it.exerciseName }
            .distinct()

    /**
     * Filters [ordered] (already in most-sessions-first order) to the names that match [query],
     * PRESERVING the input order. A blank query returns the full list unchanged. Matching is a
     * case-insensitive substring match — the same lenient match the picker's adapter uses — so
     * the visible list never re-alphabetizes as the user types.
     */
    fun filterPreservingOrder(ordered: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return ordered
        return ordered.filter { it.contains(q, ignoreCase = true) }
    }
}
