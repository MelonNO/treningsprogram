package com.migul.treningsprogram

import com.google.gson.Gson
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.BackupPreferences
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import com.migul.treningsprogram.data.backup.PreferencesMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backup v4 — the three previously-lost preferences (restDaysCsv, autoRebalanceEnabled,
 * dayBoundaryHour) now ride through export, migrate cleanly from v3, and follow the standard
 * phone-wins-if-set merge rule.
 */
class BackupV4PrefsTest {

    private val gson = Gson()

    @Test fun `current version is 4`() {
        assertEquals(4, CURRENT_BACKUP_VERSION)
    }

    @Test fun `v3 backup migrates to v4 with default new prefs`() {
        val v3 = """
            {
              "schema_version": 3,
              "exported_at": "2026-07-01T00:00:00Z",
              "sessions": [], "sets": [], "achievements": [],
              "body_measurements": [], "planned_exercises": [],
              "exercises": [], "gym_presets": [], "programs": [],
              "preferences": { "daysPerWeek": 5 }
            }
        """.trimIndent()
        val envelope = BackupMigrations.parseAndMigrate(gson, v3)
        assertEquals(5, envelope.preferences.daysPerWeek)
        // v3 has no v4 keys — they land on the documented defaults (old restore behaviour).
        assertEquals("", envelope.preferences.restDaysCsv)
        assertTrue(envelope.preferences.autoRebalanceEnabled)
        assertEquals(4, envelope.preferences.dayBoundaryHour)
    }

    @Test fun `v4 round-trips the new prefs`() {
        val out = gson.toJson(
            BackupEnvelope(
                preferences = BackupPreferences(
                    restDaysCsv = "6,7", autoRebalanceEnabled = false, dayBoundaryHour = 2
                )
            )
        )
        val back = BackupMigrations.parseAndMigrate(gson, out)
        assertEquals("6,7", back.preferences.restDaysCsv)
        assertFalse(back.preferences.autoRebalanceEnabled)
        assertEquals(2, back.preferences.dayBoundaryHour)
    }

    @Test fun `merge - phone-set values win over the backup`() {
        val phone = BackupPreferences(restDaysCsv = "1", autoRebalanceEnabled = false, dayBoundaryHour = 0)
        val backup = BackupPreferences(restDaysCsv = "6,7", autoRebalanceEnabled = true, dayBoundaryHour = 2)
        val merged = PreferencesMerger.merge(phone, backup)
        assertEquals("1", merged.restDaysCsv)
        assertFalse(merged.autoRebalanceEnabled)
        assertEquals(0, merged.dayBoundaryHour)
    }

    @Test fun `merge - defaults on the phone adopt the backup values`() {
        val phone = BackupPreferences() // untouched device
        val backup = BackupPreferences(restDaysCsv = "6,7", autoRebalanceEnabled = false, dayBoundaryHour = 2)
        val merged = PreferencesMerger.merge(phone, backup)
        assertEquals("6,7", merged.restDaysCsv)
        assertFalse(merged.autoRebalanceEnabled)
        assertEquals(2, merged.dayBoundaryHour)
    }
}
