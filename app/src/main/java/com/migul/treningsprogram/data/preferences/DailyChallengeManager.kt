package com.migul.treningsprogram.data.preferences

import com.google.gson.Gson
import com.migul.treningsprogram.data.db.dao.PlannedExerciseDao
import com.migul.treningsprogram.data.db.dao.ProgramDao
import com.migul.treningsprogram.data.db.dao.WorkoutSessionDao
import com.migul.treningsprogram.data.db.dao.WorkoutSetDao
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.data.repository.thisMonday
import com.migul.treningsprogram.domain.ChallengeCatalog
import com.migul.treningsprogram.domain.ChallengeContext
import com.migul.treningsprogram.domain.ChallengeProgress
import com.migul.treningsprogram.domain.model.DailyChallenge
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private data class DailyChallengesState(val date: String, val challenges: List<DailyChallenge>)  // 'date' field now holds ISO week key

/**
 * R4 — weekly challenges 2.0. The pool, eligibility and draw live in the pure
 * [ChallengeCatalog]; progress semantics live in [ChallengeProgress]; this manager does the
 * I/O: builds the draw-time [ChallengeContext] from the DB, persists the week's draw (targets
 * frozen), and folds completed sessions into week-scoped progress.
 *
 * Upgrade path: pre-R4 persisted JSON has no metric metadata (metric == null after Gson) — the
 * manager re-rolls that week once (explicitly allowed by the brief). Completion state stays
 * persisted per challenge inside the stored draw, so nothing double-awards within one draw.
 */
@Singleton
class DailyChallengeManager @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val gson: Gson,
    private val workoutSetDao: WorkoutSetDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val plannedExerciseDao: PlannedExerciseDao,
    private val programDao: ProgramDao
) {
    suspend fun getTodayChallenges(): List<DailyChallenge> {
        val thisWeek = isoWeekKey()
        val stored = preferencesManager.dailyChallengesJson
        if (stored.isNotEmpty()) {
            runCatching {
                val state = gson.fromJson(stored, DailyChallengesState::class.java)
                // metric == null on any row ⇒ pre-R4 state ⇒ one-time re-roll on upgrade.
                if (state.date == thisWeek && state.challenges.none { it.metric == null }) {
                    return state.challenges
                }
            }
        }
        val selected = ChallengeCatalog.draw(thisWeek, buildContext())
        saveChallenges(thisWeek, selected)
        return selected
    }

    /**
     * Folds one completed session (working sets only + its PR exercise names) into the week's
     * challenges. Returns the NEWLY completed ones (for XP + the celebration surface).
     */
    suspend fun completeChallenges(sets: List<WorkoutSet>, prExercises: List<String>): List<DailyChallenge> {
        val thisWeek = isoWeekKey()
        val current = getTodayChallenges()
        val newlyCompleted = mutableListOf<DailyChallenge>()
        val updated = current.map { ch ->
            if (ch.isCompleted) return@map ch
            val after = ChallengeProgress.afterSession(ch, sets, prExercises)
            if (after.isCompleted) newlyCompleted.add(after)
            after
        }
        saveChallenges(thisWeek, updated)
        return newlyCompleted
    }

    /**
     * Draw-time snapshot of the user's training state. Two indexed range queries + the week's
     * plan; runs once per week (plus once per pre-R4 upgrade re-roll).
     */
    private suspend fun buildContext(): ChallengeContext {
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val weekStartMs = thisMonday()
        val nowMs = System.currentTimeMillis()

        val lastWeekSets = workoutSetDao.getWorkingSetsBetween(weekStartMs - weekMs, weekStartMs)
        val recentSets = workoutSetDao.getWorkingSetsBetween(nowMs - 4 * weekMs, nowMs + 1)

        val recentBySession = recentSets.groupBy { it.sessionId }
        val sessionVolumes = recentBySession.values.map { s -> s.sumOf { it.reps.toDouble() * it.weightKg }.toInt() }
        val sessionSetCounts = recentBySession.values.map { it.size }

        val programId = programDao.getActiveOnce()?.id
        val weekPlan = if (programId != null) plannedExerciseDao.getForWeekInProgramOnce(programId, weekStartMs)
                       else plannedExerciseDao.getForWeekOnce(weekStartMs)

        // PR-able exercises: recent weighted history; the ones in this week's plan first (a PR
        // target the plan won't even visit is a bad challenge). Sorted + capped for determinism.
        val weighted = recentSets.filter { it.weightKg > 0f }.map { it.exerciseName }.distinct()
        val planNames = weekPlan.map { it.exerciseName }.toSet()
        val baseline = weighted.filter { it in planNames }.ifEmpty { weighted }

        return ChallengeContext(
            hasHistory = workoutSessionDao.getRecentCompleted(1).isNotEmpty(),
            lastWeekWorkingSets = lastWeekSets.size,
            lastWeekVolumeKg = lastWeekSets.sumOf { it.reps.toDouble() * it.weightKg }.toInt(),
            avgSessionVolumeKg = if (sessionVolumes.isEmpty()) 0 else sessionVolumes.average().toInt(),
            maxRecentSessionSets = sessionSetCounts.maxOrNull() ?: 0,
            maxRecentSessionVolumeKg = sessionVolumes.maxOrNull() ?: 0,
            plannedDayCount = weekPlan.map { it.dayOfWeek }.distinct().size,
            baselineExercises = baseline.sorted().take(12)
        )
    }

    private fun saveChallenges(weekKey: String, challenges: List<DailyChallenge>) {
        preferencesManager.dailyChallengesJson = gson.toJson(DailyChallengesState(weekKey, challenges))
    }
}

/**
 * Locale-independent ISO-week key (e.g. "2026-W26") scoping the weekly challenge rotation.
 *
 * Uses Locale.ROOT with explicit Monday-first / minimal-4-days-in-first-week rules so the key
 * — and the RNG seed derived from it — is identical regardless of the device locale, and always
 * uses Latin digits. For Monday-first / ISO locales this is byte-identical to the previous
 * default-locale "yyyy-'W'ww" formatting, so existing stored keys still match and challenges do
 * not re-roll on upgrade. (Same locale bug as autoGenWeekKey, on the separate challenge key.)
 */
fun isoWeekKey(date: Date = Date()): String =
    SimpleDateFormat("yyyy-'W'ww", Locale.ROOT).apply {
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.minimalDaysInFirstWeek = 4
    }.format(date)
