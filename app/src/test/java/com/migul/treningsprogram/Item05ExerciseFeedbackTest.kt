package com.migul.treningsprogram

import com.google.gson.Gson
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.BackupMerger
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import com.migul.treningsprogram.data.db.entity.ExerciseFeedback
import com.migul.treningsprogram.domain.ExerciseFeedbackCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 05 (2026-08-06) — per-exercise feedback that shapes the next generated program.
 *
 * Covers the two halves that are provable off-device: what the generator is TOLD
 * ([ExerciseFeedbackCatalog.promptBlock]) and that the data survives a backup round-trip while
 * older backups still import. The UI surfaces (info sheet, Settings review screen) are the user's
 * device check.
 */
class Item05ExerciseFeedbackTest {

    private val DAY = 86_400_000L
    private val NOW = 1_800_000_000_000L

    private fun fb(name: String, reason: String, note: String = "", ageDays: Int = 1) =
        ExerciseFeedback(name, reason, note, NOW - ageDays * DAY)

    // ── The core promise: hint, never a filter; keep-and-build-toward ────────────────────────

    @Test fun block_saysItIsAHintAndNotAFilter() {
        val block = ExerciseFeedbackCatalog.promptBlock(
            listOf(fb("Dumbbell Single-Leg RDL", ExerciseFeedbackCatalog.TOO_HARD)), NOW
        )
        assertTrue(block.contains("HINT"))
        assertTrue(block.contains("NOT a filter"))
        assertTrue("nothing may read as a ban", block.contains("None of these exercises is banned"))
    }

    @Test fun tooHard_instructsKeepAndProgramToward_neverDrop() {
        // Confirmed decision 5a-ii and the whole point of the feature: the default response to
        // "can't do this yet" is to KEEP the exercise and build toward it, not to remove it.
        val block = ExerciseFeedbackCatalog.promptBlock(
            listOf(fb("Dumbbell Single-Leg RDL", ExerciseFeedbackCatalog.TOO_HARD)), NOW
        )
        assertTrue(block.contains("KEEP this exercise"))
        assertTrue(block.contains("program TOWARD it"))
        assertTrue(block.contains("regression or a supporting movement"))
        assertTrue(block.contains("Do NOT silently drop it"))
    }

    @Test fun block_scopesFeedbackToTheNamedExerciseOnly() {
        // Confirmed decision 5e: applies to that exercise alone, not to similar movements.
        val block = ExerciseFeedbackCatalog.promptBlock(
            listOf(fb("Zottman Curl", ExerciseFeedbackCatalog.DISLIKE)), NOW
        )
        assertTrue(block.contains("ONLY to the exact exercise named"))
        assertTrue(block.contains("not to similar movements"))
    }

    @Test fun freeTextIsCarriedAndMarkedActionable() {
        val block = ExerciseFeedbackCatalog.promptBlock(
            listOf(fb("Barbell Back Squat", ExerciseFeedbackCatalog.EQUIPMENT, note = "rack is always busy")), NOW
        )
        assertTrue(block.contains("rack is always busy"))
        assertTrue(block.contains("act on this if it asks for a change or a swap"))
    }

    @Test fun blankNoteAddsNoLine() {
        val block = ExerciseFeedbackCatalog.promptBlock(
            listOf(fb("Barbell Back Squat", ExerciseFeedbackCatalog.LOVE, note = "   ")), NOW
        )
        assertFalse(block.contains("User's own words"))
    }

    // ── Decision D3: the AI is told WHEN feedback was given ──────────────────────────────────

    @Test fun ageIsReportedAndOldFeedbackIsMarkedStale() {
        assertEquals("given today", ExerciseFeedbackCatalog.agePhrase(NOW, NOW))
        assertEquals("given yesterday", ExerciseFeedbackCatalog.agePhrase(NOW - DAY, NOW))
        assertEquals("given 5 days ago", ExerciseFeedbackCatalog.agePhrase(NOW - 5 * DAY, NOW))

        val old = ExerciseFeedbackCatalog.agePhrase(
            NOW - (ExerciseFeedbackCatalog.STALE_AFTER_DAYS + 60) * DAY, NOW
        )
        assertTrue(old.contains("old"))
        assertTrue("stale feedback must be explicitly down-weighted", old.contains("weigh it lightly"))
    }

    @Test fun block_reportsTheAgeOfEachEntry() {
        val block = ExerciseFeedbackCatalog.promptBlock(
            listOf(fb("Pistol Squat", ExerciseFeedbackCatalog.TOO_HARD, ageDays = 12)), NOW
        )
        assertTrue(block.contains("given 12 days ago"))
    }

    // ── Robustness ──────────────────────────────────────────────────────────────────────────

    @Test fun noFeedback_producesNoBlockAtAll() {
        // A user who never uses the feature must get a byte-identical prompt to before it existed.
        assertEquals("", ExerciseFeedbackCatalog.promptBlock(emptyList(), NOW))
    }

    @Test fun unknownReasonKeysAreSkipped_notRenderedRaw() {
        // A row written by a newer build and restored into an older one must not leak a raw key.
        val block = ExerciseFeedbackCatalog.promptBlock(
            listOf(
                fb("Mystery Lift", "SOME_FUTURE_REASON"),
                fb("Bench Press", ExerciseFeedbackCatalog.TOO_EASY)
            ), NOW
        )
        assertFalse(block.contains("SOME_FUTURE_REASON"))
        assertFalse(block.contains("Mystery Lift"))
        assertTrue(block.contains("Bench Press"))
    }

    @Test fun blankExerciseNamesAreSkipped() {
        assertEquals("", ExerciseFeedbackCatalog.promptBlock(
            listOf(fb("   ", ExerciseFeedbackCatalog.LOVE)), NOW
        ))
    }

    @Test fun blockIsBounded_newestFirst() {
        // Feedback persists until removed (D3), so the block must not grow without limit — this app
        // has a documented history of generation trouble driven by prompt size.
        val many = (1..40).map { fb("Lift $it", ExerciseFeedbackCatalog.LOVE, ageDays = it) }
        val block = ExerciseFeedbackCatalog.promptBlock(many, NOW)
        val listed = (1..40).count { block.contains("Lift $it ") }
        assertEquals(ExerciseFeedbackCatalog.MAX_PROMPT_ENTRIES, listed)
        assertTrue("newest must survive the cap", block.contains("Lift 1 "))
        assertFalse("oldest must be dropped", block.contains("Lift 40 "))
    }

    @Test fun everyReasonHasADistinctKeyLabelAndActionableGuidance() {
        val reasons = ExerciseFeedbackCatalog.REASONS
        assertEquals(6, reasons.size)
        assertEquals(reasons.size, reasons.map { it.key }.toSet().size)
        assertEquals(reasons.size, reasons.map { it.label }.toSet().size)
        reasons.forEach {
            assertTrue("${it.key} needs guidance", it.aiGuidance.length > 20)
            assertNotNull(ExerciseFeedbackCatalog.reasonFor(it.key))
            assertEquals(it.label, ExerciseFeedbackCatalog.labelFor(it.key))
        }
    }

    // ── Backup: round-trips at v10, and older backups still import ───────────────────────────

    @Test fun roundTripsAtCurrentVersion() {
        val gson = Gson()
        val envelope = BackupEnvelope(
            schemaVersion = CURRENT_BACKUP_VERSION,
            exportedAt = "2026-08-06T10:00:00Z",
            exerciseFeedback = listOf(
                fb("Dumbbell Single-Leg RDL", ExerciseFeedbackCatalog.TOO_HARD, "no balance"),
                fb("Bench Press", ExerciseFeedbackCatalog.LOVE)
            )
        )
        val restored = BackupMigrations.parseAndMigrate(gson, gson.toJson(envelope))
        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
        assertEquals(2, restored.exerciseFeedback.size)
        val rdl = restored.exerciseFeedback.first { it.exerciseName.contains("RDL") }
        assertEquals(ExerciseFeedbackCatalog.TOO_HARD, rdl.reasonKey)
        assertEquals("no balance", rdl.note)
        assertTrue(rdl.updatedAtMs > 0L)
    }

    @Test fun aV9BackupStillImports_andArrivesWithNoFeedback() {
        // Older backups predate the feature; "no feedback given yet" is the correct restored state.
        val v9 = """{"schema_version":9,"exported_at":"2026-08-04T10:00:00Z","sessions":[],"sets":[]}"""
        val restored = BackupMigrations.parseAndMigrate(Gson(), v9)
        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
        assertTrue(restored.exerciseFeedback.isEmpty())
    }

    @Test fun aVeryOldBackupStillComposesAllTheWayUp() {
        val v1 = """{"schema_version":1,"exported_at":"2026-06-01T10:00:00Z","sessions":[],"sets":[]}"""
        val restored = BackupMigrations.parseAndMigrate(Gson(), v1)
        assertEquals(CURRENT_BACKUP_VERSION, restored.schemaVersion)
        assertTrue(restored.exerciseFeedback.isEmpty())
    }

    // ── Backup merge: one entry per exercise, most recent wins ───────────────────────────────

    @Test fun merge_keepsOneEntryPerExercise_mostRecentWins() {
        val existing = listOf(fb("Bench Press", ExerciseFeedbackCatalog.TOO_HARD, ageDays = 30))
        val backup = listOf(fb("Bench Press", ExerciseFeedbackCatalog.TOO_EASY, ageDays = 2))
        val merged = BackupMerger.mergeExerciseFeedback(existing, backup)
        assertEquals(1, merged.size)
        assertEquals(ExerciseFeedbackCatalog.TOO_EASY, merged.single().reasonKey)
    }

    @Test fun merge_keepsTheDeviceEntryWhenTheBackupIsOlder() {
        val existing = listOf(fb("Bench Press", ExerciseFeedbackCatalog.LOVE, ageDays = 1))
        val backup = listOf(fb("Bench Press", ExerciseFeedbackCatalog.PAIN, ageDays = 90))
        assertEquals(
            ExerciseFeedbackCatalog.LOVE,
            BackupMerger.mergeExerciseFeedback(existing, backup).single().reasonKey
        )
    }

    @Test fun merge_matchesNamesCaseInsensitively_andUnionsDistinctExercises() {
        val existing = listOf(fb("bench press", ExerciseFeedbackCatalog.LOVE, ageDays = 30))
        val backup = listOf(
            fb("Bench Press", ExerciseFeedbackCatalog.TOO_EASY, ageDays = 1),
            fb("Pistol Squat", ExerciseFeedbackCatalog.TOO_HARD, ageDays = 1)
        )
        val merged = BackupMerger.mergeExerciseFeedback(existing, backup)
        assertEquals(2, merged.size)
        assertEquals(
            ExerciseFeedbackCatalog.TOO_EASY,
            merged.first { it.exerciseName.equals("bench press", ignoreCase = true) }.reasonKey
        )
    }
}
