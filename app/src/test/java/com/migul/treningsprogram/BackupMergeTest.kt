package com.migul.treningsprogram

import com.migul.treningsprogram.data.backup.BackupMerger
import com.migul.treningsprogram.data.backup.BackupPreferences
import com.migul.treningsprogram.data.backup.PreferencesMerger
import com.migul.treningsprogram.data.backup.StatsRecomputer
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.Exercise
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.data.repository.GamificationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {

    // ---- Sessions + sets: id-collision-safe UNION ----------------------------------------------

    private fun session(id: Long, dateMs: Long, completed: Boolean = true, notes: String = "") =
        WorkoutSession(id = id, dateMs = dateMs, durationMinutes = 30, notes = notes, isCompleted = completed)

    private fun set(id: Long, sessionId: Long, name: String, reps: Int, weight: Float, warmup: Boolean = false, setNo: Int = 1) =
        WorkoutSet(id = id, sessionId = sessionId, exerciseName = name, muscleGroup = "", setNumber = setNo, reps = reps, weightKg = weight, isWarmup = warmup)

    @Test fun mergeWorkouts_unionWithNoLoss() {
        val existing = listOf(session(1, 1000))
        val existingSets = listOf(set(1, 1, "Squat", 5, 100f))
        val backupSession = session(5, 2000)
        val backupSets = listOf(set(9, 5, "Bench", 5, 80f))

        val merged = BackupMerger.mergeWorkouts(
            existing, existingSets,
            listOf(BackupMerger.SessionWithSets(backupSession, backupSets))
        )
        assertEquals(2, merged.sessions.size)
        assertEquals(2, merged.sets.size)
        // Bench set still points at its session.
        val bench = merged.sets.first { it.exerciseName == "Bench" }
        val benchSession = merged.sessions.first { it.id == bench.sessionId }
        assertEquals(2000L, benchSession.dateMs)
    }

    @Test fun mergeWorkouts_valueDuplicateIsSkipped() {
        // Same content on both sides (different surrogate id) -> one row, not two.
        val existing = listOf(session(1, 1000, notes = "x"))
        val existingSets = listOf(set(1, 1, "Squat", 5, 100f))
        val dupSession = session(99, 1000, notes = "x")   // same content, different id
        val dupSets = listOf(set(50, 99, "Squat", 5, 100f)) // same content after relink

        val merged = BackupMerger.mergeWorkouts(
            existing, existingSets,
            listOf(BackupMerger.SessionWithSets(dupSession, dupSets))
        )
        assertEquals(1, merged.sessions.size)
        assertEquals(1, merged.sets.size)
    }

    @Test fun mergeWorkouts_collidingIdDoesNotOverwriteDifferentExisting() {
        // Backup session id == existing id, but DIFFERENT content. Existing must survive untouched
        // and the backup row must be re-keyed.
        val existing = listOf(session(1, 1000, notes = "EXISTING"))
        val existingSets = listOf(set(1, 1, "Squat", 5, 100f))
        val collide = session(1, 5000, notes = "FROM_BACKUP")    // same id, different content
        val collideSets = listOf(set(1, 1, "Deadlift", 3, 140f)) // same id too

        val merged = BackupMerger.mergeWorkouts(
            existing, existingSets,
            listOf(BackupMerger.SessionWithSets(collide, collideSets))
        )
        // Both sessions present.
        assertEquals(2, merged.sessions.size)
        // The original id=1 still has the EXISTING content.
        assertEquals("EXISTING", merged.sessions.first { it.id == 1L }.notes)
        // The backup session got a fresh id and kept its content.
        val rekeyed = merged.sessions.first { it.notes == "FROM_BACKUP" }
        assertTrue(rekeyed.id != 1L)
        // Its Deadlift set follows the remap (no orphan), and the original Squat set untouched.
        val deadlift = merged.sets.first { it.exerciseName == "Deadlift" }
        assertEquals(rekeyed.id, deadlift.sessionId)
        assertTrue(merged.sets.any { it.exerciseName == "Squat" && it.sessionId == 1L })
        // No two sets share an id.
        assertEquals(merged.sets.size, merged.sets.map { it.id }.toSet().size)
    }

    // ---- BodyMeasurement: UNION ----------------------------------------------------------------

    @Test fun mergeBodyMeasurements_unionNoLossNoOverwrite() {
        val existing = listOf(BodyMeasurement(1, 1000, 80f), BodyMeasurement(2, 2000, 81f))
        val backup = listOf(
            BodyMeasurement(2, 2000, 81f),   // value duplicate -> skipped
            BodyMeasurement(2, 3000, 79f),   // id collides, different content -> re-keyed
            BodyMeasurement(9, 4000, 78f)    // brand new
        )
        val merged = BackupMerger.mergeBodyMeasurements(existing, backup)
        // 2 existing + 2 genuinely new = 4.
        assertEquals(4, merged.size)
        assertEquals(merged.size, merged.map { it.id }.toSet().size)
        // Original id=2 still has weight 81.
        assertEquals(81f, merged.first { it.id == 2L }.weightKg, 0f)
    }

    // ---- Achievement: UNION, unlocked-wins, earliest unlock ------------------------------------

    @Test fun mergeAchievements_keepsUnlocksFromBothSides() {
        val existing = listOf(
            Achievement("a", "A", "d", "x", isUnlocked = true, unlockedAtMs = 500),
            Achievement("b", "B", "d", "x", isUnlocked = false, unlockedAtMs = 0)
        )
        val backup = listOf(
            Achievement("b", "B", "d", "x", isUnlocked = true, unlockedAtMs = 700),  // unlock only in backup
            Achievement("c", "C", "d", "x", isUnlocked = true, unlockedAtMs = 900)   // backup-only achievement
        )
        val merged = BackupMerger.mergeAchievements(existing, backup).associateBy { it.id }
        assertTrue(merged["a"]!!.isUnlocked)
        assertTrue("unlock must not be lost", merged["b"]!!.isUnlocked)
        assertEquals(700L, merged["b"]!!.unlockedAtMs)
        assertNotNull(merged["c"])
        assertTrue(merged["c"]!!.isUnlocked)
    }

    @Test fun mergeAchievements_earliestUnlockWins() {
        val existing = listOf(Achievement("a", "A", "d", "x", isUnlocked = true, unlockedAtMs = 900))
        val backup = listOf(Achievement("a", "A", "d", "x", isUnlocked = true, unlockedAtMs = 300))
        val merged = BackupMerger.mergeAchievements(existing, backup)
        assertEquals(300L, merged.first { it.id == "a" }.unlockedAtMs)
    }

    // ---- PlannedExercise: newest-generated-plan-per-week wins -----------------------------------

    private fun planned(week: Long, name: String, resolvedAt: Long) = PlannedExercise(
        weekStart = week, dayOfWeek = 1, orderInDay = 0, exerciseName = name,
        sets = 3, targetReps = "5", targetWeightKg = 100f, resolvedAt = resolvedAt
    )

    @Test fun mergePlanned_newerPlanReplacesWholeWeek() {
        val existing = listOf(planned(7000, "OldA", 100L), planned(7000, "OldB", 100L))
        val backup = listOf(planned(7000, "NewA", 500L)) // newer resolvedAt -> backup wins this week
        val merged = BackupMerger.mergePlannedExercises(existing, backup)
        assertEquals(listOf("NewA"), merged.filter { it.weekStart == 7000L }.map { it.exerciseName })
    }

    @Test fun mergePlanned_olderBackupDoesNotReplace() {
        val existing = listOf(planned(7000, "Current", 500L))
        val backup = listOf(planned(7000, "Stale", 100L))
        val merged = BackupMerger.mergePlannedExercises(existing, backup)
        assertEquals(listOf("Current"), merged.filter { it.weekStart == 7000L }.map { it.exerciseName })
    }

    @Test fun mergePlanned_disjointWeeksBothKept() {
        val existing = listOf(planned(7000, "WeekA", 100L))
        val backup = listOf(planned(14000, "WeekB", 100L))
        val merged = BackupMerger.mergePlannedExercises(existing, backup)
        assertEquals(setOf("WeekA", "WeekB"), merged.map { it.exerciseName }.toSet())
    }

    @Test fun mergePlanned_tieFallsBackToExisting() {
        val existing = listOf(planned(7000, "Existing", 0L))
        val backup = listOf(planned(7000, "Backup", 0L))
        val merged = BackupMerger.mergePlannedExercises(existing, backup)
        assertEquals(listOf("Existing"), merged.map { it.exerciseName })
    }

    // ---- Exercise library: UNION by name -------------------------------------------------------

    @Test fun mergeExercises_unionByNameExistingWins() {
        val existing = listOf(Exercise(1, "Squat", "Legs", "Barbell"))
        val backup = listOf(
            Exercise(1, "squat", "Legs", "DIFFERENT"),  // same name (case-insensitive) -> existing wins
            Exercise(1, "Custom", "Back", "Cable")       // id collides but new name -> re-keyed
        )
        val merged = BackupMerger.mergeExercises(existing, backup)
        assertEquals(2, merged.size)
        assertEquals("Barbell", merged.first { it.name == "Squat" }.equipment)
        val custom = merged.first { it.name == "Custom" }
        assertTrue(custom.id != 1L)
    }

    // ---- GymPreset: UNION + id remap -----------------------------------------------------------

    @Test fun mergeGymPresets_unionAndRemap() {
        val existing = listOf(GymPreset(1, "Home", "[]", ""))
        val backup = listOf(
            GymPreset(2, "Home", "[]", ""),        // backup id 2, same content -> remaps to existing 1
            GymPreset(1, "Travel", "[\"DB\"]", "") // backup id 1 collides with existing, new content -> re-keyed
        )
        val res = BackupMerger.mergeGymPresets(existing, backup)
        assertEquals(2, res.presets.size)
        // The content-dup backup preset (id 2) maps onto the existing row id 1.
        assertEquals(1L, res.backupIdRemap[2L])
        // The colliding "Travel" (backup id 1) was re-keyed; its remap points at the new id.
        val travel = res.presets.first { it.name == "Travel" }
        assertTrue(travel.id != 1L)
        assertEquals(travel.id, res.backupIdRemap[1L])
    }

    // ---- Settings: per-field resolution --------------------------------------------------------

    @Test fun prefsMerge_phoneWinsWhenChangedFromDefault() {
        val phone = BackupPreferences(daysPerWeek = 6, fitnessGoal = "Strength")
        val backup = BackupPreferences(daysPerWeek = 3, fitnessGoal = "Endurance")
        val merged = PreferencesMerger.merge(phone, backup)
        assertEquals(6, merged.daysPerWeek)            // phone changed -> phone wins
        assertEquals("Strength", merged.fitnessGoal)   // phone changed -> phone wins
    }

    @Test fun prefsMerge_backupUsedWhenPhoneAtDefault() {
        val phone = BackupPreferences() // all defaults
        val backup = BackupPreferences(daysPerWeek = 3, restTimerSeconds = 120, injurySeverity = "Severe")
        val merged = PreferencesMerger.merge(phone, backup)
        assertEquals(3, merged.daysPerWeek)
        assertEquals(120, merged.restTimerSeconds)
        assertEquals("Severe", merged.injurySeverity)
    }

    @Test fun prefsMerge_ambiguousFieldsAreBackupWins() {
        // dailyChallengesJson + selectedGymPresetId -> backup wins even if phone has a value.
        val phone = BackupPreferences(dailyChallengesJson = "PHONE", selectedGymPresetId = 7L)
        val backup = BackupPreferences(dailyChallengesJson = "BACKUP", selectedGymPresetId = 3L)
        val merged = PreferencesMerger.merge(phone, backup)
        assertEquals("BACKUP", merged.dailyChallengesJson)
        assertEquals(3L, merged.selectedGymPresetId)
        assertTrue(PreferencesMerger.BACKUP_WINS_FIELDS.containsAll(listOf("dailyChallengesJson", "selectedGymPresetId")))
    }

    @Test fun prefsMerge_onboardingIsUnion() {
        val phone = BackupPreferences(hasCompletedOnboarding = false)
        val backup = BackupPreferences(hasCompletedOnboarding = true)
        assertTrue(PreferencesMerger.merge(phone, backup).hasCompletedOnboarding)
    }

    // ---- Stats: RECOMPUTE (never copied) -------------------------------------------------------

    @Test fun statsRecompute_isFromScratchNotCopied() {
        // Two completed sessions, second sets a PR on Squat.
        val sessions = listOf(session(1, dayMs(0)), session(2, dayMs(1)))
        val sets = listOf(
            set(1, 1, "Squat", 5, 100f),                 // baseline (not a PR)
            set(2, 1, "Warm", 5, 20f, warmup = true),    // warm-up: ignored for XP
            set(3, 2, "Squat", 5, 110f)                  // PR vs prior 100
        )
        val recomputed = StatsRecomputer.recompute(sessions, sets)

        // XP: session1 = 50 + (1 working set *5) + 0 PR = 55.
        //     session2 = 50 + (1 working set *5) + 30 PR = 85.  Total = 140.
        assertEquals(140, recomputed.totalXp)
        assertEquals(2, recomputed.totalWorkouts)
        assertEquals(1, recomputed.totalPrs)
        // Consecutive days -> streak 2.
        assertEquals(2, recomputed.currentStreak)
        assertEquals(2, recomputed.bestStreak)
        assertEquals(GamificationRepository.xpToLevel(140), recomputed.level)
    }

    @Test fun statsRecompute_ignoresSessionsWithoutWorkingSets() {
        val sessions = listOf(session(1, dayMs(0)), session(2, dayMs(1), completed = false))
        val sets = listOf(
            set(1, 1, "Squat", 5, 100f),
            set(2, 2, "Bench", 5, 80f) // belongs to an incomplete session -> not counted
        )
        val recomputed = StatsRecomputer.recompute(sessions, sets)
        assertEquals(1, recomputed.totalWorkouts)
        assertEquals(55, recomputed.totalXp)
    }

    @Test fun statsRecompute_warmupOnlySessionDoesNotCount() {
        val sessions = listOf(session(1, dayMs(0)))
        val sets = listOf(set(1, 1, "Squat", 5, 100f, warmup = true))
        val recomputed = StatsRecomputer.recompute(sessions, sets)
        assertEquals(0, recomputed.totalWorkouts)
        assertEquals(0, recomputed.totalXp)
        assertEquals(0, recomputed.currentStreak)
    }

    @Test fun statsRecompute_streakResetsOnGap() {
        val sessions = listOf(session(1, dayMs(0)), session(2, dayMs(5))) // 5-day gap
        val sets = listOf(set(1, 1, "Squat", 5, 100f), set(2, 2, "Squat", 5, 100f))
        val recomputed = StatsRecomputer.recompute(sessions, sets)
        assertEquals(1, recomputed.currentStreak) // last session resets streak to 1
        assertEquals(1, recomputed.bestStreak)
    }

    /** Day index converted to an ms timestamp at local noon (stable across the start-of-day calc). */
    private fun dayMs(dayOffset: Int): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JANUARY, 1, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        cal.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
        return cal.timeInMillis
    }
}
