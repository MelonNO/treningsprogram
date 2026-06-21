package com.migul.treningsprogram.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.migul.treningsprogram.data.db.dao.*
import com.migul.treningsprogram.data.db.entity.*

@Database(
    entities = [
        Exercise::class,
        WorkoutSession::class,
        WorkoutSet::class,
        PlannedExercise::class,
        UserStats::class,
        Achievement::class,
        GymPreset::class,
        BodyMeasurement::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun plannedExerciseDao(): PlannedExerciseDao
    abstract fun userStatsDao(): UserStatsDao
    abstract fun achievementDao(): AchievementDao
    abstract fun gymPresetDao(): GymPresetDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_stats` (
                        `id` INTEGER NOT NULL,
                        `totalXp` INTEGER NOT NULL DEFAULT 0,
                        `level` INTEGER NOT NULL DEFAULT 1,
                        `currentStreak` INTEGER NOT NULL DEFAULT 0,
                        `bestStreak` INTEGER NOT NULL DEFAULT 0,
                        `totalWorkouts` INTEGER NOT NULL DEFAULT 0,
                        `totalPrs` INTEGER NOT NULL DEFAULT 0,
                        `lastWorkoutDateMs` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `achievements` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `isUnlocked` INTEGER NOT NULL DEFAULT 0,
                        `unlockedAtMs` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN isLogged INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN actualWeightKg REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN actualReps TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN actualSets INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN recommendedRestSeconds INTEGER NOT NULL DEFAULT 90")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS gym_presets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        equipmentJson TEXT NOT NULL DEFAULT '[]',
                        notes TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS body_measurements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dateMs INTEGER NOT NULL, weightKg REAL NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN isWarmup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN rpeLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM gym_presets WHERE name = 'No Equipment'")
            }
        }

        suspend fun seedPresets(dao: GymPresetDao) {
            if (dao.count() > 0) return
            val gson = Gson()
            dao.insert(GymPreset(name = "Full Equipment Gym",
                equipmentJson = gson.toJson(listOf("Barbell + weight plates", "Full dumbbell rack",
                    "Cable machine", "Smith machine", "Pull-up / dip station", "Flat bench",
                    "Incline bench", "Leg press machine", "Leg curl machine", "Leg extension machine",
                    "Lat pulldown machine", "Seated cable row", "Chest fly machine (pec deck)",
                    "Shoulder press machine")),
                notes = ""))
            dao.insert(GymPreset(name = "Hotel Gym",
                equipmentJson = gson.toJson(listOf("Dumbbells (limited range)", "Treadmill",
                    "Stationary bike", "Resistance bands")),
                notes = "Limited equipment — avoid barbell-only exercises"))
            dao.insert(GymPreset(name = "Home Gym",
                equipmentJson = gson.toJson(listOf("Pull-up bar", "Bench press bench",
                    "Barbell", "Dumbbells", "Ab roller")),
                notes = "Low ceiling — standing overhead barbell press not possible. Avoid any exercise requiring a barbell held overhead while standing."))
        }

        val DEFAULT_EXERCISES = listOf(
            Exercise(name = "Bench Press", muscleGroup = "Chest", equipment = "Barbell"),
            Exercise(name = "Incline Dumbbell Press", muscleGroup = "Chest", equipment = "Dumbbell"),
            Exercise(name = "Cable Flyes", muscleGroup = "Chest", equipment = "Cable"),
            Exercise(name = "Push-ups", muscleGroup = "Chest", equipment = "Bodyweight"),
            Exercise(name = "Deadlift", muscleGroup = "Back", equipment = "Barbell"),
            Exercise(name = "Barbell Row", muscleGroup = "Back", equipment = "Barbell"),
            Exercise(name = "Pull-ups", muscleGroup = "Back", equipment = "Bodyweight"),
            Exercise(name = "Lat Pulldown", muscleGroup = "Back", equipment = "Cable"),
            Exercise(name = "Cable Row", muscleGroup = "Back", equipment = "Cable"),
            Exercise(name = "Squat", muscleGroup = "Legs", equipment = "Barbell"),
            Exercise(name = "Leg Press", muscleGroup = "Legs", equipment = "Machine"),
            Exercise(name = "Romanian Deadlift", muscleGroup = "Legs", equipment = "Barbell"),
            Exercise(name = "Leg Curl", muscleGroup = "Legs", equipment = "Machine"),
            Exercise(name = "Leg Extension", muscleGroup = "Legs", equipment = "Machine"),
            Exercise(name = "Calf Raise", muscleGroup = "Legs", equipment = "Machine"),
            Exercise(name = "Overhead Press", muscleGroup = "Shoulders", equipment = "Barbell"),
            Exercise(name = "Lateral Raises", muscleGroup = "Shoulders", equipment = "Dumbbell"),
            Exercise(name = "Face Pulls", muscleGroup = "Shoulders", equipment = "Cable"),
            Exercise(name = "Bicep Curl", muscleGroup = "Arms", equipment = "Dumbbell"),
            Exercise(name = "Hammer Curl", muscleGroup = "Arms", equipment = "Dumbbell"),
            Exercise(name = "Tricep Pushdown", muscleGroup = "Arms", equipment = "Cable"),
            Exercise(name = "Skull Crusher", muscleGroup = "Arms", equipment = "Barbell"),
            Exercise(name = "Plank", muscleGroup = "Core", equipment = "Bodyweight"),
            Exercise(name = "Crunches", muscleGroup = "Core", equipment = "Bodyweight"),
            Exercise(name = "Russian Twist", muscleGroup = "Core", equipment = "Bodyweight"),
            Exercise(name = "Leg Raises", muscleGroup = "Core", equipment = "Bodyweight"),
            Exercise(name = "Outdoor Run", muscleGroup = "Cardio", equipment = "None"),
            Exercise(name = "Interval Run", muscleGroup = "Cardio", equipment = "None"),
            Exercise(name = "Tempo Run", muscleGroup = "Cardio", equipment = "None"),
            Exercise(name = "Easy Jog", muscleGroup = "Cardio", equipment = "None"),
            Exercise(name = "Treadmill Run", muscleGroup = "Cardio", equipment = "Treadmill"),
            Exercise(name = "Stationary Bike", muscleGroup = "Cardio", equipment = "Stationary bike"),
            Exercise(name = "Burpees", muscleGroup = "Cardio", equipment = "Bodyweight"),
            Exercise(name = "Mountain Climbers", muscleGroup = "Cardio", equipment = "Bodyweight"),
            Exercise(name = "High Knees", muscleGroup = "Cardio", equipment = "Bodyweight"),
            Exercise(name = "Jump Rope", muscleGroup = "Cardio", equipment = "Jump rope"),
        )

        val PREDEFINED_ACHIEVEMENTS = listOf(
            Achievement("first_workout",  "First Step",      "Complete your first workout",      "🏃"),
            Achievement("workouts_5",     "Warming Up",      "Complete 5 workouts",              "💪"),
            Achievement("workouts_10",    "Regular",         "Complete 10 workouts",             "🔥"),
            Achievement("workouts_25",    "Dedicated",       "Complete 25 workouts",             "⚡"),
            Achievement("workouts_50",    "Iron Will",       "Complete 50 workouts",             "🦾"),
            Achievement("workouts_100",   "Legend",          "Complete 100 workouts",            "🏆"),
            Achievement("streak_3",       "Hat Trick",       "3-day workout streak",             "📅"),
            Achievement("streak_7",       "Week Warrior",    "7-day workout streak",             "🗓️"),
            Achievement("streak_14",      "Unstoppable",     "14-day workout streak",            "⚡"),
            Achievement("first_pr",       "PR Crusher",      "Set your first personal record",   "💥"),
            Achievement("pr_5",           "Record Breaker",  "Set 5 personal records",           "🥇"),
            Achievement("level_5",        "Intermediate",    "Reach Level 5",                    "⭐"),
            Achievement("level_10",       "Advanced",        "Reach Level 10",                   "🌟"),
            Achievement("volume_beast",   "Volume Beast",    "Log 20+ sets in one workout",      "📊"),
        )
    }
}
