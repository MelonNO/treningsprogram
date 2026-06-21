package com.migul.treningsprogram.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(context: Context) {

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

    var lastGenerationAttemptCount: Int
        get() = prefs.getInt(KEY_LAST_GEN_ATTEMPTS, 0)
        set(value) { prefs.edit().putInt(KEY_LAST_GEN_ATTEMPTS, value).apply() }

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
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

    // Comma-separated muscle group names, e.g. "Chest,Back,Shoulders"
    var priorityMuscles: String
        get() = prefs.getString(KEY_PRIORITY_MUSCLES, "") ?: ""
        set(value) { prefs.edit().putString(KEY_PRIORITY_MUSCLES, value).apply() }

    var dislikedExercises: String
        get() = prefs.getString(KEY_DISLIKED_EXERCISES, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DISLIKED_EXERCISES, value).apply() }

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
        private const val KEY_LAST_GEN_ATTEMPTS = "last_generation_attempt_count"
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
        private const val KEY_ONBOARDING_CONTEXT = "onboarding_context"
        private const val KEY_WIZARD_EQUIPMENT = "wizard_equipment"
        private const val KEY_INJURIES = "injuries"
        private const val KEY_PRIORITY_MUSCLES = "priority_muscles"
        private const val KEY_DISLIKED_EXERCISES = "disliked_exercises"
    }
}
