package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.domain.SetRenumbering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QoL item 01 (2026-07-25) — mid-workout set deletion renumbers the exercise's remaining sets.
 * Set numbers are assigned as count+1 at log time, so a deletion gap would make the NEXT logged
 * set duplicate an existing number (delete #2 of [1,2,3] → next = 2+1 = 3 → two "#3"s).
 */
class SetRenumberingTest {

    private fun set(id: Long, number: Int, loggedAt: Long, warmup: Boolean = false) = WorkoutSet(
        id = id, sessionId = 1L, exerciseName = "Bench Press", muscleGroup = "Chest",
        setNumber = number, reps = 8, weightKg = 60f, isWarmup = warmup, loggedAtMs = loggedAt
    )

    @Test fun middleDeletion_gapIsClosed() {
        // S1,S2,S3 logged; S2 deleted → remaining [1,3] must become [1,2].
        val remaining = listOf(set(1, 1, 100), set(3, 3, 300))
        val changed = SetRenumbering.renumberAfterDelete(remaining)
        assertEquals(1, changed.size)
        assertEquals(3L, changed[0].id)
        assertEquals(2, changed[0].setNumber)
    }

    @Test fun firstDeletion_allShiftDown() {
        val remaining = listOf(set(2, 2, 200), set(3, 3, 300))
        val changed = SetRenumbering.renumberAfterDelete(remaining)
        assertEquals(listOf(1, 2), changed.map { it.setNumber })
        assertEquals(listOf(2L, 3L), changed.map { it.id })
    }

    @Test fun lastDeletion_noChangesNeeded() {
        val remaining = listOf(set(1, 1, 100), set(2, 2, 200))
        assertTrue(SetRenumbering.renumberAfterDelete(remaining).isEmpty())
    }

    @Test fun deletingOnlySet_leavesNothingToRenumber() {
        assertTrue(SetRenumbering.renumberAfterDelete(emptyList()).isEmpty())
    }

    @Test fun orderFollowsLoggedTime_notStoredNumber() {
        // Out-of-order stored numbers (defensive): logged order wins.
        val remaining = listOf(set(9, 5, 300), set(7, 2, 100))
        val changed = SetRenumbering.renumberAfterDelete(remaining)
        // id 7 (earliest) → 1, id 9 → 2.
        assertEquals(2, changed.size)
        assertEquals(1, changed.first { it.id == 7L }.setNumber)
        assertEquals(2, changed.first { it.id == 9L }.setNumber)
    }

    @Test fun legacySetsWithoutTimestamp_fallBackToStoredNumberThenId() {
        val remaining = listOf(set(2, 3, 0), set(1, 2, 0))
        val changed = SetRenumbering.renumberAfterDelete(remaining)
        assertEquals(1, changed.first { it.id == 1L }.setNumber)
        assertEquals(2, changed.first { it.id == 2L }.setNumber)
    }

    @Test fun mixedWarmupAndWorking_shareOneSequence() {
        // Numbering is per-exercise across warm-up + working sets (matches log-time count+1).
        val remaining = listOf(set(1, 1, 100, warmup = true), set(3, 3, 300))
        val changed = SetRenumbering.renumberAfterDelete(remaining)
        assertEquals(1, changed.size)
        assertEquals(2, changed[0].setNumber)
    }

    @Test fun nextLoggedNumberAfterDeletion_neverDuplicates() {
        // The invariant the whole feature rests on: after renumbering, count+1 is a fresh number.
        val remaining = listOf(set(1, 1, 100), set(3, 3, 300))
        val renumbered = remaining.map { s ->
            SetRenumbering.renumberAfterDelete(remaining).firstOrNull { it.id == s.id } ?: s
        }
        val next = renumbered.size + 1
        assertTrue(renumbered.none { it.setNumber == next })
    }
}
