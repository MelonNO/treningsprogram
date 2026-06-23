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

        val newAchievements = checkAchievements(updatedStats, workingSets.size, exerciseCount, totalVolumeKg)

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

    private suspend fun checkAchievements(
        stats: UserStats,
        setCount: Int,
        exerciseCount: Int = 0,
        totalVolumeKg: Float = 0f
    ): List<Achievement> {
        val now = System.currentTimeMillis()
        val w = stats.totalWorkouts
        val s = stats.currentStreak
        val p = stats.totalPrs
        val l = stats.level
        val xp = stats.totalXp
        val bs = stats.bestStreak
        val checks = mapOf(
            // workout count
            "first_workout" to (w >= 1),
            "workouts_2"    to (w >= 2),
            "workouts_3"    to (w >= 3),
            "workouts_5"    to (w >= 5),
            "workouts_7"    to (w >= 7),
            "workouts_10"   to (w >= 10),
            "workouts_15"   to (w >= 15),
            "workouts_20"   to (w >= 20),
            "workouts_25"   to (w >= 25),
            "workouts_30"   to (w >= 30),
            "workouts_40"   to (w >= 40),
            "workouts_50"   to (w >= 50),
            "workouts_60"   to (w >= 60),
            "workouts_75"   to (w >= 75),
            "workouts_100"  to (w >= 100),
            "workouts_150"  to (w >= 150),
            "workouts_200"  to (w >= 200),
            "workouts_250"  to (w >= 250),
            "workouts_300"  to (w >= 300),
            "workouts_365"  to (w >= 365),
            "workouts_500"  to (w >= 500),
            "workouts_1000" to (w >= 1000),
            // current streak
            "streak_2"      to (s >= 2),
            "streak_3"      to (s >= 3),
            "streak_4"      to (s >= 4),
            "streak_5"      to (s >= 5),
            "streak_7"      to (s >= 7),
            "streak_10"     to (s >= 10),
            "streak_14"     to (s >= 14),
            "streak_15"     to (s >= 15),
            "streak_20"     to (s >= 20),
            "streak_21"     to (s >= 21),
            "streak_30"     to (s >= 30),
            "streak_45"     to (s >= 45),
            "streak_60"     to (s >= 60),
            "streak_90"     to (s >= 90),
            "streak_180"    to (s >= 180),
            "streak_365"    to (s >= 365),
            // personal records
            "first_pr"      to (p >= 1),
            "pr_2"          to (p >= 2),
            "pr_3"          to (p >= 3),
            "pr_5"          to (p >= 5),
            "pr_7"          to (p >= 7),
            "pr_10"         to (p >= 10),
            "pr_15"         to (p >= 15),
            "pr_25"         to (p >= 25),
            "pr_30"         to (p >= 30),
            "pr_50"         to (p >= 50),
            "pr_75"         to (p >= 75),
            "pr_100"        to (p >= 100),
            // levels
            "level_2"       to (l >= 2),
            "level_3"       to (l >= 3),
            "level_4"       to (l >= 4),
            "level_5"       to (l >= 5),
            "level_6"       to (l >= 6),
            "level_7"       to (l >= 7),
            "level_8"       to (l >= 8),
            "level_10"      to (l >= 10),
            "level_12"      to (l >= 12),
            "level_15"      to (l >= 15),
            "level_20"      to (l >= 20),
            "level_25"      to (l >= 25),
            "level_30"      to (l >= 30),
            "level_35"      to (l >= 35),
            "level_40"      to (l >= 40),
            "level_50"      to (l >= 50),
            "level_75"      to (l >= 75),
            "level_100"     to (l >= 100),
            // sets in one session
            "sets_3"        to (setCount >= 3),
            "sets_5"        to (setCount >= 5),
            "sets_7"        to (setCount >= 7),
            "sets_10"       to (setCount >= 10),
            "sets_15"       to (setCount >= 15),
            "volume_beast"  to (setCount >= 20),
            "sets_25"       to (setCount >= 25),
            "sets_30"       to (setCount >= 30),
            "sets_40"       to (setCount >= 40),
            "sets_50"       to (setCount >= 50),
            // total XP
            "xp_250"        to (xp >= 250),
            "xp_500"        to (xp >= 500),
            "xp_1000"       to (xp >= 1_000),
            "xp_2500"       to (xp >= 2_500),
            "xp_5000"       to (xp >= 5_000),
            "xp_10000"      to (xp >= 10_000),
            "xp_25000"      to (xp >= 25_000),
            "xp_50000"      to (xp >= 50_000),
            "xp_75000"      to (xp >= 75_000),
            "xp_100000"     to (xp >= 100_000),
            "xp_150000"     to (xp >= 150_000),
            "xp_500000"     to (xp >= 500_000),
            // exercise variety in one session
            "ex_variety_3"  to (exerciseCount >= 3),
            "ex_variety_5"  to (exerciseCount >= 5),
            "ex_variety_7"  to (exerciseCount >= 7),
            "ex_variety_10" to (exerciseCount >= 10),
            "ex_variety_12" to (exerciseCount >= 12),
            "ex_variety_15" to (exerciseCount >= 15),
            // total volume in one session (kg)
            "vol_100"       to (totalVolumeKg >= 100f),
            "vol_250"       to (totalVolumeKg >= 250f),
            "vol_500"       to (totalVolumeKg >= 500f),
            "vol_750"       to (totalVolumeKg >= 750f),
            "vol_1000"      to (totalVolumeKg >= 1_000f),
            "vol_2000"      to (totalVolumeKg >= 2_000f),
            "vol_3000"      to (totalVolumeKg >= 3_000f),
            "vol_5000"      to (totalVolumeKg >= 5_000f),
            "vol_7500"      to (totalVolumeKg >= 7_500f),
            "vol_10000"     to (totalVolumeKg >= 10_000f),
            "vol_20000"     to (totalVolumeKg >= 20_000f),
            // best streak ever
            "best_3"        to (bs >= 3),
            "best_7"        to (bs >= 7),
            "best_14"       to (bs >= 14),
            "best_21"       to (bs >= 21),
            "best_30"       to (bs >= 30),
            "best_60"       to (bs >= 60),
            "best_90"       to (bs >= 90),
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
        achievementDao.insertAll(AppDatabase.PREDEFINED_ACHIEVEMENTS)
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
