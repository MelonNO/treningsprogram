package com.migul.treningsprogram.data.repository

import com.migul.treningsprogram.data.backup.BackupScheduler
import com.migul.treningsprogram.data.db.dao.LiftGoalDao
import com.migul.treningsprogram.data.db.dao.WorkoutSetDao
import com.migul.treningsprogram.data.db.entity.LiftGoal
import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.GoalProgress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * N5 — goal storage + the reach detector. All decision math is pure ([GoalProgress]); this
 * class only does the DB I/O around it. Goals grant NO XP (A-G1), so nothing here touches
 * UserStats/XP events and StatsRecomputer parity is untouched.
 */
@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: LiftGoalDao,
    private val setDao: WorkoutSetDao,
    private val backupScheduler: BackupScheduler
) {

    fun observeAll(): Flow<List<LiftGoal>> = goalDao.observeAll()

    suspend fun getActiveOnce(): List<LiftGoal> = goalDao.getActiveOnce()

    suspend fun activeForExercise(name: String): LiftGoal? =
        goalDao.getActiveForExercise(name.trim())

    /**
     * Creates a goal (one ACTIVE goal per exercise — creating over an existing active goal
     * replaces its terms, keeping "edit" unceremonious).
     */
    suspend fun createOrReplace(name: String, targetKg: Float, isE1rm: Boolean, targetDateMs: Long) {
        val key = name.trim()
        if (key.isEmpty() || targetKg <= 0f) return
        val existing = goalDao.getActiveForExercise(key)
        if (existing != null) {
            goalDao.update(existing.copy(
                targetWeightKg = targetKg, isE1rm = isE1rm, targetDateMs = targetDateMs
            ))
        } else {
            goalDao.insert(LiftGoal(
                exerciseName = key,
                targetWeightKg = targetKg,
                isE1rm = isE1rm,
                targetDateMs = targetDateMs,
                createdAtMs = System.currentTimeMillis()
            ))
        }
        backupScheduler.requestBackup()
    }

    /** Abandoning is unceremonious: the goal just leaves the lists (kept as a row for merge). */
    suspend fun abandon(goal: LiftGoal) {
        goalDao.update(goal.copy(status = LiftGoal.STATUS_ABANDONED))
        backupScheduler.requestBackup()
    }

    /**
     * Reach detection on workout completion (A-G2): for each exercise in the session with an
     * ACTIVE goal, checks the session's working-set bests (warm-ups excluded by
     * [GoalProgress.sessionBests]) and promotes ACTIVE → ACHIEVED — the one-way flip that makes
     * the celebration fire exactly once. Returns the newly achieved goals for the celebration
     * surface. Achieved goals stay visible as history, never deleted.
     */
    suspend fun detectReached(sessionId: Long): List<LiftGoal> {
        val sets = setDao.getSetsForSessionOnce(sessionId)
        if (sets.isEmpty()) return emptyList()
        val reached = mutableListOf<LiftGoal>()
        for ((name, exerciseSets) in sets.groupBy { it.exerciseName }) {
            val goal = goalDao.getActiveForExercise(name) ?: continue
            val bests = GoalProgress.sessionBests(exerciseSets)
            if (GoalProgress.isReached(goal.isE1rm, goal.targetWeightKg, bests)) {
                val achieved = goal.copy(
                    status = LiftGoal.STATUS_ACHIEVED,
                    achievedAtMs = System.currentTimeMillis()
                )
                goalDao.update(achieved)
                reached.add(achieved)
            }
        }
        if (reached.isNotEmpty()) backupScheduler.requestBackup()
        return reached
    }

    /**
     * The all-time current best for a goal's progress display: best working weight, or best
     * per-session e1RM for e1RM goals (same derivation the Progress screen's e1RM line uses).
     */
    suspend fun currentBestFor(goal: LiftGoal): Float? =
        if (!goal.isE1rm) {
            setDao.getPreviousMaxWeight(goal.exerciseName, excludeSessionId = -1L)
        } else {
            setDao.getStrengthHistory(goal.exerciseName)
                .maxOfOrNull { Epley.estimate(it.maxWeight, it.bestReps) }
                ?.toFloat()
        }
}
