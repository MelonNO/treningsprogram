package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.UserStats

/**
 * R5 — presentation metadata for the ~200 predefined achievements: a CATEGORY (gallery grouping),
 * a RARITY TIER (visual weight, A-G1: derived from threshold difficulty per family), and — for
 * every achievement whose condition is a simple threshold over lifetime [UserStats] — live
 * PROGRESS toward unlock ("43/50 workouts").
 *
 * PRESENTATION ONLY: nothing here changes unlock logic, ids, or timing (explicit R5 constraint).
 * The id → meaning mapping mirrors GamificationRepository.checkAchievements: most ids are
 * pattern-derived (workouts_50, streak_14, xp_2500, …); the ~55 named "character" ids carry a
 * hand-written entry with the same thresholds the checker uses. AchievementCatalogTest locks
 * completeness against GamificationRepository.currentDefinedIds().
 *
 * Session-scoped families (sets/volume/variety/session-PRs in ONE session) and cross-stat combos
 * have no meaningful lifetime progress — they expose their condition (the description) instead.
 */
object AchievementCatalog {

    enum class Tier(val label: String) {
        COMMON("Common"), RARE("Rare"), EPIC("Epic"), LEGENDARY("Legendary")
    }

    enum class Category(val label: String) {
        WORKOUTS("Workouts"), STREAKS("Streaks"), RECORDS("Records"), LEVELS("Levels"),
        XP("XP"), BIG_SESSIONS("Big Sessions"), VARIETY("Variety"), COMBOS("Combos")
    }

    /** What lifetime stat (if any) the threshold applies to. */
    enum class Kind {
        WORKOUTS, STREAK, BEST_STREAK, PRS, SESSION_PRS, LEVEL, XP,
        SESSION_SETS, SESSION_VOLUME, SESSION_VARIETY, COMBO
    }

    data class Meta(val kind: Kind, val threshold: Int, val comboTier: Tier? = null) {
        val category: Category = when (kind) {
            Kind.WORKOUTS -> Category.WORKOUTS
            Kind.STREAK, Kind.BEST_STREAK -> Category.STREAKS
            Kind.PRS, Kind.SESSION_PRS -> Category.RECORDS
            Kind.LEVEL -> Category.LEVELS
            Kind.XP -> Category.XP
            Kind.SESSION_SETS, Kind.SESSION_VOLUME -> Category.BIG_SESSIONS
            Kind.SESSION_VARIETY -> Category.VARIETY
            Kind.COMBO -> Category.COMBOS
        }

        val tier: Tier = comboTier ?: when (kind) {
            Kind.WORKOUTS -> tierBy(threshold, 10, 50, 200)
            Kind.STREAK, Kind.BEST_STREAK -> tierBy(threshold, 7, 21, 60)
            Kind.PRS -> tierBy(threshold, 5, 25, 75)
            Kind.SESSION_PRS -> tierBy(threshold, 2, 3, 5)
            Kind.LEVEL -> tierBy(threshold, 5, 15, 40)
            Kind.XP -> tierBy(threshold, 1_000, 10_000, 100_000)
            Kind.SESSION_SETS -> tierBy(threshold, 7, 15, 30)
            Kind.SESSION_VOLUME -> tierBy(threshold, 500, 2_000, 7_500)
            Kind.SESSION_VARIETY -> tierBy(threshold, 5, 10, 12)
            Kind.COMBO -> Tier.RARE
        }
    }

    private fun tierBy(v: Int, common: Int, rare: Int, epic: Int): Tier = when {
        v <= common -> Tier.COMMON
        v <= rare -> Tier.RARE
        v <= epic -> Tier.EPIC
        else -> Tier.LEGENDARY
    }

    /** The named "character" achievements — same thresholds as checkAchievements. */
    private val NAMED: Map<String, Meta> = buildMap {
        // workout-count character milestones
        put("the_foundation", Meta(Kind.WORKOUTS, 4)); put("habit_lock", Meta(Kind.WORKOUTS, 6))
        put("the_initiate", Meta(Kind.WORKOUTS, 8)); put("going_eleven", Meta(Kind.WORKOUTS, 11))
        put("three_week_club", Meta(Kind.WORKOUTS, 17)); put("the_grind", Meta(Kind.WORKOUTS, 22))
        put("four_weeks_in", Meta(Kind.WORKOUTS, 28)); put("five_week_warrior", Meta(Kind.WORKOUTS, 35))
        put("six_week_champ", Meta(Kind.WORKOUTS, 45)); put("the_dedicated", Meta(Kind.WORKOUTS, 55))
        put("the_fanatic", Meta(Kind.WORKOUTS, 80)); put("one_twenty_five", Meta(Kind.WORKOUTS, 125))
        put("the_ironclad", Meta(Kind.WORKOUTS, 175)); put("four_centuries", Meta(Kind.WORKOUTS, 400))
        put("the_giant", Meta(Kind.WORKOUTS, 750))
        // streak: evocative milestones
        put("six_sense", Meta(Kind.STREAK, 6)); put("eight_days_week", Meta(Kind.STREAK, 8))
        put("more_than_a_week", Meta(Kind.STREAK, 11)); put("unlucky_thirteen", Meta(Kind.STREAK, 13))
        put("sweet_sixteen", Meta(Kind.STREAK, 16)); put("almost_a_month", Meta(Kind.STREAK, 25))
        put("five_weeks_str", Meta(Kind.STREAK, 35)); put("fifty_day_grind", Meta(Kind.STREAK, 50))
        put("the_obsessed", Meta(Kind.STREAK, 75)); put("century_challenge", Meta(Kind.STREAK, 100))
        put("five_month_miss", Meta(Kind.STREAK, 150))
        // PR personality names
        put("four_aces", Meta(Kind.PRS, 4)); put("six_shooter", Meta(Kind.PRS, 6))
        put("the_octopus", Meta(Kind.PRS, 8)); put("bakers_dozen_pr", Meta(Kind.PRS, 12))
        put("high_score", Meta(Kind.PRS, 20)); put("forty_records", Meta(Kind.PRS, 40))
        put("sixty_records", Meta(Kind.PRS, 60)); put("elite_records", Meta(Kind.PRS, 150))
        put("two_hundred_recs", Meta(Kind.PRS, 200)); put("quarter_k_prs", Meta(Kind.PRS, 250))
        // level titles
        put("nine_lives", Meta(Kind.LEVEL, 9)); put("one_for_eleven", Meta(Kind.LEVEL, 11))
        put("battle_hardened", Meta(Kind.LEVEL, 14)); put("seventeen_up", Meta(Kind.LEVEL, 17))
        put("over_the_line", Meta(Kind.LEVEL, 22)); put("forty_five_lives", Meta(Kind.LEVEL, 45))
        put("diamond_level", Meta(Kind.LEVEL, 60)); put("the_overlord", Meta(Kind.LEVEL, 80))
        put("the_transcendent", Meta(Kind.LEVEL, 90))
        // XP evocative names
        put("lucky_xp", Meta(Kind.XP, 750)); put("xp_builder", Meta(Kind.XP, 1_500))
        put("xp_rolling", Meta(Kind.XP, 3_500)); put("xp_surge", Meta(Kind.XP, 7_500))
        put("xp_overflow", Meta(Kind.XP, 15_000)); put("xp_fountain", Meta(Kind.XP, 20_000))
        put("xp_empire", Meta(Kind.XP, 35_000)); put("xp_monument", Meta(Kind.XP, 200_000))
        put("xp_colossus", Meta(Kind.XP, 300_000)); put("xp_infinity", Meta(Kind.XP, 1_000_000))
        // one-off session id
        put("volume_beast", Meta(Kind.SESSION_SETS, 20))
        put("first_workout", Meta(Kind.WORKOUTS, 1))
        put("first_pr", Meta(Kind.PRS, 1))
        // combos — tier rated by strictness (A-G1)
        val comboL = listOf("combo_beast", "combo_relentless", "combo_heavyweight", "combo_pr_blitz", "combo_every_rep")
        val comboE = listOf(
            "combo_hercules", "combo_marathon", "combo_iron_end", "combo_big3", "combo_clean_sweep",
            "combo_go_big", "combo_world_tour", "combo_explosive", "combo_intensity",
            "combo_specialist", "combo_singular", "combo_deep_focus"
        )
        val comboR = listOf(
            "combo_allrounder", "combo_less_more", "combo_peak", "combo_qty_qual",
            "combo_diverse_pr", "combo_jack", "combo_strength", "combo_vol_artist", "combo_grind_set"
        )
        comboL.forEach { put(it, Meta(Kind.COMBO, 0, comboTier = Tier.LEGENDARY)) }
        comboE.forEach { put(it, Meta(Kind.COMBO, 0, comboTier = Tier.EPIC)) }
        comboR.forEach { put(it, Meta(Kind.COMBO, 0, comboTier = Tier.RARE)) }
    }

    /** Metadata for [id]; never null for a defined achievement (locked by AchievementCatalogTest). */
    fun metaFor(id: String): Meta? {
        NAMED[id]?.let { return it }
        fun num(prefix: String): Int? = id.removePrefix(prefix).toIntOrNull()
        return when {
            id.startsWith("session_pr_") -> num("session_pr_")?.let { Meta(Kind.SESSION_PRS, it) }
            id.startsWith("workouts_") -> num("workouts_")?.let { Meta(Kind.WORKOUTS, it) }
            id.startsWith("streak_") -> num("streak_")?.let { Meta(Kind.STREAK, it) }
            id.startsWith("best_") -> num("best_")?.let { Meta(Kind.BEST_STREAK, it) }
            id.startsWith("pr_") -> num("pr_")?.let { Meta(Kind.PRS, it) }
            id.startsWith("level_") -> num("level_")?.let { Meta(Kind.LEVEL, it) }
            id.startsWith("xp_") -> num("xp_")?.let { Meta(Kind.XP, it) }
            id.startsWith("sets_") -> num("sets_")?.let { Meta(Kind.SESSION_SETS, it) }
            id.startsWith("vol_") -> num("vol_")?.let { Meta(Kind.SESSION_VOLUME, it) }
            id.startsWith("ex_variety_") -> num("ex_variety_")?.let { Meta(Kind.SESSION_VARIETY, it) }
            id.startsWith("combo_") -> Meta(Kind.COMBO, 0, comboTier = Tier.RARE)
            else -> null
        }
    }

    /**
     * (current, threshold) toward unlock for lifetime-stat achievements, or null for session-scoped
     * families and combos (they show their condition instead). Current is CAPPED at the threshold
     * so the bar never overshoots before unlock (explicit AC).
     */
    fun progressFor(id: String, stats: UserStats): Pair<Int, Int>? {
        val meta = metaFor(id) ?: return null
        val current = when (meta.kind) {
            Kind.WORKOUTS -> stats.totalWorkouts
            Kind.STREAK -> stats.currentStreak
            Kind.BEST_STREAK -> stats.bestStreak
            Kind.PRS -> stats.totalPrs
            Kind.LEVEL -> stats.level
            Kind.XP -> stats.totalXp
            else -> return null // session-scoped / combo: no lifetime progress
        }
        return current.coerceAtMost(meta.threshold) to meta.threshold
    }

    /** Display order of the gallery categories. */
    val CATEGORY_ORDER: List<Category> = listOf(
        Category.WORKOUTS, Category.STREAKS, Category.RECORDS, Category.LEVELS,
        Category.XP, Category.BIG_SESSIONS, Category.VARIETY, Category.COMBOS
    )

    /**
     * The "next up" teaser: the [count] locked, lifetime-progress achievements closest to
     * unlocking (by fraction of threshold reached), best first. Ties break toward the SMALLER
     * threshold (the sooner win). Only achievements with real progress (> 0) qualify.
     */
    fun nextUp(achievements: List<Achievement>, stats: UserStats, count: Int = 3): List<Achievement> =
        achievements
            .filter { !it.isUnlocked }
            .mapNotNull { a ->
                val p = progressFor(a.id, stats) ?: return@mapNotNull null
                if (p.first <= 0) return@mapNotNull null
                Triple(a, p.first.toDouble() / p.second, p.second)
            }
            .sortedWith(compareByDescending<Triple<Achievement, Double, Int>> { it.second }
                .thenBy { it.third })
            .take(count)
            .map { it.first }
}
