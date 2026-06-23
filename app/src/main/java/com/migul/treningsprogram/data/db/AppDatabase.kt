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
    version = 9,
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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN exerciseDbId TEXT")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN matchConfidence REAL NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN matchSource TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN resolvedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercises ADD COLUMN exerciseDbId TEXT")
                db.execSQL("ALTER TABLE exercises ADD COLUMN matchConfidence REAL NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE exercises ADD COLUMN matchSource TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN resolvedAt INTEGER NOT NULL DEFAULT 0")
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
            // --- original 14 ---
            Achievement("first_workout",  "First Step",           "Complete your first workout",           "🏃"),
            Achievement("workouts_5",     "Warming Up",           "Complete 5 workouts",                   "💪"),
            Achievement("workouts_10",    "Regular",              "Complete 10 workouts",                  "🔥"),
            Achievement("workouts_25",    "Dedicated",            "Complete 25 workouts",                  "⚡"),
            Achievement("workouts_50",    "Iron Will",            "Complete 50 workouts",                  "🦾"),
            Achievement("workouts_100",   "Legend",               "Complete 100 workouts",                 "🏆"),
            Achievement("streak_3",       "Hat Trick",            "3-day workout streak",                  "📅"),
            Achievement("streak_7",       "Week Warrior",         "7-day workout streak",                  "🗓️"),
            Achievement("streak_14",      "Unstoppable",          "14-day workout streak",                 "⚡"),
            Achievement("first_pr",       "PR Crusher",           "Set your first personal record",        "💥"),
            Achievement("pr_5",           "Record Breaker",       "Set 5 personal records",                "🥇"),
            Achievement("level_5",        "Intermediate",         "Reach Level 5",                         "⭐"),
            Achievement("level_10",       "Advanced",             "Reach Level 10",                        "🌟"),
            Achievement("volume_beast",   "Volume Beast",         "Log 20+ sets in one workout",           "📊"),

            // --- workout count milestones (16 new) ---
            Achievement("workouts_2",     "Double Down",          "Complete 2 workouts",                   "💪"),
            Achievement("workouts_3",     "Trilogy",              "Complete 3 workouts",                   "🔁"),
            Achievement("workouts_7",     "Week One Done",        "Complete 7 workouts",                   "🌟"),
            Achievement("workouts_15",    "Habit Forming",        "Complete 15 workouts",                  "🌱"),
            Achievement("workouts_20",    "Three Weeks In",       "Complete 20 workouts",                  "📆"),
            Achievement("workouts_30",    "Monthly Grind",        "Complete 30 workouts",                  "🗓️"),
            Achievement("workouts_40",    "Forty Strong",         "Complete 40 workouts",                  "💪"),
            Achievement("workouts_60",    "Diamond",              "Complete 60 workouts",                  "💎"),
            Achievement("workouts_75",    "Three Quarter Century","Complete 75 workouts",                  "🏅"),
            Achievement("workouts_150",   "Endurance",            "Complete 150 workouts",                 "🔥"),
            Achievement("workouts_200",   "Grinder",              "Complete 200 workouts",                 "⚡"),
            Achievement("workouts_250",   "Quarter K",            "Complete 250 workouts",                 "🏆"),
            Achievement("workouts_300",   "Machine",              "Complete 300 workouts",                 "🤖"),
            Achievement("workouts_365",   "Full Year Athlete",    "Complete 365 workouts",                 "📅"),
            Achievement("workouts_500",   "Elite Athlete",        "Complete 500 workouts",                 "🏆"),
            Achievement("workouts_1000",  "Mythical",             "Complete 1000 workouts",                "👑"),

            // --- current streak milestones (13 new) ---
            Achievement("streak_2",       "Two in a Row",         "2-day workout streak",                  "📅"),
            Achievement("streak_4",       "Long Weekend",         "4-day workout streak",                  "🏃"),
            Achievement("streak_5",       "School Week",          "5-day workout streak",                  "📚"),
            Achievement("streak_10",      "Ten Day Streak",       "10-day workout streak",                 "🔟"),
            Achievement("streak_15",      "Fortnight Run",        "15-day workout streak",                 "⚡"),
            Achievement("streak_20",      "Twenty Strong",        "20-day workout streak",                 "💪"),
            Achievement("streak_21",      "Three Weeks",          "21-day workout streak",                 "🔥"),
            Achievement("streak_30",      "Monthly Warrior",      "30-day workout streak",                 "⚡"),
            Achievement("streak_45",      "Six Week Streak",      "45-day workout streak",                 "💪"),
            Achievement("streak_60",      "Two Month Push",       "60-day workout streak",                 "🏋️"),
            Achievement("streak_90",      "Quarter Year Streak",  "90-day workout streak",                 "🌟"),
            Achievement("streak_180",     "Half Year Hero",       "180-day workout streak",                "⭐"),
            Achievement("streak_365",     "Streak God",           "Train every day for a year",            "👑"),

            // --- personal record milestones (10 new) ---
            Achievement("pr_2",           "Back to Back",         "Set 2 personal records",                "💥"),
            Achievement("pr_3",           "Triple Threat",        "Set 3 personal records",                "🔥"),
            Achievement("pr_7",           "Lucky Seven PRs",      "Set 7 personal records",                "🍀"),
            Achievement("pr_10",          "Record Machine",       "Set 10 personal records",               "⚡"),
            Achievement("pr_15",          "PR Fever",             "Set 15 personal records",               "🔥"),
            Achievement("pr_25",          "PR Hunter",            "Set 25 personal records",               "🎯"),
            Achievement("pr_30",          "Thirty Records",       "Set 30 personal records",               "💪"),
            Achievement("pr_50",          "Limit Breaker",        "Set 50 personal records",               "💪"),
            Achievement("pr_75",          "PR Addict",            "Set 75 personal records",               "🔥"),
            Achievement("pr_100",         "Beyond Limits",        "Set 100 personal records",              "👑"),

            // --- level milestones (16 new) ---
            Achievement("level_2",        "Leveled Up",           "Reach Level 2",                         "⭐"),
            Achievement("level_3",        "Getting Started",      "Reach Level 3",                         "⭐"),
            Achievement("level_4",        "Climbing",             "Reach Level 4",                         "⭐"),
            Achievement("level_6",        "On the Rise",          "Reach Level 6",                         "🌟"),
            Achievement("level_7",        "Week Seven",           "Reach Level 7",                         "🌟"),
            Achievement("level_8",        "Momentum",             "Reach Level 8",                         "🌟"),
            Achievement("level_12",       "Skilled",              "Reach Level 12",                        "💫"),
            Achievement("level_15",       "Pro",                  "Reach Level 15",                        "🏅"),
            Achievement("level_20",       "Seasoned",             "Reach Level 20",                        "🥇"),
            Achievement("level_25",       "Grandmaster",          "Reach Level 25",                        "👑"),
            Achievement("level_30",       "Legendary",            "Reach Level 30",                        "🏆"),
            Achievement("level_35",       "Mythic",               "Reach Level 35",                        "⚡"),
            Achievement("level_40",       "Immortal",             "Reach Level 40",                        "🔱"),
            Achievement("level_50",       "Godlike",              "Reach Level 50",                        "👑"),
            Achievement("level_75",       "Beyond Human",         "Reach Level 75",                        "🌌"),
            Achievement("level_100",      "Ascended",             "Reach Level 100",                       "🌠"),

            // --- sets in one session (9 new) ---
            Achievement("sets_3",         "Three's Company",      "Log 3 sets in one workout",             "💪"),
            Achievement("sets_5",         "Quick Hit",            "Log 5 sets in one workout",             "⚡"),
            Achievement("sets_7",         "Lucky Seven",          "Log 7 sets in one workout",             "🍀"),
            Achievement("sets_10",        "Working Hard",         "Log 10 sets in one workout",            "🔥"),
            Achievement("sets_15",        "Solid Session",        "Log 15 sets in one workout",            "💪"),
            Achievement("sets_25",        "Volume Freak",         "Log 25 sets in one workout",            "📊"),
            Achievement("sets_30",        "Marathon Session",     "Log 30 sets in one workout",            "🏃"),
            Achievement("sets_40",        "Relentless",           "Log 40 sets in one workout",            "🔥"),
            Achievement("sets_50",        "Iron Machine",         "Log 50 sets in one workout",            "🦾"),

            // --- total XP milestones (12 new) ---
            Achievement("xp_250",         "Gaining Ground",       "Earn 250 total XP",                     "💫"),
            Achievement("xp_500",         "First Harvest",        "Earn 500 total XP",                     "⚡"),
            Achievement("xp_1000",        "XP Collector",         "Earn 1,000 total XP",                   "💫"),
            Achievement("xp_2500",        "Rising Star",          "Earn 2,500 total XP",                   "⭐"),
            Achievement("xp_5000",        "XP Grinder",           "Earn 5,000 total XP",                   "🔥"),
            Achievement("xp_10000",       "XP Machine",           "Earn 10,000 total XP",                  "⚡"),
            Achievement("xp_25000",       "XP Addict",            "Earn 25,000 total XP",                  "🏆"),
            Achievement("xp_50000",       "Point Hoarder",        "Earn 50,000 total XP",                  "🌟"),
            Achievement("xp_75000",       "XP Maniac",            "Earn 75,000 total XP",                  "✨"),
            Achievement("xp_100000",      "XP Legend",            "Earn 100,000 total XP",                 "👑"),
            Achievement("xp_150000",      "XP Overlord",          "Earn 150,000 total XP",                 "🌌"),
            Achievement("xp_500000",      "XP God",               "Earn 500,000 total XP",                 "🌠"),

            // --- exercise variety in one session (6 new) ---
            Achievement("ex_variety_3",   "Trinity",              "Do 3 different exercises in one workout","🎯"),
            Achievement("ex_variety_5",   "Five Star Session",    "Do 5 exercises in one workout",          "⭐"),
            Achievement("ex_variety_7",   "Magnificent Seven",    "Do 7 exercises in one workout",          "💪"),
            Achievement("ex_variety_10",  "Full Program",         "Do 10 exercises in one workout",         "🔥"),
            Achievement("ex_variety_12",  "Jack of All Lifts",    "Do 12 exercises in one workout",         "🏋️"),
            Achievement("ex_variety_15",  "Workout Encyclopedia", "Do 15 exercises in one workout",         "📚"),

            // --- total volume in one session in kg (11 new) ---
            Achievement("vol_100",        "Century Volume",       "100 kg total volume in one session",    "💪"),
            Achievement("vol_250",        "Quarter Ton Session",  "250 kg total volume in one session",    "💪"),
            Achievement("vol_500",        "Half Ton",             "500 kg total volume in one session",    "🔥"),
            Achievement("vol_750",        "Three Quarter Ton",    "750 kg total volume in one session",    "⚡"),
            Achievement("vol_1000",       "One Ton",              "1,000 kg total volume in one session",  "💪"),
            Achievement("vol_2000",       "Two Ton",              "2,000 kg total volume in one session",  "⚡"),
            Achievement("vol_3000",       "Triple Ton",           "3,000 kg total volume in one session",  "🏋️"),
            Achievement("vol_5000",       "Five Ton Monster",     "5,000 kg total volume in one session",  "🔥"),
            Achievement("vol_7500",       "Seven and a Half Tons","7,500 kg total volume in one session",  "💪"),
            Achievement("vol_10000",      "Ten Ton Titan",        "10,000 kg total volume in one session", "👑"),
            Achievement("vol_20000",      "Freight Train",        "20,000 kg total volume in one session", "🚂"),

            // --- best streak ever achieved (7) ---
            Achievement("best_3",         "Weekend Warrior",      "Achieve a best streak of 3+ days",      "🏅"),
            Achievement("best_7",         "Best Week Yet",        "Achieve a best streak of 7+ days",      "📅"),
            Achievement("best_14",        "Two Week Best",        "Achieve a best streak of 14+ days",     "⚡"),
            Achievement("best_21",        "Three Week Best",      "Achieve a best streak of 21+ days",     "💪"),
            Achievement("best_30",        "Best Month",           "Achieve a best streak of 30+ days",     "🏆"),
            Achievement("best_60",        "Best Two Months",      "Achieve a best streak of 60+ days",     "🔥"),
            Achievement("best_90",        "Quarter Year Best",    "Achieve a best streak of 90+ days",     "🌟"),

            // === 86 more to reach 200 total ===

            // --- workout count fill-in (15) ---
            Achievement("workouts_4",     "On a Roll",            "Complete 4 workouts",                   "💪"),
            Achievement("workouts_6",     "Half Dozen",           "Complete 6 workouts",                   "💪"),
            Achievement("workouts_8",     "Eight Is Great",       "Complete 8 workouts",                   "🌟"),
            Achievement("workouts_11",    "Going Eleven",         "Complete 11 workouts",                  "⚡"),
            Achievement("workouts_17",    "Seventeen In",         "Complete 17 workouts",                  "🌱"),
            Achievement("workouts_22",    "Twenty-Two",           "Complete 22 workouts",                  "📆"),
            Achievement("workouts_28",    "Four Weeks",           "Complete 28 workouts",                  "🗓️"),
            Achievement("workouts_35",    "Thirty-Five",          "Complete 35 workouts",                  "💪"),
            Achievement("workouts_45",    "Forty-Five",           "Complete 45 workouts",                  "💎"),
            Achievement("workouts_55",    "Fifty-Five",           "Complete 55 workouts",                  "🏅"),
            Achievement("workouts_80",    "Eighty Strong",        "Complete 80 workouts",                  "🔥"),
            Achievement("workouts_125",   "One Twenty-Five",      "Complete 125 workouts",                 "⚡"),
            Achievement("workouts_175",   "One Seventy-Five",     "Complete 175 workouts",                 "🏆"),
            Achievement("workouts_400",   "Four Hundred",         "Complete 400 workouts",                 "🤖"),
            Achievement("workouts_750",   "Titan",                "Complete 750 workouts",                 "🦾"),

            // --- streak fill-in (11) ---
            Achievement("streak_6",       "Six Pack Streak",      "6-day workout streak",                  "🔥"),
            Achievement("streak_8",       "Eight Day Run",        "8-day workout streak",                  "📅"),
            Achievement("streak_11",      "Eleven Eleven",        "11-day workout streak",                 "🌟"),
            Achievement("streak_13",      "Unlucky Thirteen",     "13-day workout streak",                 "🍀"),
            Achievement("streak_16",      "Sweet Sixteen",        "16-day workout streak",                 "⭐"),
            Achievement("streak_25",      "Quarter Month",        "25-day workout streak",                 "⚡"),
            Achievement("streak_35",      "Five Week Streak",     "35-day workout streak",                 "💪"),
            Achievement("streak_50",      "Fifty Day Grind",      "50-day workout streak",                 "🔥"),
            Achievement("streak_75",      "Seventy-Five Days",    "75-day workout streak",                 "🌟"),
            Achievement("streak_100",     "Century Streak",       "100-day workout streak",                "🏆"),
            Achievement("streak_150",     "Five Month Streak",    "150-day workout streak",                "👑"),

            // --- PR fill-in (10) ---
            Achievement("pr_4",           "Fab Four PRs",         "Set 4 personal records",                "💥"),
            Achievement("pr_6",           "Half Dozen PRs",       "Set 6 personal records",                "🔥"),
            Achievement("pr_8",           "Eight PRs",            "Set 8 personal records",                "⚡"),
            Achievement("pr_12",          "Dozen PRs",            "Set 12 personal records",               "🎯"),
            Achievement("pr_20",          "Twenty PRs",           "Set 20 personal records",               "💪"),
            Achievement("pr_40",          "Forty PRs",            "Set 40 personal records",               "🔥"),
            Achievement("pr_60",          "Sixty PRs",            "Set 60 personal records",               "💪"),
            Achievement("pr_150",         "PR Machine",           "Set 150 personal records",              "🏆"),
            Achievement("pr_200",         "Two Hundred PRs",      "Set 200 personal records",              "👑"),
            Achievement("pr_250",         "Quarter K PRs",        "Set 250 personal records",              "🌠"),

            // --- level fill-in (9) ---
            Achievement("level_9",        "Level 9",              "Reach Level 9",                         "🌟"),
            Achievement("level_11",       "Level 11",             "Reach Level 11",                        "💫"),
            Achievement("level_14",       "Level 14",             "Reach Level 14",                        "🏅"),
            Achievement("level_17",       "Level 17",             "Reach Level 17",                        "🥇"),
            Achievement("level_22",       "Level 22",             "Reach Level 22",                        "👑"),
            Achievement("level_45",       "Level 45",             "Reach Level 45",                        "⚡"),
            Achievement("level_60",       "Level 60",             "Reach Level 60",                        "🔱"),
            Achievement("level_80",       "Level 80",             "Reach Level 80",                        "🌌"),
            Achievement("level_90",       "Level 90",             "Reach Level 90",                        "🌠"),

            // --- sets per session fill-in (8) ---
            Achievement("sets_4",         "Four Setter",          "Log 4 sets in one workout",             "💪"),
            Achievement("sets_6",         "Six Sets",             "Log 6 sets in one workout",             "⚡"),
            Achievement("sets_8",         "Eight-Pack",           "Log 8 sets in one workout",             "🍀"),
            Achievement("sets_12",        "Twelve Set Day",       "Log 12 sets in one workout",            "🔥"),
            Achievement("sets_18",        "Eighteen Sets",        "Log 18 sets in one workout",            "💪"),
            Achievement("sets_35",        "Thirty-Five Sets",     "Log 35 sets in one workout",            "🔥"),
            Achievement("sets_45",        "Forty-Five Sets",      "Log 45 sets in one workout",            "⚡"),
            Achievement("sets_60",        "Sixty Setter",         "Log 60 sets in one workout",            "🦾"),

            // --- total XP fill-in (10) ---
            Achievement("xp_750",         "XP Stash",             "Earn 750 total XP",                     "⚡"),
            Achievement("xp_1500",        "XP Builder",           "Earn 1,500 total XP",                   "💫"),
            Achievement("xp_3500",        "XP Hoarder",           "Earn 3,500 total XP",                   "🔥"),
            Achievement("xp_7500",        "XP Surge",             "Earn 7,500 total XP",                   "⚡"),
            Achievement("xp_15000",       "XP Overflow",          "Earn 15,000 total XP",                  "🏆"),
            Achievement("xp_20000",       "XP Fountain",          "Earn 20,000 total XP",                  "🌟"),
            Achievement("xp_35000",       "XP Empire",            "Earn 35,000 total XP",                  "⭐"),
            Achievement("xp_200000",      "XP Monument",          "Earn 200,000 total XP",                 "🌌"),
            Achievement("xp_300000",      "XP Colossus",          "Earn 300,000 total XP",                 "🌠"),
            Achievement("xp_1000000",     "XP Infinity",          "Earn 1,000,000 total XP",               "♾️"),

            // --- exercise variety fill-in (7) ---
            Achievement("ex_variety_2",   "Dynamic Duo",          "Do 2 different exercises in one workout","💪"),
            Achievement("ex_variety_4",   "Fab Four Exercises",   "Do 4 exercises in one workout",          "⚡"),
            Achievement("ex_variety_6",   "Sixfold",              "Do 6 exercises in one workout",          "🔥"),
            Achievement("ex_variety_8",   "Eight-Way",            "Do 8 exercises in one workout",          "💪"),
            Achievement("ex_variety_9",   "Nine Lives",           "Do 9 exercises in one workout",          "🍀"),
            Achievement("ex_variety_11",  "Eleven Exercises",     "Do 11 exercises in one workout",         "🌟"),
            Achievement("ex_variety_13",  "Baker's Dozen Lifts",  "Do 13 exercises in one workout",         "📚"),

            // --- session volume fill-in (8) ---
            Achievement("vol_150",        "One Fifty",            "150 kg total volume in one session",    "💪"),
            Achievement("vol_400",        "Four Hundred Kilos",   "400 kg total volume in one session",    "💪"),
            Achievement("vol_600",        "Six Hundred Kilos",    "600 kg total volume in one session",    "🔥"),
            Achievement("vol_1500",       "Ton and a Half",       "1,500 kg total volume in one session",  "⚡"),
            Achievement("vol_2500",       "Two and a Half Tons",  "2,500 kg total volume in one session",  "💪"),
            Achievement("vol_4000",       "Four Ton Session",     "4,000 kg total volume in one session",  "🏋️"),
            Achievement("vol_6000",       "Six Ton Session",      "6,000 kg total volume in one session",  "🔥"),
            Achievement("vol_15000",      "Fifteen Ton Beast",    "15,000 kg total volume in one session", "👑"),

            // --- best streak fill-in (8) ---
            Achievement("best_5",         "Five Day Best",        "Achieve a best streak of 5+ days",      "📚"),
            Achievement("best_10",        "Ten Day Best",         "Achieve a best streak of 10+ days",     "🔟"),
            Achievement("best_25",        "Quarter Month Best",   "Achieve a best streak of 25+ days",     "⚡"),
            Achievement("best_45",        "Six Week Best",        "Achieve a best streak of 45+ days",     "💪"),
            Achievement("best_120",       "Four Month Best",      "Achieve a best streak of 120+ days",    "🏆"),
            Achievement("best_180",       "Half Year Best",       "Achieve a best streak of 180+ days",    "🌟"),
            Achievement("best_250",       "Eight Month Best",     "Achieve a best streak of 250+ days",    "🌌"),
            Achievement("best_365",       "Full Year Best",       "Achieve a best streak of 365+ days",    "👑"),
        )
    }
}
