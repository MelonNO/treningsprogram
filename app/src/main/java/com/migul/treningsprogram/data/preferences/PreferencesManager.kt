package com.migul.treningsprogram.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.migul.treningsprogram.domain.DayBoundary
import com.migul.treningsprogram.domain.ManualRestTimes

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

    // Item 4 (rest-UX batch 2026-07): manual rest-time mode. OFF (default, incl. every existing
    // install — A1) ⇒ AI mode: the timer uses each exercise's recommendedRestSeconds. ON ⇒ the
    // timer AND the generation duration math use the user's two category times below.
    var manualRestEnabled: Boolean
        get() = prefs.getBoolean(KEY_MANUAL_REST_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_MANUAL_REST_ENABLED, value).apply() }

    var manualRestHeavySeconds: Int
        get() = prefs.getInt(KEY_MANUAL_REST_HEAVY, ManualRestTimes.DEFAULT_HEAVY_SECONDS)
        set(value) {
            prefs.edit().putInt(
                KEY_MANUAL_REST_HEAVY,
                value.coerceIn(ManualRestTimes.MIN_SECONDS, ManualRestTimes.MAX_SECONDS)
            ).apply()
        }

    var manualRestAccessorySeconds: Int
        get() = prefs.getInt(KEY_MANUAL_REST_ACCESSORY, ManualRestTimes.DEFAULT_ACCESSORY_SECONDS)
        set(value) {
            prefs.edit().putInt(
                KEY_MANUAL_REST_ACCESSORY,
                value.coerceIn(ManualRestTimes.MIN_SECONDS, ManualRestTimes.MAX_SECONDS)
            ).apply()
        }

    /** The active manual rest times, or null when AI mode is on — the one switch every consumer keys off. */
    val manualRestTimes: ManualRestTimes?
        get() = if (manualRestEnabled)
            ManualRestTimes(manualRestHeavySeconds, manualRestAccessorySeconds)
        else null

    // Item 5 (rest-UX batch 2026-07): "sessionId|exerciseIndex|startMs" for the per-exercise
    // elapsed readout, persisted so the timer survives backgrounding AND process death. "" = none.
    var exerciseTimerState: String
        get() = prefs.getString(KEY_EXERCISE_TIMER_STATE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_EXERCISE_TIMER_STATE, value).apply() }

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
    // Item 2: default is now ON. The key is written ONLY on an explicit user toggle (App Settings
    // switch → setAutoRebalanceEnabled), so an ABSENT key = "never chosen" → getter returns the new ON
    // default, while a user's explicit choice (ON or OFF) is stored and preserved verbatim — an
    // explicit OFF is never flipped back on. SharedPreferences already distinguishes absent from a
    // stored false, so no sentinel/migration is needed.
    var autoRebalanceEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REBALANCE, true)
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

    // Item 7: the whole-hour day-boundary cutoff (0..6, default 04:00). Anything before this hour
    // counts as the previous logical day everywhere the app derives "which day is it" (logging date,
    // History grouping, streaks, rest/missed detection, today's plan, week-start, PR/trend dates).
    // The getter/setter keep the process-wide [DayBoundary.cutoffHour] holder in sync so the ~80 date
    // derivations (many in file-scope helpers with no prefs access) all consult one shared value.
    var dayBoundaryHour: Int
        get() = prefs.getInt(KEY_DAY_BOUNDARY_HOUR, DayBoundary.DEFAULT_CUTOFF_HOUR)
        set(value) {
            val coerced = value.coerceIn(DayBoundary.MIN_CUTOFF_HOUR, DayBoundary.MAX_CUTOFF_HOUR)
            prefs.edit().putInt(KEY_DAY_BOUNDARY_HOUR, coerced).apply()
            DayBoundary.cutoffHour = coerced
        }

    // Auto-gen retry guard: week key + count of FAILED automatic weekly generations, so a transient
    // failure retries on a later launch instead of writing the week off, but a persistent one stops
    // burning API attempts after a small cap (see MainActivity.checkAndAutoGenerateWeeklyPlan).
    var autoGenFailWeek: String
        get() = prefs.getString(KEY_AUTO_GEN_FAIL_WEEK, "") ?: ""
        set(value) { prefs.edit().putString(KEY_AUTO_GEN_FAIL_WEEK, value).apply() }

    var autoGenFailCount: Int
        get() = prefs.getInt(KEY_AUTO_GEN_FAIL_COUNT, 0)
        set(value) { prefs.edit().putInt(KEY_AUTO_GEN_FAIL_COUNT, value).apply() }

    // F6: the versionCode whose what's-new sheet the user has already seen (0 = never recorded;
    // a fresh install records the current version silently instead of showing old notes).
    var lastSeenWhatsNewVersion: Int
        get() = prefs.getInt(KEY_LAST_SEEN_WHATS_NEW, 0)
        set(value) { prefs.edit().putInt(KEY_LAST_SEEN_WHATS_NEW, value).apply() }

    // F3: workout-reminder notification on scheduled training days (opt-in, default off).
    var workoutRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, value).apply() }

    var reminderHour: Int
        get() = prefs.getInt(KEY_REMINDER_HOUR, 17)
        set(value) { prefs.edit().putInt(KEY_REMINDER_HOUR, value.coerceIn(0, 23)).apply() }

    var reminderMinute: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTE, 0)
        set(value) { prefs.edit().putInt(KEY_REMINDER_MINUTE, value.coerceIn(0, 59)).apply() }

    // R2 (notification center): every notification type individually toggleable. Like the F3
    // reminder prefs above, these are DEVICE-LOCAL (alarms/channels are per-device) and are
    // intentionally NOT part of the backup schema.

    // Streak warning — one evening nudge when today's planned workout isn't logged yet and a
    // real streak (>= 2) would end with the day. Default ON (rare + high value).
    var streakWarningEnabled: Boolean
        get() = prefs.getBoolean(KEY_STREAK_WARNING_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_STREAK_WARNING_ENABLED, value).apply() }

    var streakWarningHour: Int
        get() = prefs.getInt(KEY_STREAK_WARNING_HOUR, 20)
        set(value) { prefs.edit().putInt(KEY_STREAK_WARNING_HOUR, value.coerceIn(0, 23)).apply() }

    var streakWarningMinute: Int
        get() = prefs.getInt(KEY_STREAK_WARNING_MINUTE, 0)
        set(value) { prefs.edit().putInt(KEY_STREAK_WARNING_MINUTE, value.coerceIn(0, 59)).apply() }

    // Weigh-in reminder — weekly, on a user-chosen weekday (1=Mon … 7=Sun, app convention) and
    // time; skipped when a weigh-in was already logged that logical day. Default OFF.
    var weighInReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEIGH_IN_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_WEIGH_IN_ENABLED, value).apply() }

    var weighInDayOfWeek: Int
        get() = prefs.getInt(KEY_WEIGH_IN_DAY, 1)
        set(value) { prefs.edit().putInt(KEY_WEIGH_IN_DAY, value.coerceIn(1, 7)).apply() }

    var weighInHour: Int
        get() = prefs.getInt(KEY_WEIGH_IN_HOUR, 9)
        set(value) { prefs.edit().putInt(KEY_WEIGH_IN_HOUR, value.coerceIn(0, 23)).apply() }

    var weighInMinute: Int
        get() = prefs.getInt(KEY_WEIGH_IN_MINUTE, 0)
        set(value) { prefs.edit().putInt(KEY_WEIGH_IN_MINUTE, value.coerceIn(0, 59)).apply() }

    // Program ready — the P3 generation-complete notification, now toggleable. Default ON
    // (the shipped behavior; the toggle only adds an OFF option).
    var programReadyEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROGRAM_READY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_PROGRAM_READY_ENABLED, value).apply() }

    // R4: the weekStart (epoch ms, Monday) whose Perfect Week bonus has already been awarded —
    // the once-per-week guard for the +150 XP adherence reward. 0 = never awarded.
    var perfectWeekAwardedWeekStart: Long
        get() = prefs.getLong(KEY_PERFECT_WEEK_AWARDED, 0L)
        set(value) { prefs.edit().putLong(KEY_PERFECT_WEEK_AWARDED, value).apply() }

    // B5: rest-day active-recovery card. The permanent off-switch (A-A3, default ON) plus the
    // per-day dismiss (logical epoch-day; the card returns on the next rest day). DEVICE-LOCAL
    // presentation state — intentionally NOT in the backup schema.
    var restDayRecoveryEnabled: Boolean
        get() = prefs.getBoolean(KEY_REST_RECOVERY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_REST_RECOVERY_ENABLED, value).apply() }

    var recoveryCardDismissedEpochDay: Long
        get() = prefs.getLong(KEY_REST_RECOVERY_DISMISSED_DAY, -1L)
        set(value) { prefs.edit().putLong(KEY_REST_RECOVERY_DISMISSED_DAY, value).apply() }

    // B7: "YYYY-M" key of the newest monthly Wrapped the user has viewed/dismissed — drives the
    // once-per-new-month Home ready-card. Presentation state, not backed up.
    var wrappedSeenMonthKey: String
        get() = prefs.getString(KEY_WRAPPED_SEEN_MONTH, "") ?: ""
        set(value) { prefs.edit().putString(KEY_WRAPPED_SEEN_MONTH, value).apply() }

    // Item 03 (training-data 2026-08): normalized (lowercased/trimmed) names of never-performed
    // planned exercises the user chose to KEEP ("dismiss" = leave my plan alone) — suppresses both
    // the Home suggestion AND the generation replace-signal for that exercise until the user actually
    // performs it (a performance breaks the detection streak, at which point the entry is pruned).
    // Newline-delimited (exercise names may legitimately contain commas). Presentation/decision
    // state — intentionally NOT in the backup schema.
    var neverPerformedKeptNames: Set<String>
        get() = (prefs.getString(KEY_NEVER_PERFORMED_KEPT, "") ?: "")
            .split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        set(value) {
            prefs.edit().putString(KEY_NEVER_PERFORMED_KEPT, value.joinToString("\n")).apply()
        }

    // Item 02 (training-data 2026-08): "weekStart:missedDay" keys of missed-day-recovery offers the
    // user accepted or declined — declining is remembered for that week (no repeat prompt for the
    // same miss), while a DIFFERENT uncovered miss in the same week can still surface. Newline-
    // delimited; callers prune stale-week keys when storing. Not backed up.
    var missedRecoveryHandledKeys: Set<String>
        get() = (prefs.getString(KEY_MISSED_RECOVERY_HANDLED, "") ?: "")
            .split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        set(value) {
            prefs.edit().putString(KEY_MISSED_RECOVERY_HANDLED, value.joinToString("\n")).apply()
        }

    init {
        // Seed the process-wide holder from persisted prefs the moment this @Singleton is constructed
        // (early — MainActivity/repositories inject it at startup), so day derivations use the user's
        // saved cutoff rather than the compile-time default.
        DayBoundary.cutoffHour = prefs.getInt(KEY_DAY_BOUNDARY_HOUR, DayBoundary.DEFAULT_CUTOFF_HOUR)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_KEY = "claude_api_key"
        private const val KEY_DAYS_PER_WEEK = "days_per_week"
        private const val KEY_GOAL = "fitness_goal"
        private const val KEY_EXPERIENCE = "experience_level"
        private const val KEY_REST_TIMER = "rest_timer_seconds"
        private const val KEY_MANUAL_REST_ENABLED = "manual_rest_enabled"
        private const val KEY_MANUAL_REST_HEAVY = "manual_rest_heavy_seconds"
        private const val KEY_MANUAL_REST_ACCESSORY = "manual_rest_accessory_seconds"
        private const val KEY_EXERCISE_TIMER_STATE = "exercise_timer_state"
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
        private const val KEY_DAY_BOUNDARY_HOUR = "day_boundary_cutoff_hour"
        private const val KEY_AUTO_GEN_FAIL_WEEK = "auto_gen_fail_week"
        private const val KEY_AUTO_GEN_FAIL_COUNT = "auto_gen_fail_count"
        private const val KEY_LAST_SEEN_WHATS_NEW = "last_seen_whats_new_version"
        private const val KEY_REMINDERS_ENABLED = "workout_reminders_enabled"
        private const val KEY_REMINDER_HOUR = "workout_reminder_hour"
        private const val KEY_REMINDER_MINUTE = "workout_reminder_minute"
        private const val KEY_STREAK_WARNING_ENABLED = "streak_warning_enabled"
        private const val KEY_STREAK_WARNING_HOUR = "streak_warning_hour"
        private const val KEY_STREAK_WARNING_MINUTE = "streak_warning_minute"
        private const val KEY_WEIGH_IN_ENABLED = "weigh_in_reminder_enabled"
        private const val KEY_WEIGH_IN_DAY = "weigh_in_day_of_week"
        private const val KEY_WEIGH_IN_HOUR = "weigh_in_hour"
        private const val KEY_WEIGH_IN_MINUTE = "weigh_in_minute"
        private const val KEY_PROGRAM_READY_ENABLED = "program_ready_enabled"
        private const val KEY_PERFECT_WEEK_AWARDED = "perfect_week_awarded_week_start"
        private const val KEY_REST_RECOVERY_ENABLED = "rest_day_recovery_enabled"
        private const val KEY_REST_RECOVERY_DISMISSED_DAY = "rest_recovery_dismissed_epoch_day"
        private const val KEY_WRAPPED_SEEN_MONTH = "wrapped_seen_month_key"
        private const val KEY_NEVER_PERFORMED_KEPT = "never_performed_kept_names"
        private const val KEY_MISSED_RECOVERY_HANDLED = "missed_recovery_handled_key"
    }
}
