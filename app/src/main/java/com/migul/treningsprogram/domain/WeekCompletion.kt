package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.PlannedExercise

/**
 * THE single definition of "how complete is a planned week", extracted from the Program tab's
 * week bar so every consumer (week bar, R4 Perfect Week award, backup-merge replay) agrees:
 *
 *   a planned DAY is complete when at least one of its planned exercises is logged;
 *   the week's progress is (complete days / days that have ≥1 planned exercise).
 *
 * Pure so the Perfect Week award and its StatsRecomputer replay are unit-testable and provably
 * identical.
 */
object WeekCompletion {

    /** (complete days, total planned days) — the Program-tab week-bar numbers. */
    fun dayCounts(weekPlan: List<PlannedExercise>): Pair<Int, Int> {
        val byDay = weekPlan.groupBy { it.dayOfWeek }
        val workoutDays = byDay.values.filter { it.isNotEmpty() }
        val doneDays = workoutDays.count { exercises -> exercises.any { it.isLogged } }
        return doneDays to workoutDays.size
    }

    /**
     * A "Perfect Week": every planned training day of the week is complete. A week with no
     * planned days is never perfect (A-C2 — no plan ⇒ no Perfect Week).
     */
    fun isPerfect(weekPlan: List<PlannedExercise>): Boolean {
        val (done, total) = dayCounts(weekPlan)
        return total > 0 && done == total
    }
}
