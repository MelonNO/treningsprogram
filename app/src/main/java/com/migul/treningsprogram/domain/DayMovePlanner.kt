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
}
