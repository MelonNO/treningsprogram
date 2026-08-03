package com.migul.treningsprogram

import com.google.gson.Gson
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Backup portability (2026-08-03) — ground-truth verification against a REAL release-build
 * (minified) export, read in place from outside the repo.
 *
 * Skips silently unless the environment variable `REAL_BACKUP_JSON` points at an actual backup
 * file, so CI and other machines are unaffected:
 *
 *     REAL_BACKUP_JSON=/path/to/backup.json ./build.sh test
 *
 * Optionally, `REAL_BACKUP_EXPECTED` pins the exact table counts as
 * "sessions,sets,achievements,bodyMeasurements,plannedExercises,exercises,gymPresets,programs"
 * so the restore is proven LOSSLESS against externally-counted numbers, not just self-consistent.
 * Only aggregate counts are ever asserted or reported — no row contents.
 */
class RealBackupCompatTest {

    @Test fun realMinifiedBackup_restoresLosslessly() {
        val path = System.getenv("REAL_BACKUP_JSON")
        assumeTrue("REAL_BACKUP_JSON not set — skipping real-file verification", !path.isNullOrBlank())
        val file = File(path!!)
        assumeTrue("file not found: $path", file.isFile)

        val env = BackupMigrations.parseAndMigrate(Gson(), file.readText())

        assertEquals(CURRENT_BACKUP_VERSION, env.schemaVersion)
        assertTrue(env.exportedAt.isNotBlank())

        // Structural integrity: nothing silently dropped or half-bound.
        assertTrue("expected at least one session", env.sessions.isNotEmpty())
        assertTrue("expected sets", env.sets.isNotEmpty())
        assertTrue("every session must carry a real timestamp", env.sessions.all { it.dateMs > 0L })
        val sessionIds = env.sessions.map { it.id }.toSet()
        assertTrue("every set must reference a restored session",
            env.sets.all { it.sessionId in sessionIds })
        assertTrue("every set must carry its exercise name", env.sets.none { it.exerciseName.isBlank() })
        assertTrue("planned rows must carry week keys",
            env.plannedExercises.all { it.weekStart > 0L && it.dayOfWeek in 1..7 })
        assertTrue("planned rows must carry exercise names",
            env.plannedExercises.none { it.exerciseName.isBlank() })
        assertNotNull("user stats must bind", env.userStats)
        assertTrue("preferences must bind to real values", env.preferences.daysPerWeek in 1..7)
        assertTrue(env.preferences.sessionDurationMinutes in 20..180)
        assertFalse("a real profile has a goal string", env.preferences.fitnessGoal.isBlank())

        val expected = System.getenv("REAL_BACKUP_EXPECTED")
        if (!expected.isNullOrBlank()) {
            val n = expected.split(",").map { it.trim().toInt() }
            assertEquals("sessions", n[0], env.sessions.size)
            assertEquals("sets", n[1], env.sets.size)
            assertEquals("achievements", n[2], env.achievements.size)
            assertEquals("bodyMeasurements", n[3], env.bodyMeasurements.size)
            assertEquals("plannedExercises", n[4], env.plannedExercises.size)
            assertEquals("exercises", n[5], env.exercises.size)
            assertEquals("gymPresets", n[6], env.gymPresets.size)
            assertEquals("programs", n[7], env.programs.size)
        }

        // Round-trip: the NEW stable format re-exports and re-imports without loss.
        val gson = Gson()
        val reimported = BackupMigrations.parseAndMigrate(gson, gson.toJson(env))
        assertEquals(env.sessions, reimported.sessions)
        assertEquals(env.sets, reimported.sets)
        assertEquals(env.achievements, reimported.achievements)
        assertEquals(env.bodyMeasurements, reimported.bodyMeasurements)
        assertEquals(env.plannedExercises, reimported.plannedExercises)
        assertEquals(env.exercises, reimported.exercises)
        assertEquals(env.gymPresets, reimported.gymPresets)
        assertEquals(env.programs, reimported.programs)
        assertEquals(env.goals, reimported.goals)
        assertEquals(env.exerciseNotes, reimported.exerciseNotes)
        assertEquals(env.userStats, reimported.userStats)
        assertEquals(env.preferences, reimported.preferences)
    }
}
