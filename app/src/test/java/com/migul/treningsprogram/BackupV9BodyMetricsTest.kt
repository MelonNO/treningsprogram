package com.migul.treningsprogram

import com.google.gson.Gson
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.BackupMerger
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.BackupPreferences
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import com.migul.treningsprogram.data.backup.PreferencesMerger
import com.migul.treningsprogram.data.db.entity.BodyMetric
import com.migul.treningsprogram.domain.BodyComposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Body-progress batch 2026-08-04 (brief 02) — backup format v9.
 *
 * Covers the acceptance criterion "backup export/import round-trips all new measurement and profile
 * data, and older backups still import cleanly".
 */
class BackupV9BodyMetricsTest {

    private val gson = Gson()

    @Test fun `v9 is the current version`() {
        assertEquals(9, CURRENT_BACKUP_VERSION)
    }

    // ── Round trip ────────────────────────────────────────────────────────────────────────────

    @Test fun `girths and profile round-trip through JSON`() {
        val original = BackupEnvelope(
            bodyMetrics = listOf(
                BodyMetric(id = 1, dateMs = 1_000L, waistCm = 88.5f, neckCm = 38f, hipCm = null),
                BodyMetric(id = 2, dateMs = 2_000L, waistCm = null, neckCm = null, hipCm = 99f)
            ),
            preferences = BackupPreferences(heightCm = 181.5f, sex = BodyComposition.SEX_FEMALE)
        )
        val restored = BackupMigrations.parseAndMigrate(gson, gson.toJson(original))

        assertEquals(original.bodyMetrics, restored.bodyMetrics)
        assertEquals(181.5f, restored.preferences.heightCm, 0.0001f)
        assertEquals(BodyComposition.SEX_FEMALE, restored.preferences.sex)
    }

    @Test fun `nulls survive the round trip - a waist-only entry stays waist-only`() {
        val original = BackupEnvelope(
            bodyMetrics = listOf(BodyMetric(id = 5, dateMs = 7_000L, waistCm = 90f))
        )
        val restored = BackupMigrations.parseAndMigrate(gson, gson.toJson(original))
        val row = restored.bodyMetrics.single()
        assertEquals(90f, row.waistCm!!, 0.0001f)
        assertNull(row.neckCm)
        assertNull(row.hipCm)
    }

    @Test fun `body fat is NOT serialized - it is derived on read`() {
        val json = gson.toJson(BackupEnvelope(bodyMetrics = listOf(BodyMetric(dateMs = 1L, waistCm = 90f))))
        assertTrue(
            "body fat must not be stored; recomputing it is what makes A5 work",
            !json.contains("bodyFat")
        )
    }

    // ── Backward compatibility ────────────────────────────────────────────────────────────────

    @Test fun `a v8 backup migrates cleanly and simply has no girths`() {
        val v8 = """
            {
              "schema_version": 8,
              "exported_at": "2026-08-03T00:00:00Z",
              "sessions": [],
              "sets": [],
              "achievements": [],
              "body_measurements": [ {"id": 1, "dateMs": 1000, "weightKg": 80.5} ],
              "planned_exercises": [],
              "exercises": [],
              "gym_presets": [],
              "programs": [],
              "goals": [],
              "exercise_notes": [],
              "preferences": { "daysPerWeek": 5 }
            }
        """.trimIndent()

        val restored = BackupMigrations.parseAndMigrate(gson, v8)

        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
        assertTrue("no girth data existed before v9", restored.bodyMetrics.isEmpty())
        // The existing weight series is untouched by the bump.
        assertEquals(1, restored.bodyMeasurements.size)
        assertEquals(80.5f, restored.bodyMeasurements.single().weightKg, 0.0001f)
        // Absent profile keys deserialize to the "not set" sentinels — A5, never a fabricated value.
        assertEquals(0f, restored.preferences.heightCm, 0.0001f)
        assertEquals("", restored.preferences.sex)
        assertEquals(5, restored.preferences.daysPerWeek)
    }

    @Test fun `a very old v1 backup still migrates all the way to v9`() {
        val v1 = """{ "schema_version": 1, "sessions": [], "sets": [] }"""
        val restored = BackupMigrations.parseAndMigrate(gson, v1)
        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
        assertTrue(restored.bodyMetrics.isEmpty())
        assertEquals(0f, restored.preferences.heightCm, 0.0001f)
    }

    @Test fun `an unset profile in a restore never invents a height or sex`() {
        val d = BackupPreferences()
        assertEquals(0f, d.heightCm, 0.0001f)
        assertEquals("", d.sex)
        assertTrue(!BodyComposition.isKnownSex(d.sex))
    }

    // ── Merge rules ───────────────────────────────────────────────────────────────────────────

    @Test fun `body metrics merge as a union, id-collision-safe`() {
        val existing = listOf(
            BodyMetric(1, 1000, waistCm = 88f),
            BodyMetric(2, 2000, waistCm = 87f)
        )
        val backup = listOf(
            BodyMetric(2, 2000, waistCm = 87f),   // value duplicate -> skipped
            BodyMetric(2, 3000, waistCm = 86f),   // id collides, different content -> re-keyed
            BodyMetric(9, 4000, waistCm = 85f)    // brand new
        )
        val merged = BackupMerger.mergeBodyMetrics(existing, backup)

        assertEquals(4, merged.size)
        assertEquals("ids stay unique after the merge", 4, merged.map { it.id }.toSet().size)
        assertTrue(merged.any { it.dateMs == 3000L && it.waistCm == 86f })
        assertTrue(merged.any { it.id == 9L && it.dateMs == 4000L })
        // Nothing existing was lost or overwritten.
        assertTrue(merged.containsAll(existing))
    }

    @Test fun `metric content key distinguishes entries that differ only in one girth`() {
        val existing = listOf(BodyMetric(1, 1000, waistCm = 88f))
        val backup = listOf(BodyMetric(1, 1000, waistCm = 88f, neckCm = 38f))
        // Same date and waist, different neck -> a genuinely different entry, must be kept.
        assertEquals(2, BackupMerger.mergeBodyMetrics(existing, backup).size)
    }

    @Test fun `profile prefs merge phone-wins-if-set`() {
        val phoneHasProfile = BackupPreferences(heightCm = 182f, sex = BodyComposition.SEX_MALE)
        val backupHasOther = BackupPreferences(heightCm = 165f, sex = BodyComposition.SEX_FEMALE)

        val kept = PreferencesMerger.merge(phoneHasProfile, backupHasOther)
        assertEquals(182f, kept.heightCm, 0.0001f)
        assertEquals(BodyComposition.SEX_MALE, kept.sex)
    }

    @Test fun `a phone that never set a profile inherits it from the backup`() {
        val blankPhone = BackupPreferences()
        val backup = BackupPreferences(heightCm = 165f, sex = BodyComposition.SEX_FEMALE)

        val restored = PreferencesMerger.merge(blankPhone, backup)
        assertEquals(165f, restored.heightCm, 0.0001f)
        assertEquals(BodyComposition.SEX_FEMALE, restored.sex)
    }
}
