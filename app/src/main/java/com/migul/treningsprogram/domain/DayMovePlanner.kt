package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.PlannedExercise

/**
 * P2: pure helper for the "do another day's workout today" MOVE. When the user performs another
 * day's planned workout as today's session and completes it, the chosen day's plan rows move into
 * today's slot: exercises the user actually performed (name in [performedNames]) are marked logged,
 * the rest stay non-logged. orderInDay is renumbered by position and the week [rationale] is stamped
 * on every moved row (mirroring the repository's save paths). No Android/DB dependency, so the move
 * rule is unit-testable on the JVM harness; the repository stamps programId and weekStart.
 *
 * The move/discard/rebalance commit ONLY on workout completion (see [P2-A1]); abandoning never calls
 * this, so the week is left unchanged.
 */
object DayMovePlanner {

    fun movedRows(
        sourceRows: List<PlannedExercise>,
        targetDay: Int,
        performedNames: Set<String>,
        rationale: String
    ): List<PlannedExercise> =
        sourceRows.sortedBy { it.orderInDay }.mapIndexed { idx, row ->
            val performed = row.exerciseName in performedNames
            row.copy(
                id = 0L,
                dayOfWeek = targetDay,
                orderInDay = idx,
                isLogged = performed,
                rationale = rationale
            )
        }

    /**
     * Item 10: the target day's FINAL plan rows after moving [sourceRows] into it, converging the two
     * cases the merged "Start Workout" must handle:
     *  - target already has LOGGED activity ([targetRows] contains an isLogged row) → the moved rows
     *    are APPENDED after the target's existing rows (kept exactly, not discarded), renumbered
     *    contiguously so the day reads as ONE continuous session;
     *  - target has NO logged activity → its planned rows are REPLACED by the moved rows.
     *
     * All returned rows carry a fresh id (0) so the repository re-inserts them as new rows; the kept
     * target rows keep their isLogged flag + actuals verbatim (only their id/order/rationale change),
     * and logged SETS/history (separate tables) are never referenced here. Pure + unit-testable; the
     * repository stamps weekStart/programId and vacates the source day.
     */
    fun applyMoveToTarget(
        targetRows: List<PlannedExercise>,
        sourceRows: List<PlannedExercise>,
        targetDay: Int,
        performedNames: Set<String>,
        rationale: String
    ): List<PlannedExercise> {
        val moved = movedRows(sourceRows, targetDay, performedNames, rationale)
        val targetHasLogged = targetRows.any { it.isLogged }
        if (!targetHasLogged) return moved
        val kept = targetRows.sortedBy { it.orderInDay }
            .mapIndexed { idx, row -> row.copy(id = 0L, orderInDay = idx, rationale = rationale) }
        val appended = moved.mapIndexed { i, r -> r.copy(orderInDay = kept.size + i) }
        return kept + appended
    }
}
