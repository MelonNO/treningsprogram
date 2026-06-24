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
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14
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
    @Provides fun provideWeeklySummaryDao(db: AppDatabase) = db.weeklySummaryDao()
    @Provides fun provideProgramDao(db: AppDatabase) = db.programDao()
    @Provides fun provideXpEventDao(db: AppDatabase) = db.xpEventDao()
}
