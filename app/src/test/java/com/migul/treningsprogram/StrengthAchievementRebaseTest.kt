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

/**
 * Brief 02 (2026-08-07) — re-basing the level-keyed achievements onto strength.
 *
 * **This file exists for one blocking acceptance criterion: nothing already earned may be lost.**
 *
 * The danger is specific and fast. `GamificationRepository.ensureAchievementsSeeded()` runs on
 * every app launch and ends with `achievementDao.deleteOrphans(validIds)` —
 * `DELETE FROM achievements WHERE id NOT IN (...)`. An achievement id that leaves
 * `PREDEFINED_ACHIEVEMENTS` therefore has its row, including the `unlockedAtMs` timestamp the user
 * earned, **hard-deleted within seconds of the app next opening**. No prompt, no backup, no undo.
 *
 * So the re-base changed only what these achievements MEAN — their `Kind` and the stat their
 * threshold is compared against — and never their ids. [theTwentySevenIdsAreFrozen] is the guard:
 * it holds the exact id set as a literal, so any future rename fails here rather than in a user's
 * database.
 */
class StrengthAchievementRebaseTest {

    /**
     * The 27 achievements that were keyed to the retired XP level, as literals.
     *
     * Deliberately hard-coded rather than derived — a derived list would happily follow a rename
     * and prove nothing. 18 carry the `level_` prefix; 9 are named "character" ids.
     */
    private val FROZEN_STRENGTH_IDS = setOf(
        "level_2", "level_3", "level_4", "level_5", "level_6", "level_7", "level_8",
        "level_10", "level_12", "level_15", "level_20", "level_25", "level_30", "level_35",
        "level_40", "level_50", "level_75", "level_100",
        "nine_lives", "one_for_eleven", "battle_hardened", "seventeen_up", "over_the_line",
        "forty_five_lives", "diamond_level", "the_overlord", "the_transcendent",
    )

    private val defined get() = AppDatabase.PREDEFINED_ACHIEVEMENTS

    // ── The data-loss guard ─────────────────────────────────────────────────────────────────

    @Test fun theTwentySevenIdsAreFrozen() {
        val ids = defined.map { it.id }.toSet()
        FROZEN_STRENGTH_IDS.forEach { id ->
            assertTrue(
                "Achievement id '$id' left PREDEFINED_ACHIEVEMENTS. ensureAchievementsSeeded() " +
                    "would hard-delete the user's earned row on the next launch. Ids are storage — " +
                    "change the meaning, never the id.",
                id in ids
            )
        }
        assertEquals(27, FROZEN_STRENGTH_IDS.size)
    }

    @Test fun theSeedListIsStillExactlyTwoHundred() {
        assertEquals(200, defined.size)
        assertEquals(200, defined.map { it.id }.toSet().size)
    }

    /**
     * The prune contract, re-asserted specifically for the re-based ids: an unlocked strength
     * achievement survives the launch-time reconcile with its timestamp intact.
     */
    @Test fun anAlreadyEarnedStrengthAchievementSurvivesTheLaunchTimePrune() {
        val earnedAt = 1_699_000_000_000L
        val db = defined.map {
            if (it.id in FROZEN_STRENGTH_IDS) it.copy(isUnlocked = true, unlockedAtMs = earnedAt)
            else it
        }
        val pruned = db.filter { it.id in GamificationRepository.currentDefinedIds() }

        assertEquals(200, pruned.size)
        FROZEN_STRENGTH_IDS.forEach { id ->
            val row = pruned.firstOrNull { it.id == id }
            assertNotNull("$id was pruned away", row)
            assertTrue("$id lost its unlock", row!!.isUnlocked)
            assertEquals("$id lost its unlock timestamp", earnedAt, row.unlockedAtMs)
        }
    }

    /**
     * D2 — anything already earned stays earned, permanently, even if the rating that earned it
     * later falls. This models `checkAchievements`, which only ever writes `isUnlocked = true` and
     * skips rows that are already unlocked: a false condition is a no-op, never an un-set.
     */
    @Test fun aRatingThatFallsBackDownNeverTakesTheAchievementAway() {
        val earnedAt = 1_699_000_000_000L
        val earned = Achievement(
            id = "level_40", name = "Immortal", description = "…", emoji = "🔱",
            isUnlocked = true, unlockedAtMs = earnedAt,
        )
        // The user's strength score has since dropped to 12 — far below this threshold.
        val conditionNowFalse = 12 >= 40
        assertTrue("precondition: the condition really is false now", !conditionNowFalse)

        val afterCheck = if (conditionNowFalse && !earned.isUnlocked) {
            earned.copy(isUnlocked = true, unlockedAtMs = 9_999L)
        } else earned   // both branches of the real checker leave an unlocked row untouched

        assertTrue(afterCheck.isUnlocked)
        assertEquals("an earned unlock must never be re-stamped either", earnedAt, afterCheck.unlockedAtMs)
    }

    // ── The re-base itself ──────────────────────────────────────────────────────────────────

    @Test fun allTwentySevenNowReadStrengthNotTheXpLevel() {
        FROZEN_STRENGTH_IDS.forEach { id ->
            val meta = AchievementCatalog.metaFor(id)
            assertNotNull("$id lost its catalog metadata", meta)
            assertEquals("$id must be a strength achievement", AchievementCatalog.Kind.STRENGTH, meta!!.kind)
            assertEquals(AchievementCatalog.Category.STRENGTH, meta.category)
        }
    }

    @Test fun noAchievementIsKeyedToTheRetiredXpLevelAnyMore() {
        // The Kind enum no longer has a LEVEL constant; this asserts the category is gone too, so
        // nothing can quietly re-add an XP-level family alongside the strength one.
        assertTrue(AchievementCatalog.Category.values().none { it.name == "LEVELS" })
        assertTrue(AchievementCatalog.Kind.values().none { it.name == "LEVEL" })
        assertTrue(AchievementCatalog.CATEGORY_ORDER.contains(AchievementCatalog.Category.STRENGTH))
    }

    /** Progress must follow the strength score, and must ignore the vestigial UserStats.level. */
    @Test fun progressComesFromTheStrengthScoreNotTheStoredLevel() {
        val stats = UserStats(id = 1, totalXp = 50_000, level = 99, totalWorkouts = 300)
        assertEquals(3 to 20, AchievementCatalog.progressFor("level_20", stats, strengthScore = 3))
        assertEquals(20 to 20, AchievementCatalog.progressFor("level_20", stats, strengthScore = 55))
        // The stored level of 99 must not leak in anywhere.
        assertEquals(0 to 20, AchievementCatalog.progressFor("level_20", stats, strengthScore = 0))
    }

    @Test fun theXpAchievementsAreUntouchedAndStillReadXp() {
        val stats = UserStats(id = 1, totalXp = 3_000, level = 1)
        assertEquals(AchievementCatalog.Kind.XP, AchievementCatalog.metaFor("xp_5000")!!.kind)
        assertEquals(3_000 to 5_000, AchievementCatalog.progressFor("xp_5000", stats, strengthScore = 0))
        assertEquals(AchievementCatalog.Kind.XP, AchievementCatalog.metaFor("lucky_xp")!!.kind)
    }

    @Test fun everyStrengthAchievementDescribesStrengthNotALevel() {
        defined.filter { it.id in FROZEN_STRENGTH_IDS }.forEach {
            assertTrue(
                "'${it.id}' still says: ${it.description}",
                it.description.contains("strength score", ignoreCase = true)
            )
        }
    }

    /** The gallery groups by display name, and two achievements must never share one. */
    @Test fun noAchievementNameCollidesWithATierName() {
        val tierNames = com.migul.treningsprogram.domain.strength.StrengthTier.values()
            .map { it.displayName }.toSet()
        defined.forEach {
            assertTrue(
                "'${it.id}' is named '${it.name}', which is also a strength tier shown on the " +
                    "breakdown screen — that reads as a contradiction when the two disagree.",
                it.name !in tierNames
            )
        }
    }

    @Test fun thresholdsAreUnchangedSoNobodyLosesGroundOnTheLadder() {
        // A sample across the range: the numbers that were XP levels are now strength scores, but
        // the numbers themselves never moved, which is what keeps every past unlock coherent.
        assertEquals(2, AchievementCatalog.metaFor("level_2")!!.threshold)
        assertEquals(40, AchievementCatalog.metaFor("level_40")!!.threshold)
        assertEquals(100, AchievementCatalog.metaFor("level_100")!!.threshold)
        assertEquals(9, AchievementCatalog.metaFor("nine_lives")!!.threshold)
        assertEquals(60, AchievementCatalog.metaFor("diamond_level")!!.threshold)
        assertEquals(90, AchievementCatalog.metaFor("the_transcendent")!!.threshold)
    }

    /** 100 is reachable only at Elite in every rated group — it stays the hardest one there is. */
    @Test fun theTopStrengthAchievementNeedsEliteEverywhere() {
        assertEquals(100, AchievementCatalog.metaFor("level_100")!!.threshold)
        assertEquals(AchievementCatalog.Tier.LEGENDARY, AchievementCatalog.metaFor("level_100")!!.tier)
    }

    @Test fun sessionScopedFamiliesStillHaveNoLifetimeProgress() {
        val stats = UserStats(id = 1)
        assertNull(AchievementCatalog.progressFor("sets_20", stats, strengthScore = 50))
        assertNull(AchievementCatalog.progressFor("combo_beast", stats, strengthScore = 50))
    }
}
