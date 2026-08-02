package com.migul.treningsprogram

import com.google.gson.Gson
import com.migul.treningsprogram.data.ExerciseInfoCorrections.Codec
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.BackupPreferences
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import com.migul.treningsprogram.data.backup.PreferencesMerger
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Backup v8 (QoL 2026-08 item 04): the exercise-info correction maps (mismatch flags + re-match
 * overrides) ride through export, migrate cleanly from v7, and UNION-merge on restore.
 */
class BackupV8CorrectionsTest {

    private val gson = Gson()

    @Test fun `v7 backup migrates to v8 with empty correction maps`() {
        val v7 = """
            {
              "schema_version": 7,
              "exported_at": "2026-07-25T00:00:00Z",
              "sessions": [], "sets": [], "achievements": [],
              "body_measurements": [], "planned_exercises": [],
              "exercises": [], "gym_presets": [], "programs": [],
              "goals": [], "exercise_notes": [],
              "preferences": { "daysPerWeek": 5 }
            }
        """.trimIndent()
        val envelope = BackupMigrations.parseAndMigrate(gson, v7)
        assertEquals(CURRENT_BACKUP_VERSION, envelope.schemaVersion)
        assertEquals(5, envelope.preferences.daysPerWeek)
        // Pre-v8 backups have no corrections — documented "" default.
        assertEquals("", envelope.preferences.exerciseFlagsJson)
        assertEquals("", envelope.preferences.exerciseOverridesJson)
    }

    @Test fun `v8 round-trips the correction maps`() {
        val flags = Codec.serialize(mapOf("zottman curl" to Codec.encodeFlag("Zottman Curl", "Pullups")))
        val overrides = Codec.serialize(mapOf("zottman curl" to "Zottman_Curl"))
        val out = gson.toJson(
            BackupEnvelope(
                preferences = BackupPreferences(
                    exerciseFlagsJson = flags, exerciseOverridesJson = overrides
                )
            )
        )
        val back = BackupMigrations.parseAndMigrate(gson, out)
        assertEquals(flags, back.preferences.exerciseFlagsJson)
        assertEquals(overrides, back.preferences.exerciseOverridesJson)
    }

    @Test fun `restore union-merges corrections - both sides kept, device wins collisions`() {
        val device = BackupPreferences(
            exerciseFlagsJson = Codec.serialize(mapOf("a" to "A-device", "shared" to "S-device")),
            exerciseOverridesJson = Codec.serialize(mapOf("x" to "X-device"))
        )
        val backup = BackupPreferences(
            exerciseFlagsJson = Codec.serialize(mapOf("b" to "B-backup", "shared" to "S-backup")),
            exerciseOverridesJson = Codec.serialize(mapOf("y" to "Y-backup"))
        )
        val merged = PreferencesMerger.merge(device, backup)
        assertEquals(
            mapOf("shared" to "S-device", "b" to "B-backup", "a" to "A-device"),
            Codec.parse(merged.exerciseFlagsJson)
        )
        assertEquals(
            mapOf("x" to "X-device", "y" to "Y-backup"),
            Codec.parse(merged.exerciseOverridesJson)
        )
    }

    @Test fun `restore with no corrections anywhere stays at the empty default`() {
        val merged = PreferencesMerger.merge(BackupPreferences(), BackupPreferences())
        assertEquals("", merged.exerciseFlagsJson)
        assertEquals("", merged.exerciseOverridesJson)
    }
}
