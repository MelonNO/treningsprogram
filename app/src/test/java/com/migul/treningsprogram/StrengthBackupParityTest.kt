package com.migul.treningsprogram

import com.google.gson.GsonBuilder
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.BackupPreferences
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import com.migul.treningsprogram.data.backup.StatsRecomputer
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.domain.BodyComposition
import com.migul.treningsprogram.domain.strength.RatingSet
import com.migul.treningsprogram.domain.strength.StrengthRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Brief 02 (2026-08-07) — a rating must survive export → import unchanged, and the recompute path
 * that rebuilds stats after a merge must not disagree with the live path.
 *
 * The design that makes both true is that **ratings are derived, never stored** — the same
 * precedent backup v9 set for body fat ("body fat is NOT serialized - it is derived on read").
 * There is no rating column to drift out of sync, `StatsRecomputer` has nothing extra to rebuild,
 * and no schema or backup version had to move. What these tests actually prove is that the three
 * INPUTS a rating is computed from all round-trip, and that recomputing on the far side reproduces
 * the near side exactly.
 */
class StrengthBackupParityTest {

    private val gson = GsonBuilder().create()
    private val NOW = 1_700_000_000_000L
    private val DAY = 24L * 60L * 60L * 1000L

    private fun envelope() = BackupEnvelope(
        schemaVersion = CURRENT_BACKUP_VERSION,
        exportedAt = "2026-08-07T10:00:00Z",
        sessions = listOf(
            WorkoutSession(id = 1, dateMs = NOW - 5 * DAY, durationMinutes = 60, notes = "", isCompleted = true),
        ),
        sets = listOf(
            WorkoutSet(id = 1, sessionId = 1, exerciseName = "Bench Press", muscleGroup = "Chest",
                setNumber = 1, reps = 5, weightKg = 100f, loggedAtMs = NOW - 5 * DAY),
            WorkoutSet(id = 2, sessionId = 1, exerciseName = "Back Squat", muscleGroup = "Legs",
                setNumber = 1, reps = 5, weightKg = 120f, loggedAtMs = NOW - 5 * DAY),
            // A warm-up, which must never rate on EITHER side of the round-trip.
            WorkoutSet(id = 3, sessionId = 1, exerciseName = "Bench Press", muscleGroup = "Chest",
                setNumber = 0, reps = 3, weightKg = 140f, isWarmup = true, loggedAtMs = NOW - 5 * DAY),
        ),
        userStats = UserStats(id = 1, totalXp = 500, level = 2, currentStreak = 3, bestStreak = 5,
            totalWorkouts = 4, totalPrs = 2, lastWorkoutDateMs = NOW - 5 * DAY),
        bodyMeasurements = listOf(BodyMeasurement(id = 1, dateMs = NOW - 3 * DAY, weightKg = 80f)),
        preferences = BackupPreferences(sex = BodyComposition.SEX_MALE, heightCm = 180f),
    )

    /** The engine's view of an envelope, exactly as StrengthRepository assembles it at runtime. */
    private fun rateFrom(env: BackupEnvelope) = StrengthRating.rate(
        sets = env.sets
            .filter { !it.isWarmup && it.reps > 0 }
            .mapNotNull { set ->
                val session = env.sessions.firstOrNull { it.id == set.sessionId } ?: return@mapNotNull null
                if (!session.isCompleted) return@mapNotNull null
                RatingSet(set.exerciseName, set.weightKg, set.reps, session.dateMs)
            },
        bodyWeightKg = env.bodyMeasurements.maxByOrNull { it.dateMs }?.weightKg,
        bodyWeightDateMs = env.bodyMeasurements.maxByOrNull { it.dateMs }?.dateMs,
        sex = env.preferences.sex,
        nowMs = NOW,
    )

    @Test fun aRatingIsIdenticalOnBothSidesOfAnExportImportRoundTrip() {
        val original = envelope()
        val before = rateFrom(original)

        val restored = BackupMigrations.parseAndMigrate(gson, gson.toJson(original))
        val after = rateFrom(restored)

        assertNotNull("precondition: the fixture must actually produce a rating", before.totalTier)
        assertEquals(before.totalTier, after.totalTier)
        assertEquals(before.totalScore, after.totalScore, 1e-6f)
        assertEquals(before.strengthScore, after.strengthScore)
        assertEquals(before.weakestRatedGroup, after.weakestRatedGroup)
        assertEquals(
            before.groups.map { it.group to it.tier },
            after.groups.map { it.group to it.tier },
        )
    }

    @Test fun allThreeRatingInputsAreCarriedByTheBackup() {
        val restored = BackupMigrations.parseAndMigrate(gson, gson.toJson(envelope()))
        // 1. the working sets (kilos and reps), 2. the weigh-in, 3. sex.
        assertTrue(restored.sets.any { it.exerciseName == "Bench Press" && it.weightKg == 100f })
        assertEquals(80f, restored.bodyMeasurements.single().weightKg, 1e-4f)
        assertEquals(BodyComposition.SEX_MALE, restored.preferences.sex)
    }

    @Test fun warmupsAreIgnoredOnBothSidesSoTheyCannotInflateARestoredRating() {
        val withWarmup = envelope()
        val withoutWarmup = withWarmup.copy(sets = withWarmup.sets.filter { !it.isWarmup })
        assertEquals(rateFrom(withoutWarmup).totalScore, rateFrom(withWarmup).totalScore, 1e-6f)
    }

    /**
     * The rating adds NOTHING to the backup format. No new table, no new preference, no envelope
     * field — so `CURRENT_BACKUP_VERSION` does not move and every older backup keeps importing
     * through the existing migration chain untouched.
     */
    @Test fun theRatingRequiredNoBackupVersionBump() {
        assertEquals(10, CURRENT_BACKUP_VERSION)
    }

    @Test fun anOlderBackupStillImportsAndSimplyRatesFromWhateverItCarries() {
        val v1 = """{"sessions":[],"sets":[],"achievements":[],"body_measurements":[]}"""
        val restored = BackupMigrations.parseAndMigrate(gson, v1)
        val rating = rateFrom(restored)
        // No sex and no weigh-in in a v1 export: unrated with a reason, never a guess, never a crash.
        assertNotNull(rating.profileUnratedReason)
        assertEquals(null, rating.totalTier)
    }

    /**
     * `StatsRecomputer` is the merge-path rebuild of `UserStats`. It must stay entirely unaware of
     * strength — the moment it starts deriving a rating, the merge path and the live path become
     * two implementations that can disagree, which is exactly the parity failure this design avoids.
     */
    @Test fun theRecomputePathDoesNotDeriveARatingAtAll() {
        val env = envelope()
        val recomputed = StatsRecomputer.recompute(env.sessions, env.sets, emptyList(), emptyList())
        // It still rebuilds XP and the vestigial level column, and nothing more.
        assertTrue(recomputed.totalXp > 0)
        assertEquals(1, recomputed.totalWorkouts)
    }

    /**
     * The rating follows the RESTORED body weight, not the exporting device's. Restoring onto a
     * phone whose owner has since gained weight must show the lower rating — that is what relative
     * strength means, and the user accepted it explicitly.
     */
    @Test fun aRestoredBackupRatesAgainstTheBodyWeightItCarries() {
        val lighter = envelope()
        val heavier = lighter.copy(
            bodyMeasurements = listOf(BodyMeasurement(id = 1, dateMs = NOW - 3 * DAY, weightKg = 100f))
        )
        assertTrue(rateFrom(heavier).totalScore < rateFrom(lighter).totalScore)
    }
}
