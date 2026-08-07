package com.migul.treningsprogram.data.repository

import com.migul.treningsprogram.data.db.dao.BodyMeasurementDao
import com.migul.treningsprogram.data.db.dao.RatingSetRow
import com.migul.treningsprogram.data.db.dao.WorkoutSetDao
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.domain.strength.RatingSet
import com.migul.treningsprogram.domain.strength.StrengthProfile
import com.migul.treningsprogram.domain.strength.StrengthRating
import com.migul.treningsprogram.domain.strength.StrengthStandards
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brief 02 (2026-08-07) — the one place the app asks "how strong is this user".
 *
 * Ratings are **derived, never stored**: this reads logged history and the newest weigh-in and
 * hands them to the pure [StrengthRating] engine. Nothing is persisted, so there is no rating to
 * fall out of sync with history, and export → import round-trips a rating for free — the inputs
 * (workout sets, body measurements, sex) have all been in the backup since v1/v9 respectively.
 * This follows the precedent the app set for body fat in backup v9: derived values are recomputed
 * on read, not serialised.
 */
@Singleton
class StrengthRepository @Inject constructor(
    private val workoutSetDao: WorkoutSetDao,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val prefs: PreferencesManager,
) {

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }

    /** The user's current strength profile. */
    suspend fun currentProfile(nowMs: Long = System.currentTimeMillis()): StrengthProfile =
        profile(rows = ratingRows(nowMs), excludeSessionId = null, nowMs = nowMs)

    /**
     * The profile as it stood **before** [sessionId] was logged, alongside the current one.
     *
     * Used to decide whether the just-finished workout moved a tier (decision D3 — the level-up
     * celebration now fires on a strength tier-up). Both sides are computed from the same row set
     * and the same `now`, so the only difference between them is the session itself — the
     * three-month window cannot slide underneath the comparison and manufacture a phantom tier-up.
     */
    suspend fun profileAroundSession(
        sessionId: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<StrengthProfile, StrengthProfile> {
        val rows = ratingRows(nowMs)
        return profile(rows, excludeSessionId = sessionId, nowMs = nowMs) to
            profile(rows, excludeSessionId = null, nowMs = nowMs)
    }

    private suspend fun ratingRows(nowMs: Long): List<RatingSetRow> =
        workoutSetDao.getRatingSets(nowMs - StrengthStandards.WINDOW_DAYS * DAY_MS)

    private suspend fun profile(
        rows: List<RatingSetRow>,
        excludeSessionId: Long?,
        nowMs: Long,
    ): StrengthProfile {
        // A4: body weight is the most recent weigh-in. The engine rejects it as stale if it
        // predates the rating window rather than rating against a number that is no longer true.
        val newest = bodyMeasurementDao.getAllOnce().maxByOrNull { it.dateMs }
        return StrengthRating.rate(
            sets = rows
                .filter { excludeSessionId == null || it.sessionId != excludeSessionId }
                .map { RatingSet(it.exerciseName, it.weightKg, it.reps, it.dateMs) },
            bodyWeightKg = newest?.weightKg,
            bodyWeightDateMs = newest?.dateMs,
            sex = prefs.sex,
            nowMs = nowMs,
        )
    }
}
