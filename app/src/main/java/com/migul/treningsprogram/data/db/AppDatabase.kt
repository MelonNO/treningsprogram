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

            // === 86 creative replacements (varied mechanics) ===

            // --- per-session personal records (5) —— new mechanic ---
            Achievement("session_pr_2",      "Double Breakthrough",  "Set 2 PRs in a single session",                   "💥"),
            Achievement("session_pr_3",      "Triple Crown",         "Set 3 PRs in a single session",                   "🔥"),
            Achievement("session_pr_5",      "PR Rampage",           "Set 5 PRs in a single session",                   "⚡"),
            Achievement("session_pr_8",      "Record Demolisher",    "Set 8 PRs in a single session",                   "🎯"),
            Achievement("session_pr_10",     "Limit Annihilator",    "Set 10+ PRs in a single session",                 "👑"),

            // --- cross-stat combo achievements (25) —— brand new mechanic ---
            Achievement("combo_allrounder",  "The All-Rounder",      "7+ exercises and 2,000+ kg in one session",       "🏋️"),
            Achievement("combo_deep_focus",  "Deep Focus",           "3,000+ kg across 3 or fewer exercises",           "💎"),
            Achievement("combo_iron_end",    "Iron Endurance",       "35+ sets and 3,000+ kg in one session",           "💪"),
            Achievement("combo_less_more",   "Less Is More",         "4 or fewer exercises but 2,000+ kg",              "🎯"),
            Achievement("combo_marathon",    "Marathon Lifter",      "40+ sets and 8+ exercises in one session",        "🏃"),
            Achievement("combo_beast",       "Beast Mode",           "50+ sets and 5,000+ kg in one session",           "🦾"),
            Achievement("combo_singular",    "Singularly Focused",   "20+ sets with just one exercise",                 "🧠"),
            Achievement("combo_peak",        "Peak Performance",     "3+ PRs and 2,000+ kg in one session",             "🔥"),
            Achievement("combo_qty_qual",    "Quantity & Quality",   "20+ sets and 3+ PRs in one session",              "⭐"),
            Achievement("combo_explosive",   "Explosive Day",        "5+ PRs and 1,500+ kg in one session",             "💥"),
            Achievement("combo_clean_sweep", "Clean Sweep",          "PR on every exercise (min 3 each)",               "🏆"),
            Achievement("combo_every_rep",   "Every Rep Counts",     "PR on every exercise with 5+ exercises",          "👑"),
            Achievement("combo_diverse_pr",  "Diverse Excellence",   "6+ exercises and 2+ PRs in one session",         "🌟"),
            Achievement("combo_hercules",    "Herculean",            "5+ PRs and 3,000+ kg in one session",             "🦾"),
            Achievement("combo_world_tour",  "World Tour",           "12+ different exercises in one session",          "🌍"),
            Achievement("combo_jack",        "Jack of All Trades",   "10+ exercises and 15+ sets in one session",       "🎭"),
            Achievement("combo_big3",        "The Big Three",        "5,000+ kg with 3 or fewer exercises",             "💪"),
            Achievement("combo_pr_blitz",    "PR Blitz",             "7+ PRs in one session",                           "⚡"),
            Achievement("combo_strength",    "Strength & Speed",     "5+ PRs and 3,000+ kg in one session",             "🔱"),
            Achievement("combo_vol_artist",  "Volume Artist",        "2,500+ kg across 6+ exercises in one session",    "🎨"),
            Achievement("combo_relentless",  "The Relentless",       "60+ sets in one session",                         "🔥"),
            Achievement("combo_go_big",      "Go Big or Go Home",    "25+ sets and 5+ PRs in one session",              "⚡"),
            Achievement("combo_intensity",   "Maximum Intensity",    "20+ sets and 4,000+ kg in one session",           "💪"),
            Achievement("combo_specialist",  "The Specialist",       "30+ sets across 2 or fewer exercises",            "🏋️"),
            Achievement("combo_heavyweight", "Heavyweight Champion", "15,000+ kg total volume in one session",          "👑"),

            // --- workout count: character-driven milestones (15) ---
            Achievement("the_foundation",    "The Foundation",       "Complete 4 workouts — the habit begins",          "🧱"),
            Achievement("habit_lock",        "Habit Lock-In",        "Complete 6 workouts — it's becoming routine",     "🔐"),
            Achievement("the_initiate",      "The Initiate",         "Complete 8 workouts — you're officially in",      "🎖️"),
            Achievement("going_eleven",      "Going to Eleven",      "Complete 11 workouts",                            "🎸"),
            Achievement("three_week_club",   "Three Week Club",      "Complete 17 workouts",                            "📆"),
            Achievement("the_grind",         "The Grind",            "Complete 22 workouts — commitment has set in",    "⚙️"),
            Achievement("four_weeks_in",     "Four Weeks In",        "Complete 28 workouts — one full month of effort", "📅"),
            Achievement("five_week_warrior", "Five Week Warrior",    "Complete 35 workouts",                            "⚔️"),
            Achievement("six_week_champ",    "Six Week Champion",    "Complete 45 workouts",                            "🏅"),
            Achievement("the_dedicated",     "The Dedicated",        "Complete 55 workouts — it's who you are now",     "🎯"),
            Achievement("the_fanatic",       "The Fanatic",          "Complete 80 workouts",                            "🔥"),
            Achievement("one_twenty_five",   "One Twenty-Five",      "Complete 125 workouts",                           "💫"),
            Achievement("the_ironclad",      "The Ironclad",         "Complete 175 workouts",                           "🔗"),
            Achievement("four_centuries",    "Four Centuries",       "Complete 400 workouts",                           "🏛️"),
            Achievement("the_giant",         "The Giant",            "Complete 750 workouts",                           "🏔️"),

            // --- streak: evocative milestones (11) ---
            Achievement("six_sense",         "Six Sense",            "6-day streak — almost a full week",               "🌅"),
            Achievement("eight_days_week",   "Eight Days a Week",    "8-day streak — more than a full week",            "🎵"),
            Achievement("more_than_a_week",  "More Than a Week",     "11-day streak",                                   "💪"),
            Achievement("unlucky_thirteen",  "Unlucky? Thirteen!",   "13-day streak — luck has nothing to do with it",  "🍀"),
            Achievement("sweet_sixteen",     "Sweet Sixteen",        "16-day streak",                                   "🎂"),
            Achievement("almost_a_month",    "Almost a Month",       "25-day streak",                                   "📅"),
            Achievement("five_weeks_str",    "Five Weeks Straight",  "35-day streak",                                   "⚡"),
            Achievement("fifty_day_grind",   "Fifty Day Grind",      "50-day streak",                                   "🔥"),
            Achievement("the_obsessed",      "The Obsessed",         "75-day streak — dedication at another level",     "💪"),
            Achievement("century_challenge", "100-Day Challenge",    "100-day streak",                                  "🏆"),
            Achievement("five_month_miss",   "Five Month Mission",   "150-day streak",                                  "👑"),

            // --- PR milestones: personality names (10) ---
            Achievement("four_aces",         "Four Aces",            "Set 4 personal records — a winning hand",         "🃏"),
            Achievement("six_shooter",       "Six Shooter",          "Set 6 personal records",                          "🎯"),
            Achievement("the_octopus",       "The Octopus",          "Set 8 personal records",                          "🐙"),
            Achievement("bakers_dozen_pr",   "Baker's Dozen PRs",    "Set 12 personal records",                         "🍪"),
            Achievement("high_score",        "High Score",           "Set 20 personal records",                         "🎮"),
            Achievement("forty_records",     "Forty Records",        "Set 40 personal records",                         "💪"),
            Achievement("sixty_records",     "Sixty Records",        "Set 60 personal records",                         "🔥"),
            Achievement("elite_records",     "Elite Records",        "Set 150 personal records",                        "🏆"),
            Achievement("two_hundred_recs",  "Two Hundred Records",  "Set 200 personal records",                        "👑"),
            Achievement("quarter_k_prs",     "Quarter K PRs",        "Set 250 personal records",                        "🌠"),

            // --- level milestones: titles that feel earned (9) ---
            Achievement("nine_lives",        "Nine Lives",           "Reach Level 9",                                   "🐱"),
            Achievement("one_for_eleven",    "One for Eleven",       "Reach Level 11",                                  "🌟"),
            Achievement("battle_hardened",   "Battle Hardened",      "Reach Level 14",                                  "⚔️"),
            Achievement("seventeen_up",      "Seventeen Up",         "Reach Level 17",                                  "🚀"),
            Achievement("over_the_line",     "Over the Line",        "Reach Level 22",                                  "🎯"),
            Achievement("forty_five_lives",  "Forty-Five Lives",     "Reach Level 45",                                  "💪"),
            Achievement("diamond_level",     "Diamond",              "Reach Level 60",                                  "💎"),
            Achievement("the_overlord",      "The Overlord",         "Reach Level 80",                                  "🔱"),
            Achievement("the_transcendent",  "The Transcendent",     "Reach Level 90",                                  "✨"),

            // --- XP milestones: evocative names (10) ---
            Achievement("lucky_xp",          "Lucky Seven-Fifty",    "Earn 750 total XP",                               "🍀"),
            Achievement("xp_builder",        "XP Builder",           "Earn 1,500 total XP",                             "💫"),
            Achievement("xp_rolling",        "XP Rolling",           "Earn 3,500 total XP",                             "⚡"),
            Achievement("xp_surge",          "XP Surge",             "Earn 7,500 total XP",                             "🔥"),
            Achievement("xp_overflow",       "XP Overflow",          "Earn 15,000 total XP",                            "🏆"),
            Achievement("xp_fountain",       "XP Fountain",          "Earn 20,000 total XP",                            "🌟"),
            Achievement("xp_empire",         "XP Empire",            "Earn 35,000 total XP",                            "💎"),
            Achievement("xp_monument",       "XP Monument",          "Earn 200,000 total XP",                           "🌌"),
            Achievement("xp_colossus",       "XP Colossus",          "Earn 300,000 total XP",                           "🌠"),
            Achievement("xp_infinity",       "XP Infinity",          "Earn 1,000,000 total XP",                         "♾️"),

            // --- bonus creative combo (1) ---
            Achievement("combo_grind_set",   "The Grind Set",        "25+ sets, 4+ exercises, and 1+ PR in one session","💪"),
        )
    }
}
