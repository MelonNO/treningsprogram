package com.migul.treningsprogram.di

import android.content.Context
import androidx.room.Room
import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.dao.BodyMeasurementDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "treningsprogram.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7
            )
            .build()

    @Provides fun provideExerciseDao(db: AppDatabase) = db.exerciseDao()
    @Provides fun provideSessionDao(db: AppDatabase) = db.workoutSessionDao()
    @Provides fun provideSetDao(db: AppDatabase) = db.workoutSetDao()
    @Provides fun providePlannedDao(db: AppDatabase) = db.plannedExerciseDao()
    @Provides fun provideUserStatsDao(db: AppDatabase) = db.userStatsDao()
    @Provides fun provideAchievementDao(db: AppDatabase) = db.achievementDao()
    @Provides fun provideGymPresetDao(db: AppDatabase) = db.gymPresetDao()
    @Provides fun provideBodyMeasurementDao(db: AppDatabase): BodyMeasurementDao = db.bodyMeasurementDao()
}
