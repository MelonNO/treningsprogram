package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.Achievement
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY isUnlocked DESC, name")
    fun observeAll(): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements WHERE id = :id")
    suspend fun getById(id: String): Achievement?

    @Query("SELECT COUNT(*) FROM achievements")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(achievements: List<Achievement>)

    @Update
    suspend fun update(achievement: Achievement)

    @Query("UPDATE achievements SET isUnlocked = 0, unlockedAtMs = 0")
    suspend fun resetAll()

    @Query("SELECT * FROM achievements ORDER BY name")
    suspend fun getAllOnce(): List<Achievement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(achievements: List<Achievement>)

    @Query("DELETE FROM achievements")
    suspend fun deleteAll()

    /**
     * Delete every row whose id is NOT in [validIds].
     * Called by GamificationRepository.ensureAchievementsSeeded() so that rows from
     * renamed/replaced achievement IDs in older builds are pruned on upgrade while the
     * unlock state of every still-valid id is preserved.
     */
    @Query("DELETE FROM achievements WHERE id NOT IN (:validIds)")
    suspend fun deleteOrphans(validIds: List<String>)

    /**
     * Update the display metadata (name, description, emoji) of an existing achievement row
     * without touching its unlock state. Used during seed reconciliation so that corrected
     * display names/descriptions (e.g. duplicate "Diamond" rename, combo_strength description
     * fix) propagate to upgraded devices that already have the old values stored.
     */
    @Query("UPDATE achievements SET name = :name, description = :description, emoji = :emoji WHERE id = :id")
    suspend fun updateMetadata(id: String, name: String, description: String, emoji: String)
}
