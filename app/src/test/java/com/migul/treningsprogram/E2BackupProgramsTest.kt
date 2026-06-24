package com.migul.treningsprogram

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.BackupMerger
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E2 backup inclusion (v3): the `programs` table is core user data and is backed up.
 *  - v3 round-trips programs + the planned_exercises.programId column.
 *  - a v2 backup (no programs) forward-migrates cleanly to v3 (empty programs, null programId).
 *  - on restore, backup plan rows are repointed at the program they merged into (id remap).
 */
class E2BackupProgramsTest {

    private val gson: Gson = GsonBuilder().create()

    @Test fun version_isThree() {
        assertEquals(3, CURRENT_BACKUP_VERSION)
    }

    @Test fun v3RoundTrip_preservesProgramsAndProgramId() {
        val envelope = BackupEnvelope(
            schemaVersion = CURRENT_BACKUP_VERSION,
            exportedAt = "2026-06-24T10:00:00Z",
            programs = listOf(
                Program(id = 1, name = "Full Gym", createdAtMs = 100L, isActive = true, mesocycleWeeks = 6, blockStartWeek = 7000L, isDeloadActive = true, isFrozen = false),
                Program(id = 2, name = "Travel", createdAtMs = 200L, isActive = false, isFrozen = true)
            ),
            plannedExercises = listOf(
                PlannedExercise(id = 1, weekStart = 7000L, dayOfWeek = 1, orderInDay = 0, exerciseName = "Squat", sets = 4, targetReps = "5", targetWeightKg = 100f, programId = 1L)
            )
        )
        val json = gson.toJson(envelope)
        val restored = BackupMigrations.parseAndMigrate(gson, json)

        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
        assertEquals(envelope.programs, restored.programs)
        // programId rides through whole-entity Gson.
        assertEquals(1L, restored.plannedExercises.first().programId)
        // mesocycle/deload/frozen state survives.
        val fullGym = restored.programs.first { it.name == "Full Gym" }
        assertEquals(6, fullGym.mesocycleWeeks)
        assertTrue(fullGym.isDeloadActive)
        assertTrue(restored.programs.first { it.name == "Travel" }.isFrozen)
    }

    @Test fun v2Backup_migratesToV3_withEmptyProgramsAndNullProgramId() {
        // A v2 envelope: 8 tables, no `programs`, plan row without programId.
        val v2Json = """
            {
              "schema_version": 2,
              "exported_at": "2026-02-01T00:00:00Z",
              "sessions": [],
              "sets": [],
              "achievements": [],
              "user_stats": null,
              "body_measurements": [],
              "planned_exercises": [
                {"id": 1, "weekStart": 7000, "dayOfWeek": 1, "orderInDay": 0, "exerciseName": "Squat", "sets": 3, "targetReps": "5", "targetWeightKg": 100.0}
              ],
              "exercises": [],
              "gym_presets": [],
              "preferences": {}
            }
        """.trimIndent()
        val restored = BackupMigrations.parseAndMigrate(gson, v2Json)

        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
        assertTrue("v2 backup migrates to empty programs", restored.programs.isEmpty())
        // Pre-E2 plan row → programId null (adopted into the default program on next launch).
        assertEquals(1, restored.plannedExercises.size)
        assertNull(restored.plannedExercises.first().programId)
    }

    @Test fun restore_repointsBackupPlanRowsThroughProgramIdRemap() {
        // Device already has a program named "My Program" with id 1; backup's same-named program is
        // id 99 → it remaps to 1, and a backup plan row scoped to 99 must follow to 1.
        val existing = listOf(Program(id = 1, name = "My Program", createdAtMs = 1L, isActive = true))
        val backup = listOf(Program(id = 99, name = "My Program", createdAtMs = 2L, isActive = false))
        val mergedPrograms = BackupMerger.mergePrograms(existing, backup)
        assertEquals(1L, mergedPrograms.backupIdRemap[99L])

        // Simulate the repository's repoint step.
        val backupPlan = listOf(
            PlannedExercise(id = 5, weekStart = 8000L, dayOfWeek = 2, orderInDay = 0, exerciseName = "Bench", sets = 3, targetReps = "5", targetWeightKg = 80f, programId = 99L)
        )
        val repointed = backupPlan.map { row ->
            val pid = row.programId
            if (pid != null) row.copy(programId = mergedPrograms.backupIdRemap[pid] ?: pid) else row
        }
        assertEquals(1L, repointed.first().programId)
    }

    @Test fun mergePlanned_doesNotCrossProgramBoundaries() {
        // Same weekStart in two different programs must NOT replace each other.
        fun row(program: Long, name: String, resolved: Long) = PlannedExercise(
            weekStart = 7000L, dayOfWeek = 1, orderInDay = 0, exerciseName = name,
            sets = 3, targetReps = "5", targetWeightKg = 0f, resolvedAt = resolved, programId = program
        )
        val existing = listOf(row(1, "ProgA-Squat", 100L))
        val backup = listOf(row(2, "ProgB-Bench", 200L)) // newer, but DIFFERENT program
        val merged = BackupMerger.mergePlannedExercises(existing, backup)
        // Both survive — they belong to different programs for the same week.
        assertEquals(setOf("ProgA-Squat", "ProgB-Bench"), merged.map { it.exerciseName }.toSet())
    }
}
