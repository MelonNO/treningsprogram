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
            // === 86 more to reach 200 total ===
            // workout count fill-in
            "workouts_4"    to (w >= 4),
            "workouts_6"    to (w >= 6),
            "workouts_8"    to (w >= 8),
            "workouts_11"   to (w >= 11),
            "workouts_17"   to (w >= 17),
            "workouts_22"   to (w >= 22),
            "workouts_28"   to (w >= 28),
            "workouts_35"   to (w >= 35),
            "workouts_45"   to (w >= 45),
            "workouts_55"   to (w >= 55),
            "workouts_80"   to (w >= 80),
            "workouts_125"  to (w >= 125),
            "workouts_175"  to (w >= 175),
            "workouts_400"  to (w >= 400),
            "workouts_750"  to (w >= 750),
            // streak fill-in
            "streak_6"      to (s >= 6),
            "streak_8"      to (s >= 8),
            "streak_11"     to (s >= 11),
            "streak_13"     to (s >= 13),
            "streak_16"     to (s >= 16),
            "streak_25"     to (s >= 25),
            "streak_35"     to (s >= 35),
            "streak_50"     to (s >= 50),
            "streak_75"     to (s >= 75),
            "streak_100"    to (s >= 100),
            "streak_150"    to (s >= 150),
            // PR fill-in
            "pr_4"          to (p >= 4),
            "pr_6"          to (p >= 6),
            "pr_8"          to (p >= 8),
            "pr_12"         to (p >= 12),
            "pr_20"         to (p >= 20),
            "pr_40"         to (p >= 40),
            "pr_60"         to (p >= 60),
            "pr_150"        to (p >= 150),
            "pr_200"        to (p >= 200),
            "pr_250"        to (p >= 250),
            // level fill-in
            "level_9"       to (l >= 9),
            "level_11"      to (l >= 11),
            "level_14"      to (l >= 14),
            "level_17"      to (l >= 17),
            "level_22"      to (l >= 22),
            "level_45"      to (l >= 45),
            "level_60"      to (l >= 60),
            "level_80"      to (l >= 80),
            "level_90"      to (l >= 90),
            // sets per session fill-in
            "sets_4"        to (setCount >= 4),
            "sets_6"        to (setCount >= 6),
            "sets_8"        to (setCount >= 8),
            "sets_12"       to (setCount >= 12),
            "sets_18"       to (setCount >= 18),
            "sets_35"       to (setCount >= 35),
            "sets_45"       to (setCount >= 45),
            "sets_60"       to (setCount >= 60),
            // total XP fill-in
            "xp_750"        to (xp >= 750),
            "xp_1500"       to (xp >= 1_500),
            "xp_3500"       to (xp >= 3_500),
            "xp_7500"       to (xp >= 7_500),
            "xp_15000"      to (xp >= 15_000),
            "xp_20000"      to (xp >= 20_000),
            "xp_35000"      to (xp >= 35_000),
            "xp_200000"     to (xp >= 200_000),
            "xp_300000"     to (xp >= 300_000),
            "xp_1000000"    to (xp >= 1_000_000),
            // exercise variety fill-in
            "ex_variety_2"  to (exerciseCount >= 2),
            "ex_variety_4"  to (exerciseCount >= 4),
            "ex_variety_6"  to (exerciseCount >= 6),
            "ex_variety_8"  to (exerciseCount >= 8),
            "ex_variety_9"  to (exerciseCount >= 9),
            "ex_variety_11" to (exerciseCount >= 11),
            "ex_variety_13" to (exerciseCount >= 13),
            // volume per session fill-in
            "vol_150"       to (totalVolumeKg >= 150f),
            "vol_400"       to (totalVolumeKg >= 400f),
            "vol_600"       to (totalVolumeKg >= 600f),
            "vol_1500"      to (totalVolumeKg >= 1_500f),
            "vol_2500"      to (totalVolumeKg >= 2_500f),
            "vol_4000"      to (totalVolumeKg >= 4_000f),
            "vol_6000"      to (totalVolumeKg >= 6_000f),
            "vol_15000"     to (totalVolumeKg >= 15_000f),
            // best streak fill-in
            "best_5"        to (bs >= 5),
            "best_10"       to (bs >= 10),
            "best_25"       to (bs >= 25),
            "best_45"       to (bs >= 45),
            "best_120"      to (bs >= 120),
            "best_180"      to (bs >= 180),
            "best_250"      to (bs >= 250),
            "best_365"      to (bs >= 365),
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
