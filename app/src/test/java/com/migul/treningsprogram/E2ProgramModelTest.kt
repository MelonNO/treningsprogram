package com.migul.treningsprogram

import com.migul.treningsprogram.data.backup.BackupMerger
import com.migul.treningsprogram.data.db.entity.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E2 program model: the pure-testable parts of the program/active-program concept and the mesocycle
 * week math. The active-program SWITCH (exactly-one-active invariant) is enforced by ProgramDao's
 * transaction on-device; here we lock the invariant where it is also enforced purely — the backup
 * merge — plus the mesocycle week-in-block derivation.
 */
class E2ProgramModelTest {

    private val WEEK = 7L * 24 * 60 * 60 * 1000

    private fun program(
        id: Long, name: String, active: Boolean = false,
        meso: Int = 0, blockStart: Long = 0L
    ) = Program(
        id = id, name = name, createdAtMs = id, isActive = active,
        mesocycleWeeks = meso, blockStartWeek = blockStart
    )

    // ---- mesocycle week-in-block (pure arithmetic) ----

    @Test fun plainProgram_isAlwaysWeekOne() {
        assertEquals(1, Program.weekInBlock(mesocycleWeeks = 0, blockStartWeek = 0L, currentMonday = 999_000L))
    }

    @Test fun blockStartWeek_isWeekOne() {
        val start = 1_000_000L
        assertEquals(1, Program.weekInBlock(6, start, start))
    }

    @Test fun thirdWeekOfBlock_isWeekThree() {
        val start = 1_000_000L
        assertEquals(3, Program.weekInBlock(6, start, start + 2 * WEEK))
    }

    @Test fun weekInBlock_clampedToBlockLength() {
        val start = 1_000_000L
        // 10 weeks elapsed in a 6-week block → clamps to 6, never reports past the end.
        assertEquals(6, Program.weekInBlock(6, start, start + 10 * WEEK))
    }

    @Test fun weekInBlock_neverBelowOne() {
        val start = 1_000_000L
        // currentMonday before blockStart (shouldn't happen) → clamps to 1.
        assertEquals(1, Program.weekInBlock(6, start, start - 3 * WEEK))
    }

    // ---- active-program invariant via merge (exactly one active survives) ----

    @Test fun mergePrograms_keepsExactlyOneActive_existingWins() {
        val existing = listOf(program(1, "Full Gym", active = true))
        val backup = listOf(program(50, "Travel", active = true)) // backup also claims active
        val merged = BackupMerger.mergePrograms(existing, backup)

        // Both programs present, but only ONE active — the device's existing one.
        assertEquals(2, merged.programs.size)
        assertEquals(1, merged.programs.count { it.isActive })
        assertTrue(merged.programs.first { it.isActive }.name == "Full Gym")
    }

    @Test fun mergePrograms_sameNameDedupes_existingWins_andRemapsId() {
        val existing = listOf(program(1, "My Program", active = true))
        val backup = listOf(program(99, "My Program", active = false))
        val merged = BackupMerger.mergePrograms(existing, backup)

        // Same name → single program kept (existing id 1), backup id 99 remapped to 1.
        assertEquals(1, merged.programs.size)
        assertEquals(1L, merged.programs.first().id)
        assertEquals(1L, merged.backupIdRemap[99L])
    }

    @Test fun mergePrograms_backupOnlyProgram_addedInactive_whenDeviceHasActive() {
        val existing = listOf(program(1, "Home", active = true))
        val backup = listOf(program(7, "Gym", active = false))
        val merged = BackupMerger.mergePrograms(existing, backup)

        assertEquals(2, merged.programs.size)
        assertEquals(1, merged.programs.count { it.isActive })
        // The backup-only program is present but NOT active.
        assertTrue(merged.programs.any { it.name == "Gym" && !it.isActive })
    }

    @Test fun mergePrograms_emptyExisting_promotesOneActive() {
        val merged = BackupMerger.mergePrograms(
            existing = emptyList(),
            backup = listOf(program(3, "A", active = false), program(4, "B", active = false))
        )
        assertEquals(2, merged.programs.size)
        // With no active on either side, exactly one is promoted active.
        assertEquals(1, merged.programs.count { it.isActive })
    }
}
