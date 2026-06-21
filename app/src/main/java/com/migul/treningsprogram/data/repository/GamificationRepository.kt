package com.migul.treningsprogram.data.repository

import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.dao.AchievementDao
import com.migul.treningsprogram.data.db.dao.UserStatsDao
import com.migul.treningsprogram.data.db.dao.WorkoutSetDao
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.data.preferences.DailyChallengeManager
import com.migul.treningsprogram.domain.model.WorkoutResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.sqrt

@Singleton
class GamificationRepository @Inject constructor(
    private val userStatsDao: UserStatsDao,
    private val achievementDao: AchievementDao,
    private val workoutSetDao: WorkoutSetDao,
    private val dailyChallengeManager: DailyChallengeManager
) {
    val userStats: Flow<UserStats?> = userStatsDao.observe()

    suspend fun resetAll() {
        userStatsDao.upsert(UserStats(id = 1))
        achievementDao.resetAll()
    }

    suspend fun processWorkoutCompletion(sessionId: Long): WorkoutResult {
        ensureAchievementsSeeded()

        val sets = workoutSetDao.getSetsForSessionOnce(sessionId)
        val workingSets = sets.filter { !it.isWarmup }
        val setsLogged = workingSets.size
        val totalVolumeKg = workingSets.sumOf { it.reps.toDouble() * it.weightKg }.toFloat()
        val exerciseCount = workingSets.map { it.exerciseName }.toSet().size
        val prExercises = detectPersonalRecords(sessionId, sets)

        val completedChallenges = dailyChallengeManager.completeChallenges(sets, prExercises.isNotEmpty())
        val bonusChallengeXp = completedChallenges.sumOf { it.bonusXp }

        val baseXp = 50
        val setXp = workingSets.size * 5
        val prXp = prExercises.size * 30
        val xpEarned = baseXp + setXp + prXp + bonusChallengeXp

        val stats = userStatsDao.get() ?: UserStats()
        val prevLevel = stats.level
        val newTotalXp = stats.totalXp + xpEarned
        val newLevel = xpToLevel(newTotalXp)
        val newTotalWorkouts = stats.totalWorkouts + 1
        val newTotalPrs = stats.totalPrs + prExercises.size

        val today = startOfDay(System.currentTimeMillis())
        val yesterday = today - 86_400_000L
        val lastDay = startOfDay(stats.lastWorkoutDateMs)
        val newStreak = when {
            lastDay == today     -> stats.currentStreak
            lastDay == yesterday -> stats.currentStreak + 1
            else                 -> 1
        }

        val updatedStats = stats.copy(
            totalXp = newTotalXp,
            level = newLevel,
            currentStreak = newStreak,
            bestStreak = maxOf(stats.bestStreak, newStreak),
            totalWorkouts = newTotalWorkouts,
            totalPrs = newTotalPrs,
            lastWorkoutDateMs = System.currentTimeMillis()
        )
        userStatsDao.upsert(updatedStats)

        val newAchievements = checkAchievements(updatedStats, workingSets.size)

        return WorkoutResult(
            xpEarned = xpEarned,
            totalXp = newTotalXp,
            level = newLevel,
            levelProgress = levelProgress(newTotalXp),
            xpToNextLevel = xpForLevel(newLevel + 1) - newTotalXp,
            didLevelUp = newLevel > prevLevel,
            previousLevel = prevLevel,
            currentStreak = newStreak,
            personalRecords = prExercises,
            newAchievements = newAchievements,
            totalWorkouts = newTotalWorkouts,
            completedChallenges = completedChallenges,
            bonusChallengeXp = bonusChallengeXp,
            setsLogged = setsLogged,
            totalVolumeKg = totalVolumeKg,
            exerciseCount = exerciseCount
        )
    }

    private suspend fun detectPersonalRecords(sessionId: Long, sets: List<WorkoutSet>): List<String> =
        sets.filter { !it.isWarmup }
            .groupBy { it.exerciseName }
            .mapValues { (_, s) -> s.maxOf { it.weightKg } }
            .filter { (name, currentMax) ->
                val prevMax = workoutSetDao.getPreviousMaxWeight(name, sessionId) ?: 0f
                currentMax > prevMax
            }
            .keys.toList()

    private suspend fun checkAchievements(stats: UserStats, setCount: Int): List<Achievement> {
        val now = System.currentTimeMillis()
        val checks = mapOf(
            "first_workout" to (stats.totalWorkouts >= 1),
            "workouts_5"    to (stats.totalWorkouts >= 5),
            "workouts_10"   to (stats.totalWorkouts >= 10),
            "workouts_25"   to (stats.totalWorkouts >= 25),
            "workouts_50"   to (stats.totalWorkouts >= 50),
            "workouts_100"  to (stats.totalWorkouts >= 100),
            "streak_3"      to (stats.currentStreak >= 3),
            "streak_7"      to (stats.currentStreak >= 7),
            "streak_14"     to (stats.currentStreak >= 14),
            "first_pr"      to (stats.totalPrs >= 1),
            "pr_5"          to (stats.totalPrs >= 5),
            "level_5"       to (stats.level >= 5),
            "level_10"      to (stats.level >= 10),
            "volume_beast"  to (setCount >= 20),
        )
        return checks.mapNotNull { (id, condition) ->
            if (!condition) return@mapNotNull null
            val a = achievementDao.getById(id) ?: return@mapNotNull null
            if (a.isUnlocked) return@mapNotNull null
            val unlocked = a.copy(isUnlocked = true, unlockedAtMs = now)
            achievementDao.update(unlocked)
            unlocked
        }
    }

    suspend fun ensureAchievementsSeeded() {
        if (achievementDao.count() == 0) {
            achievementDao.insertAll(AppDatabase.PREDEFINED_ACHIEVEMENTS)
        }
    }

    companion object {
        fun xpToLevel(xp: Int): Int = floor(sqrt(xp / 200.0)).toInt() + 1

        fun xpForLevel(level: Int): Int = ((level - 1) * (level - 1)) * 200

        fun levelProgress(xp: Int): Float {
            val level = xpToLevel(xp)
            val start = xpForLevel(level).toFloat()
            val end = xpForLevel(level + 1).toFloat()
            return ((xp - start) / (end - start)).coerceIn(0f, 1f)
        }

        fun levelTitle(level: Int): String = when (level) {
            1          -> "Rookie"
            2          -> "Novice"
            3          -> "Trainee"
            4          -> "Athlete"
            5          -> "Competitor"
            6          -> "Warrior"
            7          -> "Champion"
            8          -> "Iron Man"
            9          -> "Elite"
            10         -> "Master"
            11         -> "Expert"
            12         -> "Veteran"
            13         -> "Pro"
            14         -> "Phenom"
            in 15..19  -> "Legend"
            else       -> "Transcendent"
        }

        private fun startOfDay(ms: Long): Long {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
