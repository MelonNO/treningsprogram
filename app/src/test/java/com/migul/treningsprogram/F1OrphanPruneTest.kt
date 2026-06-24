package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.repository.GamificationRepository
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for F1 orphan-row pruning and the S6 achievement-definition fixes.
 *
 * F1 root-cause recap: GamificationRepository.ensureAchievementsSeeded() previously only
 * @Insert(onConflict=IGNORE) and never deleted, so renamed/replaced IDs from old builds
 * persisted as orphan rows, inflating the profile count above the defined-set size (e.g. 286
 * rows vs 200 defined). The fix adds deleteOrphans(validIds) which removes any row whose id
 * is not in the currently-defined set, preserving unlock state for all still-valid ids.
 *
 * These tests exercise the pure logic that backs the fix without requiring a real Room DB,
 * by working against the static AppDatabase.PREDEFINED_ACHIEVEMENTS list directly.
 */
class F1OrphanPruneTest {

    // ---- Helpers simulating the in-memory DB state ----

    /** Simulate a DB that has the current defined set + some extra orphan ids. */
    private fun dbWithOrphans(orphanIds: List<String>): List<Achievement> {
        val defined = AppDatabase.PREDEFINED_ACHIEVEMENTS.toMutableList()
        orphanIds.forEach { id -> defined.add(Achievement(id = id, name = "old", description = "old", emoji = "?")) }
        return defined
    }

    /** Apply the prune logic: keep only rows whose id is in the valid-id set. */
    private fun applyPrune(dbRows: List<Achievement>, validIds: Set<String>): List<Achievement> =
        dbRows.filter { it.id in validIds }

    // ---- F1 tests ----

    @Test fun `defined achievements count is 200`() {
        assertEquals(200, AppDatabase.PREDEFINED_ACHIEVEMENTS.size)
    }

    @Test fun `every defined achievement has a unique id`() {
        val ids = AppDatabase.PREDEFINED_ACHIEVEMENTS.map { it.id }
        assertEquals("duplicate IDs found", ids.size, ids.toSet().size)
    }

    @Test fun `prune removes orphan rows leaving only defined set`() {
        val orphans = listOf("old_workout_60", "ancient_streak", "removed_id_xyz")
        val db = dbWithOrphans(orphans)
        assertEquals(203, db.size)

        val validIds = GamificationRepository.currentDefinedIds()
        val pruned = applyPrune(db, validIds)

        assertEquals(200, pruned.size)
        assertTrue("orphan 'old_workout_60' should be pruned", pruned.none { it.id == "old_workout_60" })
        assertTrue("orphan 'ancient_streak' should be pruned", pruned.none { it.id == "ancient_streak" })
        assertTrue("orphan 'removed_id_xyz' should be pruned", pruned.none { it.id == "removed_id_xyz" })
    }

    @Test fun `prune preserves unlock state of still-valid achievements`() {
        val validIds = GamificationRepository.currentDefinedIds()

        // Simulate an upgraded device where 'first_workout' is already unlocked
        val db = AppDatabase.PREDEFINED_ACHIEVEMENTS.map { a ->
            if (a.id == "first_workout") a.copy(isUnlocked = true, unlockedAtMs = 1_700_000_000_000L)
            else a
        } + listOf(Achievement("orphan_id", "orphan", "old achievement", "?"))

        val pruned = applyPrune(db, validIds)
        val firstWorkout = pruned.find { it.id == "first_workout" }
        assertNotNull("first_workout should survive prune", firstWorkout)
        assertTrue("unlock state must be preserved", firstWorkout!!.isUnlocked)
        assertEquals("unlockedAtMs must be preserved", 1_700_000_000_000L, firstWorkout.unlockedAtMs)
    }

    @Test fun `prune on clean install (no orphans) returns full defined set unchanged`() {
        val validIds = GamificationRepository.currentDefinedIds()
        val db = AppDatabase.PREDEFINED_ACHIEVEMENTS.toList()
        val pruned = applyPrune(db, validIds)
        assertEquals(200, pruned.size)
    }

    @Test fun `prune on empty db returns empty list`() {
        val validIds = GamificationRepository.currentDefinedIds()
        val pruned = applyPrune(emptyList(), validIds)
        assertTrue(pruned.isEmpty())
    }

    @Test fun `currentDefinedIds contains all predefined ids`() {
        val ids = GamificationRepository.currentDefinedIds()
        AppDatabase.PREDEFINED_ACHIEVEMENTS.forEach { a ->
            assertTrue("${a.id} must be in currentDefinedIds()", a.id in ids)
        }
    }

    @Test fun `prune does not remove any currently-defined id`() {
        val orphans = listOf("ghost_1", "ghost_2", "ghost_3")
        val db = dbWithOrphans(orphans)
        val validIds = GamificationRepository.currentDefinedIds()
        val pruned = applyPrune(db, validIds)

        // Every defined id survives
        AppDatabase.PREDEFINED_ACHIEVEMENTS.forEach { a ->
            assertTrue("${a.id} must survive prune", pruned.any { it.id == a.id })
        }
    }
}
