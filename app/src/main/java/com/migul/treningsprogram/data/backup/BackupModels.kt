package com.migul.treningsprogram.data.backup

import com.google.gson.annotations.SerializedName
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.Exercise
import com.migul.treningsprogram.data.db.entity.ExerciseNote
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.data.db.entity.LiftGoal
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
 * v5 — widens the preferences object again (manualRestEnabled / manualRestHeavySeconds /
 *      manualRestAccessorySeconds). See v4 -> v5.
 * v6 — feature batch 2026-07-03: adds the N5 `goals` (lift_goals) and N7 `exercise_notes`
 *      tables as two new top-level lists. Older backups simply have neither; the migration
 *      step adds empty arrays. See v5 -> v6.
 *
 * v8 — QoL batch 2026-08-03 (item 04): widens the preferences object with the two
 *      exercise-info correction maps (exerciseFlagsJson / exerciseOverridesJson — mismatch flags
 *      and re-match overrides, serialized by ExerciseInfoCorrections.Codec). Absent keys in older
 *      backups deserialize to "" (no corrections); restore union-merges per key (device wins).
 *
 * To add a future version, bump [CURRENT_BACKUP_VERSION] and register a step in
 * [BackupMigrations.STEPS]. Each step migrates the raw JSON tree from version N to N+1, so the
 * chain is composable and an arbitrarily old backup migrates cleanly into the current shape.
 */
const val CURRENT_BACKUP_VERSION = 8

/**
 * Backup-eligible preferences. The Anthropic API key is intentionally NEVER serialized here.
 *
 * Defaults below MUST match [com.migul.treningsprogram.data.preferences.PreferencesManager]'s
 * getter defaults so the merge engine can tell "user changed this on the new phone" (value !=
 * default -> phone wins) from "still default" (-> backup value is used).
 */
data class BackupPreferences(
    @SerializedName("daysPerWeek") val daysPerWeek: Int = DEFAULT_DAYS_PER_WEEK,
    @SerializedName("fitnessGoal") val fitnessGoal: String = DEFAULT_FITNESS_GOAL,
    @SerializedName("experienceLevel") val experienceLevel: String = DEFAULT_EXPERIENCE_LEVEL,
    @SerializedName("sessionDurationMinutes") val sessionDurationMinutes: Int = DEFAULT_SESSION_DURATION,
    @SerializedName("separateCardioDays") val separateCardioDays: Boolean = DEFAULT_SEPARATE_CARDIO,
    @SerializedName("injuries") val injuries: String = DEFAULT_STRING,
    @SerializedName("injurySeverity") val injurySeverity: String = DEFAULT_STRING,
    @SerializedName("priorityMuscles") val priorityMuscles: String = DEFAULT_STRING,
    @SerializedName("dislikedExercises") val dislikedExercises: String = DEFAULT_STRING,
    @SerializedName("onboardingContext") val onboardingContext: String = DEFAULT_STRING,
    @SerializedName("wizardEquipment") val wizardEquipment: String = DEFAULT_STRING,
    @SerializedName("hasCompletedOnboarding") val hasCompletedOnboarding: Boolean = DEFAULT_BOOL,
    @SerializedName("restTimerSeconds") val restTimerSeconds: Int = DEFAULT_REST_TIMER,
    @SerializedName("dailyChallengesJson") val dailyChallengesJson: String = DEFAULT_STRING,
    @SerializedName("selectedGymPresetId") val selectedGymPresetId: Long = DEFAULT_GYM_PRESET_ID,
    // v4: the three training-relevant prefs a restore used to silently lose.
    @SerializedName("restDaysCsv") val restDaysCsv: String = DEFAULT_STRING,
    @SerializedName("autoRebalanceEnabled") val autoRebalanceEnabled: Boolean = DEFAULT_AUTO_REBALANCE,
    @SerializedName("dayBoundaryHour") val dayBoundaryHour: Int = DEFAULT_DAY_BOUNDARY_HOUR,
    // v5 (rest-UX 2026-07, item 4): manual rest-time mode + the two category times.
    @SerializedName("manualRestEnabled") val manualRestEnabled: Boolean = DEFAULT_BOOL,
    @SerializedName("manualRestHeavySeconds") val manualRestHeavySeconds: Int = DEFAULT_MANUAL_REST_HEAVY,
    @SerializedName("manualRestAccessorySeconds") val manualRestAccessorySeconds: Int = DEFAULT_MANUAL_REST_ACCESSORY,
    // v8 (QoL 2026-08 item 04): exercise-info mismatch flags + re-match overrides
    // (ExerciseInfoCorrections.Codec JSON maps; "" = none). Union-merged on restore.
    @SerializedName("exerciseFlagsJson") val exerciseFlagsJson: String = DEFAULT_STRING,
    @SerializedName("exerciseOverridesJson") val exerciseOverridesJson: String = DEFAULT_STRING
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
    @SerializedName("sessions") val sessions: List<WorkoutSession> = emptyList(),
    @SerializedName("sets") val sets: List<WorkoutSet> = emptyList(),
    @SerializedName("achievements") val achievements: List<Achievement> = emptyList(),
    @SerializedName("user_stats") val userStats: UserStats? = null,
    @SerializedName("body_measurements") val bodyMeasurements: List<BodyMeasurement> = emptyList(),
    @SerializedName("planned_exercises") val plannedExercises: List<PlannedExercise> = emptyList(),
    @SerializedName("exercises") val exercises: List<Exercise> = emptyList(),
    @SerializedName("gym_presets") val gymPresets: List<GymPreset> = emptyList(),
    // E2 (v3): named saved programs. Empty for v1/v2 backups (the migration adds an empty list).
    @SerializedName("programs") val programs: List<Program> = emptyList(),
    // v6 (N5): lift goals. Empty for pre-v6 backups (the migration adds an empty list).
    @SerializedName("goals") val goals: List<LiftGoal> = emptyList(),
    // v6 (N7): per-exercise setup notes. Empty for pre-v6 backups.
    @SerializedName("exercise_notes") val exerciseNotes: List<ExerciseNote> = emptyList(),
    @SerializedName("preferences") val preferences: BackupPreferences = BackupPreferences()
)
