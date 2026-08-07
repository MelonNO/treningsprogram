package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.domain.AchievementCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R5 — catalog completeness, tier/category assignment, progress math, next-up selection. */
class AchievementCatalogTest {

    // THE completeness lock: every defined achievement has metadata (category + tier), so every
    // item appears exactly once, in a category, with a tier (explicit AC).
    @Test fun everyDefinedAchievementHasMeta() {
        val missing = GamificationRepository.currentDefinedIds()
            .filter { AchievementCatalog.metaFor(it) == null }
        assertTrue("ids without catalog metadata: $missing", missing.isEmpty())
    }

    @Test fun definedSetIsThePredefinedList() {
        assertEquals(
            AppDatabase.PREDEFINED_ACHIEVEMENTS.size,
            GamificationRepository.currentDefinedIds().size
        )
    }

    @Test fun tiersScaleWithDifficultyPerFamily() {
        fun tier(id: String) = AchievementCatalog.metaFor(id)!!.tier
        // workouts: <=10 C, <=50 R, <=200 E, above L
        assertEquals(AchievementCatalog.Tier.COMMON, tier("first_workout"))
        assertEquals(AchievementCatalog.Tier.RARE, tier("workouts_50"))
        assertEquals(AchievementCatalog.Tier.EPIC, tier("workouts_100"))
        assertEquals(AchievementCatalog.Tier.LEGENDARY, tier("workouts_1000"))
        // streaks
        assertEquals(AchievementCatalog.Tier.COMMON, tier("streak_3"))
        assertEquals(AchievementCatalog.Tier.LEGENDARY, tier("streak_365"))
        // named ids inherit their family's scale
        assertEquals(AchievementCatalog.Tier.COMMON, tier("the_foundation"))   // 4 workouts
        assertEquals(AchievementCatalog.Tier.LEGENDARY, tier("the_giant"))     // 750 workouts
        // combos are hand-rated
        assertEquals(AchievementCatalog.Tier.LEGENDARY, tier("combo_beast"))
        assertEquals(AchievementCatalog.Tier.RARE, tier("combo_allrounder"))
    }

    @Test fun categoriesGroupFamilies() {
        fun cat(id: String) = AchievementCatalog.metaFor(id)!!.category
        assertEquals(AchievementCatalog.Category.WORKOUTS, cat("workouts_50"))
        assertEquals(AchievementCatalog.Category.STREAKS, cat("streak_7"))
        assertEquals(AchievementCatalog.Category.STREAKS, cat("best_14"))
        assertEquals(AchievementCatalog.Category.RECORDS, cat("pr_10"))
        assertEquals(AchievementCatalog.Category.RECORDS, cat("session_pr_3"))
        assertEquals(AchievementCatalog.Category.STRENGTH, cat("level_10"))
        assertEquals(AchievementCatalog.Category.XP, cat("xp_2500"))
        assertEquals(AchievementCatalog.Category.BIG_SESSIONS, cat("sets_15"))
        assertEquals(AchievementCatalog.Category.BIG_SESSIONS, cat("vol_1000"))
        assertEquals(AchievementCatalog.Category.BIG_SESSIONS, cat("volume_beast"))
        assertEquals(AchievementCatalog.Category.VARIETY, cat("ex_variety_5"))
        assertEquals(AchievementCatalog.Category.COMBOS, cat("combo_hercules"))
    }

    @Test fun everyCategoryIsNonEmptyAndCountsSumToTotal() {
        val byCat = GamificationRepository.currentDefinedIds()
            .groupBy { AchievementCatalog.metaFor(it)!!.category }
        AchievementCatalog.CATEGORY_ORDER.forEach { cat ->
            assertTrue("category $cat is empty", (byCat[cat]?.size ?: 0) > 0)
        }
        assertEquals(
            GamificationRepository.currentDefinedIds().size,
            byCat.values.sumOf { it.size }
        )
    }

    // ── progress ──────────────────────────────────────────────────────────────────────────────

    private val stats = UserStats(
        totalXp = 3_000, level = 4, currentStreak = 12, bestStreak = 12,
        totalWorkouts = 43, totalPrs = 9
    )

    /**
     * Brief 02: the `level_` family now reads a 0-100 STRENGTH score rather than UserStats.level.
     * The fixture keeps level = 4 deliberately, and this is set to something different, so a
     * regression that reads the stored level again shows up as a wrong number rather than passing.
     */
    private val strengthScore = 4

    @Test fun lifetimeThresholdsShowLiveProgress() {
        assertEquals(43 to 50, AchievementCatalog.progressFor("workouts_50", stats, strengthScore))
        assertEquals(12 to 14, AchievementCatalog.progressFor("streak_14", stats, strengthScore))
        assertEquals(9 to 10, AchievementCatalog.progressFor("pr_10", stats, strengthScore))
        assertEquals(3_000 to 5_000, AchievementCatalog.progressFor("xp_5000", stats, strengthScore))
        assertEquals(4 to 5, AchievementCatalog.progressFor("level_5", stats, strengthScore))
        assertEquals(12 to 21, AchievementCatalog.progressFor("best_21", stats, strengthScore))
    }

    @Test fun progressNeverExceedsTheBar() {
        // Explicit AC: current is capped at the threshold before unlock.
        assertEquals(14 to 14, AchievementCatalog.progressFor("streak_14", stats.copy(currentStreak = 99), strengthScore))
    }

    @Test fun sessionScopedAndCombosHaveNoLifetimeProgress() {
        assertNull(AchievementCatalog.progressFor("sets_15", stats, strengthScore))
        assertNull(AchievementCatalog.progressFor("vol_1000", stats, strengthScore))
        assertNull(AchievementCatalog.progressFor("ex_variety_5", stats, strengthScore))
        assertNull(AchievementCatalog.progressFor("session_pr_3", stats, strengthScore))
        assertNull(AchievementCatalog.progressFor("combo_beast", stats, strengthScore))
    }

    // ── next up ───────────────────────────────────────────────────────────────────────────────

    private fun ach(id: String, unlocked: Boolean = false) =
        Achievement(id, "n", "d", "🏆", isUnlocked = unlocked)

    @Test fun nextUpPicksTheClosestLockedByFraction() {
        val achievements = listOf(
            ach("workouts_50"),            // 43/50 = 0.86
            ach("streak_14"),              // 12/14 ≈ 0.857
            ach("pr_10"),                  // 9/10 = 0.9
            ach("xp_5000"),                // 3000/5000 = 0.6
            ach("workouts_40", unlocked = true), // unlocked: excluded
            ach("combo_beast")             // no progress: excluded
        )
        val next = AchievementCatalog.nextUp(achievements, stats, strengthScore, 3).map { it.id }
        assertEquals(listOf("pr_10", "workouts_50", "streak_14"), next)
    }

    @Test fun nextUpEmptyForFreshUser() {
        val fresh = UserStats()
        val achievements = listOf(ach("workouts_5"), ach("streak_3"))
        assertTrue(AchievementCatalog.nextUp(achievements, fresh, 0).isEmpty())
    }

    @Test fun nextUpUpdatesWithStats() {
        val achievements = listOf(ach("workouts_5"), ach("streak_3"))
        val after = AchievementCatalog.nextUp(achievements, UserStats(totalWorkouts = 4, currentStreak = 1), 0, 3)
        assertNotNull(after)
        assertEquals("workouts_5", after.first().id) // 4/5 beats 1/3
    }
}
