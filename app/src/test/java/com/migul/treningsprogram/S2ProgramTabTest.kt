package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.DayPlanEditor
import com.migul.treningsprogram.domain.DeloadPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2 program-tab bug sweep — locks the pure logic behind the two defects fixed in this wave:
 *
 *  1. Deload silently dropped on a same-week "Regenerate program now": [DeloadPolicy.nextDeloadStateForRegen]
 *     must KEEP a deload when re-doing the current week, while still entering on stalls and exiting on
 *     a genuine week transition.
 *  2. Lost manual edit on rapid add/delete/reorder: the repository now applies each [DayPlanEditor]
 *     transform against the FRESHLY-persisted day rows (read inside a locked transaction). The DB
 *     interleaving is verified on-device, but here we lock the invariant the repository relies on —
 *     that applying the editor transforms SEQUENTIALLY (each on the previous result, which is what
 *     reading-fresh-each-time produces) always yields a contiguous 0..n-1 orderInDay.
 */
class S2ProgramTabTest {

    private fun ex(id: Long, name: String, order: Int, day: Int = 1) = PlannedExercise(
        id = id, weekStart = 0L, dayOfWeek = day, orderInDay = order,
        exerciseName = name, sets = 3, targetReps = "8-12", targetWeightKg = 50f
    )

    private fun assertContiguousOrder(list: List<PlannedExercise>) {
        list.forEachIndexed { index, e ->
            assertEquals("orderInDay must equal index for ${e.exerciseName}", index, e.orderInDay)
        }
    }

    // ── 1. Deload regen state machine ───────────────────────────────────────────────────────────

    @Test fun regen_sameWeek_whileDeloading_keepsDeload() {
        // The original bug: tapping "Regenerate program now" again inside a deload week flipped the
        // flag off. Re-doing the current week must KEEP the deload regardless of stall count.
        assertTrue(
            DeloadPolicy.nextDeloadStateForRegen(
                currentlyDeloading = true, stalledCount = 0, replacingCurrentWeek = true
            )
        )
        assertTrue(
            DeloadPolicy.nextDeloadStateForRegen(
                currentlyDeloading = true, stalledCount = 5, replacingCurrentWeek = true
            )
        )
    }

    @Test fun regen_freshWeek_whileDeloading_exitsDeload() {
        // A genuine week transition (no plan yet for the new week) still exits after one deload week.
        assertFalse(
            DeloadPolicy.nextDeloadStateForRegen(
                currentlyDeloading = true, stalledCount = 5, replacingCurrentWeek = false
            )
        )
    }

    @Test fun regen_entersDeload_whenStallsReached_evenReplacingCurrentWeek() {
        // Not currently deloading + enough stalls → enter a deload, whether or not a plan exists.
        assertTrue(
            DeloadPolicy.nextDeloadStateForRegen(
                currentlyDeloading = false, stalledCount = 2, replacingCurrentWeek = true
            )
        )
        assertTrue(
            DeloadPolicy.nextDeloadStateForRegen(
                currentlyDeloading = false, stalledCount = 2, replacingCurrentWeek = false
            )
        )
    }

    @Test fun regen_noStalls_notDeloading_staysOff() {
        assertFalse(
            DeloadPolicy.nextDeloadStateForRegen(
                currentlyDeloading = false, stalledCount = 0, replacingCurrentWeek = true
            )
        )
        assertFalse(
            DeloadPolicy.nextDeloadStateForRegen(
                currentlyDeloading = false, stalledCount = 1, replacingCurrentWeek = false
            )
        )
    }

    @Test fun regenForRegen_matchesPlainPolicy_whenNotReplacingCurrentWeek() {
        // When NOT replacing the current week, the regen variant must behave exactly like the plain
        // once-per-week state machine — so the auto-gen path's semantics are unchanged.
        for (deloading in listOf(false, true)) {
            for (count in 0..3) {
                assertEquals(
                    "deloading=$deloading count=$count",
                    DeloadPolicy.nextDeloadState(deloading, count),
                    DeloadPolicy.nextDeloadStateForRegen(deloading, count, replacingCurrentWeek = false)
                )
            }
        }
    }

    // ── 2. Sequential editor application (models the read-fresh-each-time repository path) ───────

    @Test fun rapidReorderThenDelete_appliedSequentially_keepsContiguousOrder() {
        // Move "Bench" up, THEN delete "Squat" — each applied to the previous result (what the
        // repository now produces by reading fresh rows under a lock). Pre-fix, the delete read a
        // stale snapshot and clobbered the move; sequential application must not.
        val start = listOf(ex(1, "Squat", 0), ex(2, "Bench", 1), ex(3, "Row", 2))
        val afterMove = DayPlanEditor.move(start, start[1], up = true)   // Bench, Squat, Row
        val squatNow = afterMove.first { it.exerciseName == "Squat" }
        val afterDelete = DayPlanEditor.remove(afterMove, squatNow)      // Bench, Row

        assertEquals(listOf("Bench", "Row"), afterDelete.map { it.exerciseName })
        assertContiguousOrder(afterDelete)
    }

    @Test fun rapidAddThenMove_appliedSequentially_keepsContiguousOrder() {
        val start = listOf(ex(1, "Squat", 0), ex(2, "Bench", 1))
        val afterAdd = DayPlanEditor.add(start, ex(0, "Curl", 99))       // Squat, Bench, Curl
        val curlNow = afterAdd.first { it.exerciseName == "Curl" }
        val afterMove = DayPlanEditor.move(afterAdd, curlNow, up = true) // Squat, Curl, Bench

        assertEquals(listOf("Squat", "Curl", "Bench"), afterMove.map { it.exerciseName })
        assertContiguousOrder(afterMove)
        assertEquals(3, afterMove.size)
    }

    @Test fun deleteAllOneByOne_endsEmpty_withNoOrphanIndices() {
        var list = listOf(ex(1, "A", 0), ex(2, "B", 1), ex(3, "C", 2))
        while (list.isNotEmpty()) {
            list = DayPlanEditor.remove(list, list.first())
            assertContiguousOrder(list)
        }
        assertTrue(list.isEmpty())
    }
}
