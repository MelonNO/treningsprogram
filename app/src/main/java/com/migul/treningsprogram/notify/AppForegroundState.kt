package com.migul.treningsprogram.notify

/**
 * P3: process-wide foreground flag. Maintained by an [android.app.Application.ActivityLifecycleCallbacks]
 * registered in TreningsprogramApp (started-activity count). [GenerationNotifier] reads it so a
 * generation-complete notification is posted ONLY when the app is backgrounded — if a generation
 * finishes while the user is looking at the on-screen status, no notification is posted.
 *
 * Kept as a plain object (no DI) so the lifecycle callback and the notifier can both reach it without
 * a dependency cycle. Counter writes are synchronized; the read is volatile-cheap.
 */
object AppForegroundState {
    @Volatile
    private var startedActivities = 0

    /** True while at least one Activity is in the started (visible) state. */
    val isForeground: Boolean get() = startedActivities > 0

    @Synchronized
    fun onActivityStarted() {
        startedActivities++
    }

    @Synchronized
    fun onActivityStopped() {
        if (startedActivities > 0) startedActivities--
    }
}
