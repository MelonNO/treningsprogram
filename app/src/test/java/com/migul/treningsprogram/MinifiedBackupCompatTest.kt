package com.migul.treningsprogram

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.MinifiedBackupCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backup portability (2026-08-03).
 *
 * Release builds used to export backups with R8-MINIFIED field names (no @SerializedName/keep
 * rules on the backup-reachable classes), so debug and release wrote mutually incompatible JSON.
 * The fix pins every backup-reachable field with @SerializedName (stable format from now on) and
 * adds [MinifiedBackupCompat] so every EXISTING minified backup still restores transparently.
 *
 * The minified fixture below mirrors the REAL structure of a release-build v8 export (verified
 * against the user's actual export and the release `mapping.txt`): annotated fields keep their
 * annotation keys; un-annotated fields appear as declaration-order letters.
 */
class MinifiedBackupCompatTest {

    private val gson = Gson()

    /** A faithful miniature of a release-build (minified) v8 export. */
    private val minifiedV8 = """
        {
          "schema_version": 8,
          "exported_at": "2026-08-03T11:24:32Z",
          "c": [ {"a": 7, "b": 1754000000000, "c": 62, "d": "good day", "e": true} ],
          "d": [
            {"a": 1, "b": 7, "c": "Bench Press", "d": "Chest", "e": 1, "f": 10, "g": 60.0, "h": false, "i": "Hard", "j": 1754000100000},
            {"a": 2, "b": 7, "c": "Bench Press", "d": "Chest", "e": 0, "f": 8, "g": 40.0, "h": true, "i": "", "j": 1754000000500}
          ],
          "e": [ {"a": "first_workout", "b": "First Workout", "c": "Log one workout", "d": "X", "e": true, "f": 1700000000000} ],
          "user_stats": {"a": 1, "b": 5200, "c": 7, "d": 3, "e": 9, "f": 41, "g": 12, "h": 1754000000000},
          "body_measurements": [ {"a": 3, "b": 1753000000000, "c": 82.5} ],
          "planned_exercises": [
            {"a": 11, "b": 1753653600000, "c": 2, "d": 0, "e": "Barbell Squat", "f": 4, "g": "6-8",
             "h": 90.0, "i": "", "j": true, "k": 92.5, "l": "8,8,7,6", "m": 4, "n": 180,
             "o": "0043", "p": 0.92, "q": "resolver", "r": 1753000000000, "s": "keeps the squat anchor", "t": 1}
          ],
          "i": [ {"a": 5, "b": "Bench Press", "c": "Chest", "d": "Barbell", "e": "0025", "f": 0.9, "g": "resolver", "h": 1753000000000} ],
          "gym_presets": [ {"a": 1, "b": "Home", "c": "[]", "d": ""} ],
          "k": [ {"a": 1, "b": "Default", "c": 1750000000000, "d": true, "e": 0, "f": 0, "g": false, "h": false} ],
          "l": [],
          "exercise_notes": [],
          "n": {"a": 4, "b": "Hypertrophy", "c": "Intermediate", "d": 60, "e": false, "f": "", "g": "",
                "h": "Chest", "i": "", "j": "", "k": "", "l": true, "m": 90, "n": "", "o": -1,
                "p": "6,7", "q": true, "r": 4, "s": false, "t": 180, "u": 90, "v": "", "w": ""}
        }
    """.trimIndent()

    @Test fun minifiedV8_restoresEveryTableAndField() {
        val env = BackupMigrations.parseAndMigrate(gson, minifiedV8)

        assertEquals(CURRENT_BACKUP_VERSION, env.schemaVersion)
        assertEquals("2026-08-03T11:24:32Z", env.exportedAt)

        val session = env.sessions.single()
        assertEquals(7L, session.id)
        assertEquals(1754000000000L, session.dateMs)
        assertEquals(62, session.durationMinutes)
        assertEquals("good day", session.notes)
        assertTrue(session.isCompleted)
        assertNull(session.kind)

        assertEquals(2, env.sets.size)
        val work = env.sets.first()
        assertEquals(7L, work.sessionId)
        assertEquals("Bench Press", work.exerciseName)
        assertEquals("Chest", work.muscleGroup)
        assertEquals(10, work.reps)
        assertEquals(60.0f, work.weightKg, 0f)
        assertFalse(work.isWarmup)
        assertEquals("Hard", work.rpeLabel)
        assertEquals(1754000100000L, work.loggedAtMs)
        assertTrue(env.sets[1].isWarmup)

        val ach = env.achievements.single()
        assertEquals("first_workout", ach.id)
        assertTrue(ach.isUnlocked)

        val stats = env.userStats!!
        assertEquals(5200, stats.totalXp)
        assertEquals(7, stats.level)
        assertEquals(41, stats.totalWorkouts)

        val bm = env.bodyMeasurements.single()
        assertEquals(82.5f, bm.weightKg, 0f)

        val pe = env.plannedExercises.single()
        assertEquals("Barbell Squat", pe.exerciseName)
        assertEquals(4, pe.sets)
        assertEquals("6-8", pe.targetReps)
        assertEquals(90.0f, pe.targetWeightKg, 0f)
        assertTrue(pe.isLogged)
        assertEquals(92.5f, pe.actualWeightKg, 0f)
        assertEquals("8,8,7,6", pe.actualReps)
        assertEquals(180, pe.recommendedRestSeconds)
        assertEquals("0043", pe.exerciseDbId)
        assertEquals("keeps the squat anchor", pe.rationale)
        assertEquals(1L, pe.programId)

        assertEquals("Bench Press", env.exercises.single().name)

        val preset = env.gymPresets.single()
        assertEquals("Home", preset.name)
        assertNull(preset.barWeightKg)          // absent nullable stays null
        assertNull(preset.avoidExercisesJson)

        val program = env.programs.single()
        assertEquals("Default", program.name)
        assertTrue(program.isActive)

        assertTrue(env.goals.isEmpty())
        assertTrue(env.exerciseNotes.isEmpty())

        val prefs = env.preferences
        assertEquals(4, prefs.daysPerWeek)
        assertEquals("Hypertrophy", prefs.fitnessGoal)
        assertEquals("Intermediate", prefs.experienceLevel)
        assertEquals(60, prefs.sessionDurationMinutes)
        assertEquals("Chest", prefs.priorityMuscles)
        assertTrue(prefs.hasCompletedOnboarding)
        assertEquals("6,7", prefs.restDaysCsv)
        assertEquals(4, prefs.dayBoundaryHour)
        assertEquals(180, prefs.manualRestHeavySeconds)
    }

    // ── The canonical (debug/new-stable) format must be untouched by the compat layer ──────────

    @Test fun canonicalBackup_isNotMistakenForMinified_andRoundTrips() {
        val canonical = gson.toJson(BackupEnvelope(exportedAt = "2026-08-03T00:00:00Z"))
        val root = JsonParser.parseString(canonical).asJsonObject
        assertFalse(MinifiedBackupCompat.looksMinified(root))

        val env = BackupMigrations.parseAndMigrate(gson, canonical)
        assertEquals(CURRENT_BACKUP_VERSION, env.schemaVersion)
        assertEquals("2026-08-03T00:00:00Z", env.exportedAt)
    }

    /**
     * The NEW stable export format: @SerializedName pins every key, so serialization emits the
     * canonical names (this is what makes release == debug from now on — in a release build the
     * same annotations drive Gson, so this JVM assertion covers the release format too).
     */
    @Test fun export_usesStableCanonicalKeys() {
        val json = gson.toJson(BackupEnvelope())
        for (key in listOf(
            "schema_version", "sessions", "sets", "achievements", "body_measurements",
            "planned_exercises", "exercises", "gym_presets", "programs", "goals",
            "exercise_notes", "preferences"
        )) {
            assertTrue("top-level key '$key' must be present in an export", json.contains("\"$key\""))
        }
        val prefsJson = gson.toJson(com.migul.treningsprogram.data.backup.BackupPreferences())
        assertTrue(prefsJson.contains("\"daysPerWeek\""))
        assertTrue(prefsJson.contains("\"exerciseOverridesJson\""))
        val sessionJson = gson.toJson(
            com.migul.treningsprogram.data.db.entity.WorkoutSession(id = 1, dateMs = 2, durationMinutes = 3)
        )
        assertTrue(sessionJson.contains("\"dateMs\""))
        assertFalse("minified letters must never appear", sessionJson.contains("\"b\":"))
    }

    // ── Era-correct envelope maps (the envelope's letters shifted as fields were inserted) ─────

    @Test fun envelopeMap_isEraAware() {
        assertEquals("preferences", MinifiedBackupCompat.envelopeMapFor(2)["k"])
        assertEquals("preferences", MinifiedBackupCompat.envelopeMapFor(3)["l"])
        assertEquals("preferences", MinifiedBackupCompat.envelopeMapFor(5)["l"])
        assertEquals("preferences", MinifiedBackupCompat.envelopeMapFor(6)["n"])
        assertEquals("preferences", MinifiedBackupCompat.envelopeMapFor(8)["n"])
        assertEquals("programs", MinifiedBackupCompat.envelopeMapFor(8)["k"])
    }

    /** A minified v5-era backup: preferences under "l", no goals/notes lists; migrations fill in. */
    @Test fun minifiedV5_translatesWithEraMap_andMigratesForward() {
        val v5 = """
            {
              "schema_version": 5,
              "exported_at": "2026-01-01T00:00:00Z",
              "c": [], "d": [], "e": [],
              "body_measurements": [], "planned_exercises": [],
              "i": [], "gym_presets": [], "k": [],
              "l": {"a": 3, "b": "Strength", "c": "Beginner", "d": 45}
            }
        """.trimIndent()
        val env = BackupMigrations.parseAndMigrate(gson, v5)
        assertEquals(CURRENT_BACKUP_VERSION, env.schemaVersion)
        assertEquals(3, env.preferences.daysPerWeek)
        assertEquals("Strength", env.preferences.fitnessGoal)
        assertEquals(45, env.preferences.sessionDurationMinutes)
        assertTrue(env.goals.isEmpty())          // added by v5 -> v6 migration
        assertTrue(env.exerciseNotes.isEmpty())
    }
}
