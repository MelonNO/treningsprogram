package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.UserStats
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStatsDao {
    @Query("SELECT * FROM user_stats WHERE id = 1")
    fun observe(): Flow<UserStats?>

    @Query("SELECT * FROM user_stats WHERE id = 1")
    suspend fun get(): UserStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: UserStats)
}
