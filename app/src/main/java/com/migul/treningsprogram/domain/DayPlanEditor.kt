package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.PlannedExercise

/**
 * E1 — manual program editing: the PURE (Android-free, Room-free) list transforms behind
 * add / delete / reorder of a single day's planned exercises.
 *
 * The Program screen lets the user hand-edit a day's plan. Persistence goes through
 * `WorkoutRepository.saveDayPlan(weekStart, day, list)`, which RE-KEYS `orderInDay` by the
 * ROW VALUES it is handed — so every list we pass it must already carry `orderInDay = index`.
 * That re-keying is the only non-trivial logic here, and it is exactly what these functions
 * guarantee. Keeping it pure means the ordering invariant can be unit-tested without a DB.
 *
 * Field edits (sets/reps/weight/notes) are a plain `copy(...)` and live in the ViewModel; only
 * the list-shape transforms that produce a deterministic `orderInDay` sequence live here.
 */
object DayPlanEditor {

    /** Stamp `orderInDay = index` onto every row so ordering is deterministic for saveDayPlan. */
    fun reindex(list: List<PlannedExercise>): List<PlannedExercise> =
        list.mapIndexed { index, ex -> if (ex.orderInDay == index) ex else ex.copy(orderInDay = index) }

    /** Remove [target] (matched by id) from [list] and re-index. */
    fun remove(list: List<PlannedExercise>, target: PlannedExercise): List<PlannedExercise> =
        reindex(list.filterNot { it.id == target.id })

    /** Append [newExercise] to the end of [list] and re-index (so its orderInDay = last index). */
    fun add(list: List<PlannedExercise>, newExercise: PlannedExercise): List<PlannedExercise> =
        reindex(list + newExercise)

    /**
     * Move [target] (matched by id) one slot toward the start ([up] = true) or end ([up] = false),
     * then re-index. A no-op (returns a re-indexed copy of the original order) when the target is
     * already at the relevant edge or is not present.
     */
    fun move(list: List<PlannedExercise>, target: PlannedExercise, up: Boolean): List<PlannedExercise> {
        val from = list.indexOfFirst { it.id == target.id }
        if (from < 0) return reindex(list)
        val to = if (up) from - 1 else from + 1
        if (to !in list.indices) return reindex(list)
        val mutable = list.toMutableList()
        mutable[from] = list[to]
        mutable[to] = list[from]
        return reindex(mutable)
    }
}
