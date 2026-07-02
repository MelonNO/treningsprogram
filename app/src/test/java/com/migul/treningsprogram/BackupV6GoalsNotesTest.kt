package com.migul.treningsprogram

import com.google.gson.Gson
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.BackupMerger
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.db.entity.ExerciseNote
import com.migul.treningsprogram.data.db.entity.LiftGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backup v6 (feature batch 2026-07-03) — the N5 `goals` and N7 `exercise_notes` tables ride
 * through export, pre-v6 backups migrate cleanly (empty lists, no crash), and the merge rules
 * hold: goals union by identity with achieved-state-wins; notes union by name with
 * latest-edit-wins.
 */
class BackupV6GoalsNotesTest {

    private val gson = Gson()

    // ── migration ────────────────────────────────────────────────────────────────────────────

    @Test fun `v5 backup migrates to v6 with empty goals and notes`() {
        val v5 = """
            {
              "schema_version": 5,
              "exported_at": "2026-07-01T00:00:00Z",
              "sessions": [], "sets": [], "achievements": [],
              "body_measurements": [], "planned_exercises": [],
              "exercises": [], "gym_presets": [], "programs": [],
              "preferences": { "daysPerWeek": 5 }
            }
        """.trimIndent()
        val envelope = BackupMigrations.parseAndMigrate(gson, v5)
        assertEquals(6, envelope.schemaVersion)
        assertTrue(envelope.goals.isEmpty())
        assertTrue(envelope.exerciseNotes.isEmpty())
        assertEquals(5, envelope.preferences.daysPerWeek)
    }

    @Test fun `v1 backup still composes all the way to v6`() {
        val v1 = """
            {
              "schema_version": 1,
              "sessions": [], "sets": [], "achievements": [],
              "body_measurements": [], "planned_exercises": [],
              "preferences": {}
            }
        """.trimIndent()
        val envelope = BackupMigrations.parseAndMigrate(gson, v1)
        assertEquals(6, envelope.schemaVersion)
        assertTrue(envelope.goals.isEmpty())
        assertTrue(envelope.exerciseNotes.isEmpty())
    }

    // ── round-trip ───────────────────────────────────────────────────────────────────────────

    @Test fun `v6 round-trips goals and notes`() {
        val out = gson.toJson(
            BackupEnvelope(
                goals = listOf(
                    LiftGoal(id = 1, exerciseName = "Bench Press", targetWeightKg = 100f,
                        isE1rm = false, targetDateMs = 1_760_000_000_000L, createdAtMs = 42L)
                ),
                exerciseNotes = listOf(
                    ExerciseNote("Bench Press", "Pin height 7, belt on top sets", 99L)
                )
            )
        )
        val back = BackupMigrations.parseAndMigrate(gson, out)
        assertEquals(1, back.goals.size)
        assertEquals("Bench Press", back.goals[0].exerciseName)
        assertEquals(100f, back.goals[0].targetWeightKg)
        assertEquals(LiftGoal.STATUS_ACTIVE, back.goals[0].status)
        assertEquals(1, back.exerciseNotes.size)
        assertEquals("Pin height 7, belt on top sets", back.exerciseNotes[0].note)
    }

    // ── goal merge ───────────────────────────────────────────────────────────────────────────

    private fun goal(
        id: Long, name: String = "Bench Press", target: Float = 100f, createdAt: Long = 42L,
        status: String = LiftGoal.STATUS_ACTIVE, achievedAt: Long = 0L
    ) = LiftGoal(id = id, exerciseName = name, targetWeightKg = target,
        createdAtMs = createdAt, status = status, achievedAtMs = achievedAt)

    @Test fun `goal merge - same goal achieved on one side stays achieved`() {
        val existing = listOf(goal(1, status = LiftGoal.STATUS_ACTIVE))
        val backup = listOf(goal(7, status = LiftGoal.STATUS_ACHIEVED, achievedAt = 500L))
        val merged = BackupMerger.mergeGoals(existing, backup)
        assertEquals(1, merged.size)
        assertEquals(LiftGoal.STATUS_ACHIEVED, merged[0].status)
        assertEquals(500L, merged[0].achievedAtMs)
        assertEquals(1L, merged[0].id) // existing row kept, state upgraded
    }

    @Test fun `goal merge - backup-only goal is added with a collision-safe id`() {
        val existing = listOf(goal(1))
        val backup = listOf(goal(1, name = "Squat", target = 140f, createdAt = 77L))
        val merged = BackupMerger.mergeGoals(existing, backup)
        assertEquals(2, merged.size)
        val squat = merged.first { it.exerciseName == "Squat" }
        assertTrue(squat.id != 1L)
    }

    @Test fun `goal merge - identical goals do not duplicate`() {
        val existing = listOf(goal(1))
        val merged = BackupMerger.mergeGoals(existing, listOf(goal(1)))
        assertEquals(1, merged.size)
    }

    @Test fun `goal merge - active never downgrades an achieved goal`() {
        val existing = listOf(goal(1, status = LiftGoal.STATUS_ACHIEVED, achievedAt = 300L))
        val merged = BackupMerger.mergeGoals(existing, listOf(goal(9, status = LiftGoal.STATUS_ACTIVE)))
        assertEquals(1, merged.size)
        assertEquals(LiftGoal.STATUS_ACHIEVED, merged[0].status)
        assertEquals(300L, merged[0].achievedAtMs)
    }

    // ── note merge ───────────────────────────────────────────────────────────────────────────

    @Test fun `note merge - latest edit wins, case-insensitive name identity`() {
        val existing = listOf(ExerciseNote("Bench Press", "old", updatedAtMs = 100L))
        val backup = listOf(ExerciseNote("bench press", "newer", updatedAtMs = 200L))
        val merged = BackupMerger.mergeExerciseNotes(existing, backup)
        assertEquals(1, merged.size)
        assertEquals("newer", merged[0].note)
    }

    @Test fun `note merge - device wins on tie, backup-only names are added`() {
        val existing = listOf(ExerciseNote("Bench Press", "device", updatedAtMs = 100L))
        val backup = listOf(
            ExerciseNote("Bench Press", "backup", updatedAtMs = 100L),
            ExerciseNote("Squat", "low bar, wide stance", updatedAtMs = 50L)
        )
        val merged = BackupMerger.mergeExerciseNotes(existing, backup)
        assertEquals(2, merged.size)
        assertEquals("device", merged.first { it.exerciseName == "Bench Press" }.note)
        assertEquals("low bar, wide stance", merged.first { it.exerciseName == "Squat" }.note)
    }
}
