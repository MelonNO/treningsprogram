package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.WorkoutSet

/**
 * QoL item 01 — mid-workout set deletion.
 *
 * Set numbers are assigned at log time as "count of this exercise's sets + 1", and the log
 * screen renders them as W{n}/S{n}. Deleting a middle set would leave a gap that the very
 * next logged set duplicates (delete #2 of [1,2,3] → next = count(2)+1 = 3 → two "#3"s).
 * After a deletion the remaining sets of that exercise are therefore renumbered
 * sequentially in logged order, keeping the labels and the count+1 rule coherent.
 */
object SetRenumbering {

    /**
     * Returns copies of the sets whose [WorkoutSet.setNumber] must change to restore a
     * sequential 1..n numbering (in logged order); already-correct sets are omitted.
     * Order is by wall-clock log time, falling back to the stored number then row id for
     * legacy rows with loggedAtMs == 0.
     */
    fun renumberAfterDelete(remaining: List<WorkoutSet>): List<WorkoutSet> =
        remaining
            .sortedWith(compareBy({ it.loggedAtMs }, { it.setNumber }, { it.id }))
            .mapIndexedNotNull { i, s -> if (s.setNumber != i + 1) s.copy(setNumber = i + 1) else null }
}
