package com.migul.treningsprogram

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.BackupPreferences
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.Exercise
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Versioned serialization + forward-migration framework.
 *  - round-trip serialize→deserialize preserves all 8 entity types + all included prefs.
 *  - v1 (old manual export) JSON migrates and restores into the current shape.
 */
class BackupSerializationTest {

    private val gson: Gson = GsonBuilder().create()

    private fun fullEnvelope(): BackupEnvelope = BackupEnvelope(
        schemaVersion = CURRENT_BACKUP_VERSION,
        exportedAt = "2026-06-24T10:00:00Z",
        sessions = listOf(
            WorkoutSession(id = 1, dateMs = 1000L, durationMinutes = 45, notes = "leg day", isCompleted = true),
            WorkoutSession(id = 2, dateMs = 2000L, durationMinutes = 50, notes = "", isCompleted = true)
        ),
        sets = listOf(
            WorkoutSet(id = 1, sessionId = 1, exerciseName = "Squat", muscleGroup = "Legs", setNumber = 1, reps = 5, weightKg = 100f, loggedAtMs = 1001L),
            WorkoutSet(id = 2, sessionId = 2, exerciseName = "Bench", muscleGroup = "Chest", setNumber = 1, reps = 5, weightKg = 80f, isWarmup = true)
        ),
        achievements = listOf(
            Achievement("first_workout", "First Step", "desc", "🏃", isUnlocked = true, unlockedAtMs = 1500L)
        ),
        userStats = UserStats(id = 1, totalXp = 500, level = 2, currentStreak = 3, bestStreak = 5, totalWorkouts = 4, totalPrs = 2, lastWorkoutDateMs = 2000L),
        bodyMeasurements = listOf(BodyMeasurement(id = 1, dateMs = 1000L, weightKg = 82.5f)),
        plannedExercises = listOf(
            PlannedExercise(id = 1, weekStart = 7000L, dayOfWeek = 1, orderInDay = 0, exerciseName = "Squat", sets = 4, targetReps = "5", targetWeightKg = 100f, resolvedAt = 1234L)
        ),
        exercises = listOf(
            Exercise(id = 1, name = "Custom Lift", muscleGroup = "Back", equipment = "Barbell")
        ),
        gymPresets = listOf(
            GymPreset(id = 1, name = "Home Gym", equipmentJson = "[\"Barbell\"]", notes = "garage")
        ),
        preferences = BackupPreferences(
            daysPerWeek = 5, fitnessGoal = "Strength", experienceLevel = "Advanced",
            sessionDurationMinutes = 75, separateCardioDays = true, injuries = "knee",
            injurySeverity = "Moderate", priorityMuscles = "Chest,Back", dislikedExercises = "Burpees",
            onboardingContext = "ctx", wizardEquipment = "Barbell,Dumbbell", hasCompletedOnboarding = true,
            restTimerSeconds = 120, dailyChallengesJson = "{\"a\":1}", selectedGymPresetId = 1L
        )
    )

    @Test fun roundTrip_preservesAll8EntityTypesAndAllPrefs() {
        val original = fullEnvelope()
        val json = gson.toJson(original)
        val restored = BackupMigrations.parseAndMigrate(gson, json)

        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
        assertEquals(original.sessions, restored.sessions)
        assertEquals(original.sets, restored.sets)
        assertEquals(original.achievements, restored.achievements)
        assertEquals(original.userStats, restored.userStats)
        assertEquals(original.bodyMeasurements, restored.bodyMeasurements)
        assertEquals(original.plannedExercises, restored.plannedExercises)
        assertEquals(original.exercises, restored.exercises)            // previously MISSING
        assertEquals(original.gymPresets, restored.gymPresets)          // previously MISSING
        assertEquals(original.preferences, restored.preferences)        // incl. new prefs
    }

    @Test fun roundTrip_neverContainsApiKey() {
        val json = gson.toJson(fullEnvelope())
        assertTrue("backup JSON must not carry an apiKey field", !json.contains("apiKey"))
        assertTrue(!json.contains("claude_api_key"))
    }

    /** The exact shape the existing manual export produced (schema_version = 1, no exercises/presets). */
    private val v1Json = """
        {
          "schema_version": 1,
          "exported_at": "2026-01-01T00:00:00Z",
          "sessions": [
            {"id": 10, "dateMs": 5000, "durationMinutes": 40, "notes": "old", "isCompleted": true}
          ],
          "sets": [
            {"id": 7, "sessionId": 10, "exerciseName": "Deadlift", "muscleGroup": "Back", "setNumber": 1, "reps": 5, "weightKg": 120.0, "isWarmup": false, "rpeLabel": "", "loggedAtMs": 0}
          ],
          "achievements": [
            {"id": "first_pr", "name": "PR Crusher", "description": "d", "emoji": "💥", "isUnlocked": true, "unlockedAtMs": 4000}
          ],
          "user_stats": {"id": 1, "totalXp": 300, "level": 2, "currentStreak": 1, "bestStreak": 3, "totalWorkouts": 2, "totalPrs": 1, "lastWorkoutDateMs": 5000},
          "body_measurements": [{"id": 3, "dateMs": 5000, "weightKg": 80.0}],
          "planned_exercises": [
            {"id": 1, "weekStart": 7000, "dayOfWeek": 1, "orderInDay": 0, "exerciseName": "Deadlift", "sets": 3, "targetReps": "5", "targetWeightKg": 120.0}
          ],
          "preferences": {
            "daysPerWeek": 3, "fitnessGoal": "Endurance", "experienceLevel": "Beginner",
            "sessionDurationMinutes": 45, "separateCardioDays": false, "injuries": "",
            "priorityMuscles": "", "dislikedExercises": "", "onboardingContext": "",
            "wizardEquipment": "", "hasCompletedOnboarding": true
          }
        }
    """.trimIndent()

    @Test fun v1ManualExport_migratesAndRestoresIntoCurrentShape() {
        val restored = BackupMigrations.parseAndMigrate(gson, v1Json)

        // Version is now current.
        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)

        // v1 data carried through.
        assertEquals(1, restored.sessions.size)
        assertEquals(10L, restored.sessions.first().id)
        assertEquals(1, restored.sets.size)
        assertEquals("Deadlift", restored.sets.first().exerciseName)
        assertEquals(1, restored.achievements.size)
        assertTrue(restored.achievements.first().isUnlocked)
        assertEquals(300, restored.userStats!!.totalXp)
        assertEquals(1, restored.bodyMeasurements.size)
        assertEquals(1, restored.plannedExercises.size)

        // New v2 tables default to empty, not null.
        assertTrue(restored.exercises.isEmpty())
        assertTrue(restored.gymPresets.isEmpty())

        // Old prefs carried; new prefs fall back to defaults (NOT garbage).
        assertEquals(3, restored.preferences.daysPerWeek)
        assertEquals("Endurance", restored.preferences.fitnessGoal)
        assertEquals(BackupPreferences().restTimerSeconds, restored.preferences.restTimerSeconds)
        assertEquals(BackupPreferences().injurySeverity, restored.preferences.injurySeverity)
        assertEquals(BackupPreferences().selectedGymPresetId, restored.preferences.selectedGymPresetId)
        assertEquals(BackupPreferences().dailyChallengesJson, restored.preferences.dailyChallengesJson)
    }

    @Test fun missingSchemaVersion_isTreatedAsV1() {
        val noVersion = v1Json.replace("\"schema_version\": 1,", "")
        val restored = BackupMigrations.parseAndMigrate(gson, noVersion)
        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
        assertEquals(1, restored.sessions.size)
    }

    @Test fun newerThanSupported_isRejected() {
        val future = v1Json.replace("\"schema_version\": 1", "\"schema_version\": 999")
        try {
            BackupMigrations.parseAndMigrate(gson, future)
            fail("expected rejection of a newer-than-supported backup")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("newer app version"))
        }
    }

    @Test fun malformedJson_isRejected() {
        try {
            BackupMigrations.parseAndMigrate(gson, "not json at all")
            fail("expected rejection of malformed input")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid backup file", e.message)
        }
    }
}
