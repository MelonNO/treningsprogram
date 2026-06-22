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
}
