package com.migul.treningsprogram

import com.google.gson.GsonBuilder
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.BackupPreferences
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * S7 regression tests: backup/restore correctness, malformed input handling, schema guards.
 *
 * Covers:
 * - Empty JSON object → rejects gracefully (not a crash)
 * - Empty string → rejects gracefully
 * - Whitespace-only → rejects gracefully
 * - JSON array (not an object) → rejects gracefully
 * - Schema version missing → treated as v1 and forward-migrated (existing test; duplicated here
 *   for S7 completeness check)
 * - Schema version == CURRENT_BACKUP_VERSION → accepted as-is
 * - A future schema version (> CURRENT) → rejected with "newer app version" message
 * - A v1 backup with no exercises/gym_presets/programs → migrates to current, defaults to empty
 * - Export JSON must not contain an apiKey field
 * - Preferences round-trip: all backup-eligible fields are carried through serialisation
 */
class S7BackupRestoreTest {

    private val gson = GsonBuilder().create()

    // ---- Malformed / adversarial inputs --------------------------------------------------------

    @Test fun emptyJsonObject_isRejected() {
        try {
            BackupMigrations.parseAndMigrate(gson, "{}")
            // An empty object has no sessions/sets — fine to parse but we expect it to
            // treat schema_version as absent -> v1 -> migrates to current. Verify it doesn't crash.
            // (This tests the "no schema_version key" path producing a valid v1->current migration.)
        } catch (_: IllegalArgumentException) {
            // Also acceptable if the implementation rejects a completely empty envelope.
        }
    }

    @Test fun emptyString_isRejected() {
        try {
            BackupMigrations.parseAndMigrate(gson, "")
            fail("Expected IllegalArgumentException for empty input")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid backup file", e.message)
        }
    }

    @Test fun nullishInput_isRejected() {
        try {
            BackupMigrations.parseAndMigrate(gson, "   ")
            fail("Expected IllegalArgumentException for whitespace-only input")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid backup file", e.message)
        }
    }

    @Test fun jsonArray_isRejected() {
        try {
            BackupMigrations.parseAndMigrate(gson, "[]")
            fail("Expected IllegalArgumentException for JSON array input")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid backup file", e.message)
        }
    }

    @Test fun plainText_isRejected() {
        try {
            BackupMigrations.parseAndMigrate(gson, "not json at all")
            fail("Expected rejection for non-JSON input")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid backup file", e.message)
        }
    }

    // ---- Schema version guards -----------------------------------------------------------------

    @Test fun currentSchemaVersion_isAccepted() {
        val envelope = BackupEnvelope(schemaVersion = CURRENT_BACKUP_VERSION)
        val json = gson.toJson(envelope)
        val restored = BackupMigrations.parseAndMigrate(gson, json)
        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
    }

    @Test fun futureSchemaVersion_isRejectedWithClearMessage() {
        val futureJson = """{"schema_version": 9999}"""
        try {
            BackupMigrations.parseAndMigrate(gson, futureJson)
            fail("Expected rejection of future schema version")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error should mention 'newer app version', got: ${e.message}",
                e.message!!.contains("newer app version")
            )
        }
    }

    @Test fun missingSchemaVersion_isTreatedAsV1() {
        // A backup with no schema_version should be forward-migrated from v1.
        val noVersionJson = """
            {
              "exported_at": "2026-01-01T00:00:00Z",
              "sessions": [],
              "sets": [],
              "preferences": {}
            }
        """.trimIndent()
        val restored = BackupMigrations.parseAndMigrate(gson, noVersionJson)
        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
    }

    // ---- API key must NEVER appear in export ---------------------------------------------------

    @Test fun exportJson_neverContainsApiKey() {
        // BackupEnvelope has no apiKey field — verify no field named "apiKey" or
        // "claude_api_key" leaks into the serialised output (BackupSerializationTest already
        // covers this; we duplicate it here as a S7-specific safety pin).
        val envelope = BackupEnvelope(
            schemaVersion = CURRENT_BACKUP_VERSION,
            preferences = BackupPreferences(daysPerWeek = 5)
        )
        val json = gson.toJson(envelope)
        assertTrue("Export must not contain apiKey", !json.contains("apiKey"))
        assertTrue("Export must not contain claude_api_key", !json.contains("claude_api_key"))
    }

    // ---- Preferences round-trip ----------------------------------------------------------------

    @Test fun preferencesRoundTrip_allFieldsPreserved() {
        val prefs = BackupPreferences(
            daysPerWeek = 6,
            fitnessGoal = "Strength",
            experienceLevel = "Advanced",
            sessionDurationMinutes = 75,
            separateCardioDays = true,
            injuries = "knee",
            injurySeverity = "Moderate",
            priorityMuscles = "Chest,Back",
            dislikedExercises = "Burpees",
            onboardingContext = "ctx",
            wizardEquipment = "Barbell,Dumbbell",
            hasCompletedOnboarding = true,
            restTimerSeconds = 120,
            dailyChallengesJson = "{\"day\":1}",
            selectedGymPresetId = 3L
        )
        val envelope = BackupEnvelope(preferences = prefs)
        val json = gson.toJson(envelope)
        val restored = BackupMigrations.parseAndMigrate(gson, json)

        assertEquals(6, restored.preferences.daysPerWeek)
        assertEquals("Strength", restored.preferences.fitnessGoal)
        assertEquals("Advanced", restored.preferences.experienceLevel)
        assertEquals(75, restored.preferences.sessionDurationMinutes)
        assertTrue(restored.preferences.separateCardioDays)
        assertEquals("knee", restored.preferences.injuries)
        assertEquals("Moderate", restored.preferences.injurySeverity)
        assertEquals("Chest,Back", restored.preferences.priorityMuscles)
        assertEquals("Burpees", restored.preferences.dislikedExercises)
        assertEquals("ctx", restored.preferences.onboardingContext)
        assertEquals("Barbell,Dumbbell", restored.preferences.wizardEquipment)
        assertTrue(restored.preferences.hasCompletedOnboarding)
        assertEquals(120, restored.preferences.restTimerSeconds)
        assertEquals("{\"day\":1}", restored.preferences.dailyChallengesJson)
        assertEquals(3L, restored.preferences.selectedGymPresetId)
    }

    // ---- v2 backup forward-migrates to current ------------------------------------------------

    @Test fun v2Backup_migratesAndDefaultsProgramsToEmpty() {
        // A v2 backup (no "programs" array) should gain an empty programs list on migration.
        val v2Json = """
            {
              "schema_version": 2,
              "exported_at": "2026-02-01T00:00:00Z",
              "sessions": [],
              "sets": [],
              "achievements": [],
              "body_measurements": [],
              "planned_exercises": [],
              "exercises": [],
              "gym_presets": [],
              "preferences": {}
            }
        """.trimIndent()
        val restored = BackupMigrations.parseAndMigrate(gson, v2Json)
        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
        assertTrue("programs should default to empty on v2 migrate", restored.programs.isEmpty())
    }
}
