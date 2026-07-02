package com.migul.treningsprogram.notify

/**
 * R2 — pure fire-time conditions for every notification type, extracted from the receivers so the
 * decision logic is unit-testable on the JVM. Each receiver gathers the inputs (prefs, plan, stats,
 * foreground flag) and asks the gate; the gate never touches Android.
 *
 * Common rules baked into every gate: a disabled toggle suppresses, and a foregrounded app
 * suppresses (someone actively using the app doesn't need a status-bar poke). The runtime
 * POST_NOTIFICATIONS permission check stays in the receivers (it is Android API, not logic).
 */
object NotificationGate {

    /**
     * F3 training-day reminder: fires only when today has a planned session that isn't fully
     * logged yet. Rest days (empty plan) stay silent.
     */
    fun workoutReminderShouldFire(
        enabled: Boolean,
        plannedExerciseCount: Int,
        allLogged: Boolean,
        isForeground: Boolean
    ): Boolean =
        enabled && plannedExerciseCount > 0 && !allLogged && !isForeground

    /**
     * Streak warning: same day conditions as the training-day reminder, PLUS a real streak at
     * stake — never at 0 or 1 (nothing meaningful would "end tonight"). R1 semantics make rest
     * days streak-neutral, so an empty plan (rest day) correctly suppresses this too.
     */
    fun streakWarningShouldFire(
        enabled: Boolean,
        plannedExerciseCount: Int,
        allLogged: Boolean,
        currentStreak: Int,
        isForeground: Boolean
    ): Boolean =
        enabled && plannedExerciseCount > 0 && !allLogged && currentStreak >= 2 && !isForeground

    /**
     * Weekly weigh-in reminder: suppressed when a weigh-in was already logged on today's logical
     * day — the reminder's whole job is done.
     */
    fun weighInShouldFire(
        enabled: Boolean,
        weighInLoggedToday: Boolean,
        isForeground: Boolean
    ): Boolean =
        enabled && !weighInLoggedToday && !isForeground
}
