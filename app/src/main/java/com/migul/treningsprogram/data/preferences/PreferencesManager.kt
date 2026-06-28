package com.migul.treningsprogram.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "treningsprogram_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences("treningsprogram_prefs_fallback", Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) { prefs.edit().putString(KEY_API_KEY, value).apply() }

    var daysPerWeek: Int
        get() = prefs.getInt(KEY_DAYS_PER_WEEK, 4)
        set(value) { prefs.edit().putInt(KEY_DAYS_PER_WEEK, value).apply() }

    var fitnessGoal: String
        get() = prefs.getString(KEY_GOAL, "Hypertrophy") ?: "Hypertrophy"
        set(value) { prefs.edit().putString(KEY_GOAL, value).apply() }

    var experienceLevel: String
        get() = prefs.getString(KEY_EXPERIENCE, "Intermediate") ?: "Intermediate"
        set(value) { prefs.edit().putString(KEY_EXPERIENCE, value).apply() }

    var restTimerSeconds: Int
        get() = prefs.getInt(KEY_REST_TIMER, 90)
        set(value) { prefs.edit().putInt(KEY_REST_TIMER, value).apply() }

    var dailyChallengesJson: String
        get() = prefs.getString(KEY_DAILY_CHALLENGES, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DAILY_CHALLENGES, value).apply() }

    var sessionDurationMinutes: Int
        get() = prefs.getInt(KEY_SESSION_DURATION, 60)
        set(value) { prefs.edit().putInt(KEY_SESSION_DURATION, value).apply() }

    var selectedGymPresetId: Long
        get() = prefs.getLong(KEY_GYM_PRESET, -1L)
        set(value) { prefs.edit().putLong(KEY_GYM_PRESET, value).apply() }

    var lastAutoGenerateWeek: String
        get() = prefs.getString(KEY_LAST_AUTO_GENERATE_WEEK, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_AUTO_GENERATE_WEEK, value).apply() }

    var separateCardioDays: Boolean
        get() = prefs.getBoolean(KEY_SEPARATE_CARDIO_DAYS, false)
        set(value) { prefs.edit().putBoolean(KEY_SEPARATE_CARDIO_DAYS, value).apply() }

    // P1: when ON, changing a day's PRIMARY MUSCLE FOCUS (manually or via single-day regenerate)
    // auto-rebalances the rest of the current week's non-logged days around the locked changed day.
    // Default OFF ⇒ changing a day affects only that day (the pre-P1 behaviour). The P2 "do another
    // day's workout today" rebalance always runs regardless of this toggle.
    var autoRebalanceEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REBALANCE, false)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_REBALANCE, value).apply() }

    // B08: day-selection mode is encoded entirely by this CSV of REST weekday ints (1=Mon … 7=Sun).
    //  - NON-BLANK (e.g. "6,7") ⇒ REST-DAY mode: the user picked these as rest days; training is
    //    planned on the remaining weekdays and days/week is DERIVED (7 − rest days).
    //  - BLANK ("") ⇒ COUNT mode: the user picks a NUMBER of training days ([daysPerWeek]) and the
    //    AI chooses which days are rest (the pre-B08 behaviour).
    //
    // Keying the mode off this one value makes the migration safe with no extra flag: an EXISTING
    // user (who never set rest days) has a blank CSV ⇒ stays in count mode with their saved
    // [daysPerWeek] until they opt in. A new user picks rest days in setup ⇒ non-blank ⇒ rest mode.
    var restDaysCsv: String
        get() = prefs.getString(KEY_REST_DAYS, "") ?: ""
        set(value) { prefs.edit().putString(KEY_REST_DAYS, value).apply() }

    // B1: ISO-week key of the last week an automatic weekly coach summary was generated.
    // Guards the once-per-week trigger (mirrors lastAutoGenerateWeek for plan generation).
    var lastWeeklySummaryWeek: String
        get() = prefs.getString(KEY_LAST_WEEKLY_SUMMARY_WEEK, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_WEEKLY_SUMMARY_WEEK, value).apply() }

    var lastGenerationAttemptCount: Int
        get() = prefs.getInt(KEY_LAST_GEN_ATTEMPTS, 0)
        set(value) { prefs.edit().putInt(KEY_LAST_GEN_ATTEMPTS, value).apply() }

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false) ||
                ((appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
                 java.io.File(appContext.filesDir, ".skip_onboarding").exists())
        set(value) { prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply() }

    var onboardingContext: String
        get() = prefs.getString(KEY_ONBOARDING_CONTEXT, "") ?: ""
        set(value) { prefs.edit().putString(KEY_ONBOARDING_CONTEXT, value).apply() }

    // Comma-separated equipment list saved during wizard (used for auto-generation)
    var wizardEquipment: String
        get() = prefs.getString(KEY_WIZARD_EQUIPMENT, "") ?: ""
        set(value) { prefs.edit().putString(KEY_WIZARD_EQUIPMENT, value).apply() }

    var injuries: String
        get() = prefs.getString(KEY_INJURIES, "") ?: ""
        set(value) { prefs.edit().putString(KEY_INJURIES, value).apply() }

    // One of "Mild" / "Moderate" / "Severe" (empty "" = unspecified/none)
    var injurySeverity: String
        get() = prefs.getString(KEY_INJURY_SEVERITY, "") ?: ""
        set(value) { prefs.edit().putString(KEY_INJURY_SEVERITY, value).apply() }

    // Comma-separated muscle group names, e.g. "Chest,Back,Shoulders"
    var priorityMuscles: String
        get() = prefs.getString(KEY_PRIORITY_MUSCLES, "") ?: ""
        set(value) { prefs.edit().putString(KEY_PRIORITY_MUSCLES, value).apply() }

    var dislikedExercises: String
        get() = prefs.getString(KEY_DISLIKED_EXERCISES, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DISLIKED_EXERCISES, value).apply() }

    var skippedUpdateVersion: String
        get() = prefs.getString(KEY_SKIPPED_UPDATE_VERSION, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SKIPPED_UPDATE_VERSION, value).apply() }

    /**
     * In-progress set-entry drafts for the active workout, as a raw JSON string keyed by
     * session id. Lets the values the user typed into the weight/reps fields (but hasn't
     * logged yet) survive a full process kill, so resuming restores them instead of
     * reverting to AI suggestions. Stored per-session so an old session's draft never
     * bleeds into a new one; cleared when the session completes/abandons.
     */
    var workoutDraftJson: String
        get() = prefs.getString(KEY_WORKOUT_DRAFT, "") ?: ""
        set(value) { prefs.edit().putString(KEY_WORKOUT_DRAFT, value).apply() }

    // Auto-rest-day logging: the epoch-millis the feature first ran on this install. Persisted once
    // (0 = never run). Acts as the floor for the rest/missed backfill window so the first launch after
    // the update does NOT retroactively invent rest/missed days for the period before the feature
    // existed; gaps since the last activity ARE filled on later launches.
    var restDayFeatureFirstRunMs: Long
        get() = prefs.getLong(KEY_REST_DAY_FIRST_RUN, 0L)
        set(value) { prefs.edit().putLong(KEY_REST_DAY_FIRST_RUN, value).apply() }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_KEY = "claude_api_key"
        private const val KEY_DAYS_PER_WEEK = "days_per_week"
        private const val KEY_GOAL = "fitness_goal"
        private const val KEY_EXPERIENCE = "experience_level"
        private const val KEY_REST_TIMER = "rest_timer_seconds"
        private const val KEY_DAILY_CHALLENGES = "daily_challenges_json"
        private const val KEY_SESSION_DURATION = "session_duration_minutes"
        private const val KEY_GYM_PRESET = "selected_gym_preset_id"
        private const val KEY_LAST_AUTO_GENERATE_WEEK = "last_auto_generate_week"
        private const val KEY_SEPARATE_CARDIO_DAYS = "separate_cardio_days"
        private const val KEY_AUTO_REBALANCE = "auto_rebalance_enabled"
        private const val KEY_REST_DAYS = "rest_days_csv"
        private const val KEY_LAST_WEEKLY_SUMMARY_WEEK = "last_weekly_summary_week"
        private const val KEY_LAST_GEN_ATTEMPTS = "last_generation_attempt_count"
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
        private const val KEY_ONBOARDING_CONTEXT = "onboarding_context"
        private const val KEY_WIZARD_EQUIPMENT = "wizard_equipment"
        private const val KEY_INJURIES = "injuries"
        private const val KEY_INJURY_SEVERITY = "injury_severity"
        private const val KEY_PRIORITY_MUSCLES = "priority_muscles"
        private const val KEY_DISLIKED_EXERCISES = "disliked_exercises"
        private const val KEY_SKIPPED_UPDATE_VERSION = "skipped_update_version"
        private const val KEY_WORKOUT_DRAFT = "workout_inprogress_draft"
        private const val KEY_REST_DAY_FIRST_RUN = "rest_day_feature_first_run_ms"
    }
}
