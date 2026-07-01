package com.migul.treningsprogram.data.repository

import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.dao.AchievementDao
import com.migul.treningsprogram.data.db.dao.UserStatsDao
import com.migul.treningsprogram.data.db.dao.WorkoutSetDao
import com.migul.treningsprogram.data.db.dao.XpEventDao
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
    private val xpEventDao: XpEventDao,
    private val dailyChallengeManager: DailyChallengeManager
) {
    val userStats: Flow<UserStats?> = userStatsDao.observe()

    suspend fun resetAll() {
        userStatsDao.upsert(UserStats(id = 1))
        achievementDao.resetAll()
        // U2: clear the XP log so a stats reset doesn't leave orphan events whose totals no
        // longer reconcile with the (now-zeroed) XP bar. Covers BOTH "Reset workouts/stats" and
        // Factory Reset, since both call resetAll().
        xpEventDao.deleteAll()
    }

    /**
     * From-scratch recompute of [UserStats] after a backup MERGE.
     *
     * Unlike [processWorkoutCompletion] (which is INCREMENTAL — it adds XP for a single new
     * session) and [resetAll] (which only zeroes), this replays the ENTIRE merged history
     * deterministically and overwrites UserStats. It must be used after a merge so XP/level/streak
     * are NOT double-counted by copying a UserStats row from either backup side.
     *
     * Delegates the math to [com.migul.treningsprogram.data.backup.StatsRecomputer], which mirrors
     * the live formulas (baseXp=50, setXp=5/working-set, prXp=30/PR, level via [xpToLevel], streak
     * from consecutive training days). The merged achievement set is passed for completeness; it
     * does not drive UserStats numbers.
     */
    suspend fun recomputeStatsFromHistory(
        sessions: List<com.migul.treningsprogram.data.db.entity.WorkoutSession>,
        sets: List<WorkoutSet>,
        achievements: List<Achievement> = emptyList()
    ) {
        val recomputed = com.migul.treningsprogram.data.backup.StatsRecomputer
            .recompute(sessions, sets, achievements)
        userStatsDao.upsert(recomputed)
    }

    suspend fun processWorkoutCompletion(sessionId: Long): WorkoutResult {
        ensureAchievementsSeeded()

        val sets = workoutSetDao.getSetsForSessionOnce(sessionId)
        val workingSets = sets.filter { !it.isWarmup }
        val setsLogged = workingSets.size
        val totalVolumeKg = workingSets.sumOf { it.reps.toDouble() * it.weightKg }.toFloat()
        val exerciseCount = workingSets.map { it.exerciseName }.toSet().size
        val prExercises = detectPersonalRecords(sessionId, sets)

        // Pass working sets only so the challenge completion criteria match the live in-progress
        // preview (which also uses working sets). Previously all sets were passed, causing a
        // mismatch: warmup sets could satisfy e.g. sets_10 at completion even though the
        // real-time progress display showed the goal not yet reached.
        val completedChallenges = dailyChallengeManager.completeChallenges(workingSets, prExercises.isNotEmpty())
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

        // Item 7: streak days use the LOGICAL day boundary (a 01:00 workout counts toward the
        // previous day), consistent with History/rest-missed/today's-plan.
        val today = com.migul.treningsprogram.domain.DayBoundary.logicalEpochDay(System.currentTimeMillis())
        val lastDay = com.migul.treningsprogram.domain.DayBoundary.logicalEpochDay(stats.lastWorkoutDateMs)
        val newStreak = when {
            lastDay == today         -> stats.currentStreak
            lastDay == today - 1L    -> stats.currentStreak + 1
            else                     -> 1
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

        // U2: record this award in the forward-only XP log. PURE OBSERVATION — we reuse the EXACT
        // component amounts computed above (baseXp/setXp/prXp/bonusChallengeXp); nothing here
        // changes how much XP is granted. Itemized so the user sees what earned each chunk; the
        // sum of the inserted rows equals xpEarned.
        XpEventBuilder.buildWorkoutEvents(
            timestampMs = updatedStats.lastWorkoutDateMs,
            sessionId = sessionId,
            baseXp = baseXp,
            setXp = setXp,
            setCount = workingSets.size,
            prXp = prXp,
            prCount = prExercises.size,
            bonusChallengeXp = bonusChallengeXp,
            challengeNames = completedChallenges.map { it.name }
        ).forEach { xpEventDao.insert(it) }

        val newAchievements = checkAchievements(updatedStats, workingSets.size, exerciseCount, totalVolumeKg, prExercises.size)

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
                // null = no prior performance → this is the baseline, NOT a PR.
                // Do NOT coerce null to 0f: a first-ever lift must never count as a PR.
                val prevMax = workoutSetDao.getPreviousMaxWeight(name, sessionId)
                isWeightPr(currentMax, prevMax)
            }
            .keys.toList()

    private suspend fun checkAchievements(
        stats: UserStats,
        setCount: Int,
        exerciseCount: Int = 0,
        totalVolumeKg: Float = 0f,
        sessionPrCount: Int = 0
    ): List<Achievement> {
        val now = System.currentTimeMillis()
        val w  = stats.totalWorkouts
        val s  = stats.currentStreak
        val p  = stats.totalPrs
        val l  = stats.level
        val xp = stats.totalXp
        val bs = stats.bestStreak
        val sc = setCount
        val ec = exerciseCount
        val vol = totalVolumeKg
        val sp = sessionPrCount
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
            "best_90"            to (bs >= 90),
            // === 86 creative replacements ===
            // per-session PR achievements
            "session_pr_2"       to (sp >= 2),
            "session_pr_3"       to (sp >= 3),
            "session_pr_5"       to (sp >= 5),
            "session_pr_8"       to (sp >= 8),
            "session_pr_10"      to (sp >= 10),
            // cross-stat combo achievements
            "combo_allrounder"   to (ec >= 7 && vol >= 2_000f),
            "combo_deep_focus"   to (ec <= 3 && ec >= 1 && vol >= 3_000f),
            "combo_iron_end"     to (sc >= 35 && vol >= 3_000f),
            "combo_less_more"    to (ec in 1..4 && vol >= 2_000f),
            "combo_marathon"     to (sc >= 40 && ec >= 8),
            "combo_beast"        to (sc >= 50 && vol >= 5_000f),
            "combo_singular"     to (sc >= 20 && ec == 1),
            "combo_peak"         to (sp >= 3 && vol >= 2_000f),
            "combo_qty_qual"     to (sc >= 20 && sp >= 3),
            "combo_explosive"    to (sp >= 5 && vol >= 1_500f),
            "combo_clean_sweep"  to (sp >= 3 && ec >= 3 && sp >= ec),
            "combo_every_rep"    to (sp >= 5 && ec >= 5 && sp >= ec),
            "combo_diverse_pr"   to (ec >= 6 && sp >= 2),
            "combo_hercules"     to (sp >= 5 && vol >= 3_000f),
            "combo_world_tour"   to (ec >= 12),
            "combo_jack"         to (ec >= 10 && sc >= 15),
            "combo_big3"         to (ec in 1..3 && vol >= 5_000f),
            "combo_pr_blitz"     to (sp >= 7),
            "combo_strength"     to (sp >= 5 && vol < 2_000f),
            "combo_vol_artist"   to (vol >= 2_500f && ec >= 6),
            "combo_relentless"   to (sc >= 60),
            "combo_go_big"       to (sc >= 25 && sp >= 5),
            "combo_intensity"    to (sc >= 20 && vol >= 4_000f),
            "combo_specialist"   to (sc >= 30 && ec in 1..2),
            "combo_heavyweight"  to (vol >= 15_000f),
            "combo_grind_set"    to (sc >= 25 && ec >= 4 && sp >= 1),
            // workout count: character milestones
            "the_foundation"     to (w >= 4),
            "habit_lock"         to (w >= 6),
            "the_initiate"       to (w >= 8),
            "going_eleven"       to (w >= 11),
            "three_week_club"    to (w >= 17),
            "the_grind"          to (w >= 22),
            "four_weeks_in"      to (w >= 28),
            "five_week_warrior"  to (w >= 35),
            "six_week_champ"     to (w >= 45),
            "the_dedicated"      to (w >= 55),
            "the_fanatic"        to (w >= 80),
            "one_twenty_five"    to (w >= 125),
            "the_ironclad"       to (w >= 175),
            "four_centuries"     to (w >= 400),
            "the_giant"          to (w >= 750),
            // streak: evocative milestones
            "six_sense"          to (s >= 6),
            "eight_days_week"    to (s >= 8),
            "more_than_a_week"   to (s >= 11),
            "unlucky_thirteen"   to (s >= 13),
            "sweet_sixteen"      to (s >= 16),
            "almost_a_month"     to (s >= 25),
            "five_weeks_str"     to (s >= 35),
            "fifty_day_grind"    to (s >= 50),
            "the_obsessed"       to (s >= 75),
            "century_challenge"  to (s >= 100),
            "five_month_miss"    to (s >= 150),
            // PR: personality names
            "four_aces"          to (p >= 4),
            "six_shooter"        to (p >= 6),
            "the_octopus"        to (p >= 8),
            "bakers_dozen_pr"    to (p >= 12),
            "high_score"         to (p >= 20),
            "forty_records"      to (p >= 40),
            "sixty_records"      to (p >= 60),
            "elite_records"      to (p >= 150),
            "two_hundred_recs"   to (p >= 200),
            "quarter_k_prs"      to (p >= 250),
            // levels: titles that feel earned
            "nine_lives"         to (l >= 9),
            "one_for_eleven"     to (l >= 11),
            "battle_hardened"    to (l >= 14),
            "seventeen_up"       to (l >= 17),
            "over_the_line"      to (l >= 22),
            "forty_five_lives"   to (l >= 45),
            "diamond_level"      to (l >= 60),
            "the_overlord"       to (l >= 80),
            "the_transcendent"   to (l >= 90),
            // XP: evocative names
            "lucky_xp"           to (xp >= 750),
            "xp_builder"         to (xp >= 1_500),
            "xp_rolling"         to (xp >= 3_500),
            "xp_surge"           to (xp >= 7_500),
            "xp_overflow"        to (xp >= 15_000),
            "xp_fountain"        to (xp >= 20_000),
            "xp_empire"          to (xp >= 35_000),
            "xp_monument"        to (xp >= 200_000),
            "xp_colossus"        to (xp >= 300_000),
            "xp_infinity"        to (xp >= 1_000_000),
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

    /**
     * Reconcile the persisted achievements table with the currently-defined set:
     * 1. Insert any missing rows (IGNORE on conflict preserves existing unlock state).
     * 2. Refresh display metadata (name, description, emoji) for every defined id so that
     *    corrected names/descriptions propagate to upgraded devices.
     * 3. Prune any rows whose id is no longer in the defined set (orphan rows from
     *    IDs that were renamed/replaced in earlier builds).
     *
     * This ensures the profile achievements count equals the defined-set size on BOTH
     * clean-install and upgraded devices, without losing the unlock state of any
     * still-valid achievement.
     */
    suspend fun ensureAchievementsSeeded() {
        achievementDao.insertAll(AppDatabase.PREDEFINED_ACHIEVEMENTS)
        AppDatabase.PREDEFINED_ACHIEVEMENTS.forEach { a ->
            achievementDao.updateMetadata(a.id, a.name, a.description, a.emoji)
        }
        val validIds = AppDatabase.PREDEFINED_ACHIEVEMENTS.map { it.id }
        achievementDao.deleteOrphans(validIds)
    }

    companion object {
        /**
         * A heaviest-weight PR is awarded ONLY when a real prior performance is beaten.
         * The first-ever performance ([previousMax] == null) establishes the baseline
         * and is never itself a PR. Equalling or going below the previous max is not a PR.
         */
        fun isWeightPr(currentMax: Float, previousMax: Float?): Boolean =
            previousMax != null && currentMax > previousMax

        /** Returns the IDs of all currently-defined achievements (useful for testing reconcile logic). */
        fun currentDefinedIds(): Set<String> =
            AppDatabase.PREDEFINED_ACHIEVEMENTS.map { it.id }.toSet()

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
    }
}
