package com.migul.treningsprogram.domain

import android.content.Context
import android.content.Intent
import com.migul.treningsprogram.notify.GenerationForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Item 05 (generation background survival): app-scoped executor for AI program generation.
 *
 * Every generation used to run inside a UI-owned coroutine scope (`viewModelScope` in the Settings /
 * Setup-wizard / Program view models, the activity `lifecycleScope` for the weekly auto-gen). Those
 * scopes give a running generation NO protection once the user backgrounds the app: with all
 * activities stopped the process drops into the cached bucket, where Android may freeze it (cached
 * apps freezer, Android 12+ — suspends every thread, killing the in-flight SSE read) or kill it
 * outright (LMK / swipe-away from recents). Either way the coroutine dies silently: nothing is
 * saved, no onFailure runs, and no notification fires. Screen-off with the app still foregrounded
 * never demotes the process, which is why that case always worked.
 *
 * This runner fixes both halves:
 *  1. [scope] is process-lifetime (SupervisorJob, Main.immediate — same dispatcher semantics as
 *     `viewModelScope`), so navigating away, VM clearing, or activity destruction can no longer
 *     cancel a generation.
 *  2. [withProtection] holds a short-lived dataSync foreground service
 *     ([GenerationForegroundService], same pattern as RestTimerService) for as long as at least one
 *     generation is in flight, which keeps the process out of the cached bucket while the user is
 *     away. Start/stop are best-effort (`runCatching`): if the OS refuses the service (e.g. a
 *     background start), the generation still runs exactly as it did before this change.
 *
 * Foreground UX is unchanged — callers keep publishing progress through their own state flows and
 * [GenerationState]; this class only decides WHERE the coroutine lives and how the process is kept
 * alive.
 */
@Singleton
class GenerationRunner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Process-lifetime scope for generation work. Main.immediate mirrors viewModelScope so the
     * moved launch blocks keep their exact threading behaviour (state-flow writes on main, IO via
     * the suspending repository/Retrofit calls as before).
     */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val activeProtected = AtomicInteger(0)

    /** True while at least one protected generation section is in flight. */
    val isBusy: Boolean get() = activeProtected.get() > 0

    /**
     * Runs [block] with the foreground service held. Re-entrant across concurrent generations —
     * the service is started by the first section and stopped when the last one finishes.
     */
    suspend fun <T> withProtection(block: suspend () -> T): T {
        if (activeProtected.incrementAndGet() == 1) {
            runCatching {
                context.startForegroundService(Intent(context, GenerationForegroundService::class.java))
            }
        }
        try {
            return block()
        } finally {
            if (activeProtected.decrementAndGet() == 0) {
                runCatching {
                    context.stopService(Intent(context, GenerationForegroundService::class.java))
                }
            }
        }
    }

    /** Launches [block] on the app-scope with the foreground service held for its whole duration. */
    fun launch(block: suspend () -> Unit): Job = scope.launch { withProtection(block) }
}
