package com.migul.treningsprogram.data.backup

import com.google.gson.annotations.SerializedName
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.Exercise
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.Program
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet

/**
 * Backup format versioning.
 *
 * v1 — the original manual export ([ExportRepository] schema_version = 1): wipe-and-replace.
 *      Missing the Exercise (custom library) and GymPreset tables and several preferences.
 * v2 — the cloud-backup shape: all 8 entity tables + all backup-eligible prefs,
 *      restored via MERGE (never wipe). See [BackupMigrations] for v1 -> v2.
 * v3 — adds the E2 `programs` table (named saved programs + mesocycle/deload state). The
 *      planned_exercises.programId column rides through whole-entity Gson automatically (like B2's
 *      rationale column did); v3 only adds the new top-level `programs` list. See v2 -> v3.
 * v4 — adds three previously-missed preferences (restDaysCsv, autoRebalanceEnabled,
 *      dayBoundaryHour) so a restore keeps the pinned rest days and day boundary. Absent keys in
 *      older backups fall back to the field defaults on deserialize; the step only bumps the
 *      version. See v3 -> v4.
 *
 * To add a future version, bump [CURRENT_BACKUP_VERSION] and register a step in
 * [BackupMigrations.STEPS]. Each step migrates the raw JSON tree from version N to N+1, so the
 * chain is composable and an arbitrarily old backup migrates cleanly into the current shape.
 */
const val CURRENT_BACKUP_VERSION = 5

/**
 * Backup-eligible preferences. The Anthropic API key is intentionally NEVER serialized here.
 *
 * Defaults below MUST match [com.migul.treningsprogram.data.preferences.PreferencesManager]'s
 * getter defaults so the merge engine can tell "user changed this on the new phone" (value !=
 * default -> phone wins) from "still default" (-> backup value is used).
 */
data class BackupPreferences(
    val daysPerWeek: Int = DEFAULT_DAYS_PER_WEEK,
    val fitnessGoal: String = DEFAULT_FITNESS_GOAL,
    val experienceLevel: String = DEFAULT_EXPERIENCE_LEVEL,
    val sessionDurationMinutes: Int = DEFAULT_SESSION_DURATION,
    val separateCardioDays: Boolean = DEFAULT_SEPARATE_CARDIO,
    val injuries: String = DEFAULT_STRING,
    val injurySeverity: String = DEFAULT_STRING,
    val priorityMuscles: String = DEFAULT_STRING,
    val dislikedExercises: String = DEFAULT_STRING,
    val onboardingContext: String = DEFAULT_STRING,
    val wizardEquipment: String = DEFAULT_STRING,
    val hasCompletedOnboarding: Boolean = DEFAULT_BOOL,
    val restTimerSeconds: Int = DEFAULT_REST_TIMER,
    val dailyChallengesJson: String = DEFAULT_STRING,
    val selectedGymPresetId: Long = DEFAULT_GYM_PRESET_ID,
    // v4: the three training-relevant prefs a restore used to silently lose.
    val restDaysCsv: String = DEFAULT_STRING,
    val autoRebalanceEnabled: Boolean = DEFAULT_AUTO_REBALANCE,
    val dayBoundaryHour: Int = DEFAULT_DAY_BOUNDARY_HOUR,
    // v5 (rest-UX 2026-07, item 4): manual rest-time mode + the two category times.
    val manualRestEnabled: Boolean = DEFAULT_BOOL,
    val manualRestHeavySeconds: Int = DEFAULT_MANUAL_REST_HEAVY,
    val manualRestAccessorySeconds: Int = DEFAULT_MANUAL_REST_ACCESSORY
) {
    companion object {
        const val DEFAULT_DAYS_PER_WEEK = 4
        const val DEFAULT_FITNESS_GOAL = "Hypertrophy"
        const val DEFAULT_EXPERIENCE_LEVEL = "Intermediate"
        const val DEFAULT_SESSION_DURATION = 60
        const val DEFAULT_AUTO_REBALANCE = true
        const val DEFAULT_DAY_BOUNDARY_HOUR = 4  // = DayBoundary.DEFAULT_CUTOFF_HOUR
        const val DEFAULT_SEPARATE_CARDIO = false
        const val DEFAULT_STRING = ""
        const val DEFAULT_BOOL = false
        const val DEFAULT_REST_TIMER = 90
        const val DEFAULT_GYM_PRESET_ID = -1L
        const val DEFAULT_MANUAL_REST_HEAVY = 180      // = ManualRestTimes.DEFAULT_HEAVY_SECONDS
        const val DEFAULT_MANUAL_REST_ACCESSORY = 90   // = ManualRestTimes.DEFAULT_ACCESSORY_SECONDS
    }
}

/**
 * The versioned backup envelope. All user data EXCEPT the API key.
 */
data class BackupEnvelope(
    @SerializedName("schema_version") val schemaVersion: Int = CURRENT_BACKUP_VERSION,
    @SerializedName("exported_at") val exportedAt: String = "",
    val sessions: List<WorkoutSession> = emptyList(),
    val sets: List<WorkoutSet> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
    @SerializedName("user_stats") val userStats: UserStats? = null,
    @SerializedName("body_measurements") val bodyMeasurements: List<BodyMeasurement> = emptyList(),
    @SerializedName("planned_exercises") val plannedExercises: List<PlannedExercise> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    @SerializedName("gym_presets") val gymPresets: List<GymPreset> = emptyList(),
    // E2 (v3): named saved programs. Empty for v1/v2 backups (the migration adds an empty list).
    val programs: List<Program> = emptyList(),
    val preferences: BackupPreferences = BackupPreferences()
)
