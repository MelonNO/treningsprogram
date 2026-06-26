package com.migul.treningsprogram.domain

/**
 * Pure (Android-free) predicates for the data screens' empty-state behaviour (B07).
 *
 * B07 removes the per-field/per-chart "log more to see X" guidance copy: a field with no data is
 * simply hidden, and fields appear individually as data accumulates. To avoid a brand-new user
 * landing on a completely blank screen, each surface keeps EXACTLY ONE short top-level line, shown
 * only when the WHOLE screen has no data. These helpers decide that whole-screen-empty condition
 * so the fragments can flip a single top-level message on/off without per-field clutter.
 *
 * Kept as a plain object so it is JVM-unit-testable without Android (see B07EmptyStateTest).
 */
object DataScreenEmptyState {

    /**
     * Progress tab is entirely empty when no exercises have ever been logged. When at least one
     * exercise exists the picker is shown (so the user can choose one) and the chart/PR cards
     * appear/hide individually based on the selected exercise's data — no per-field copy.
     */
    fun isProgressEmpty(exerciseNameCount: Int): Boolean = exerciseNameCount == 0

    /**
     * The per-exercise Trends screen is entirely empty when the exercise has no recorded history
     * (no working sets in completed sessions). Otherwise the individual charts/sections appear or
     * hide on their own (e.g. the e1RM trend needs >= 2 points to draw).
     */
    fun isTrendsEmpty(historyPointCount: Int): Boolean = historyPointCount == 0
}
