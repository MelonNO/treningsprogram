package com.migul.treningsprogram.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.migul.treningsprogram.domain.AutoGenSchedule
import java.util.concurrent.TimeUnit

/**
 * Item 06 (facet b): keeps exactly one pending [WeeklyAutoGenWorker] scheduled for the next
 * logical-Monday slot (see [AutoGenSchedule]).
 *
 * Why WorkManager and not the existing AlarmManager precedent: the run must execute an ~up-to-8-min
 * network job with no UI in front. An inexact alarm's BroadcastReceiver gets seconds of guaranteed
 * execution and NO right to start a foreground service from the background on Android 12+ (exact
 * alarms would, but SCHEDULE_EXACT_ALARM is user-revocable and denied by default on 14). A
 * WorkManager job runs with a wakelock and the process out of the cached bucket for up to 10
 * minutes, honours the network constraint, and persists across reboots — no boot receiver, no
 * alarm permission.
 *
 * [ensureScheduled] (app foreground entry) uses KEEP: cheap, idempotent, self-heals a lost chain.
 * [scheduleNext] (from inside the worker) uses APPEND_OR_REPLACE: appending never cancels the
 * currently-running instance of the same unique work (REPLACE would), and a wedged/cancelled chain
 * is replaced outright.
 */
object AutoGenWorkScheduler {

    const val WORK_NAME = "weekly_auto_gen"

    /** Idempotent; called on every app foreground entry. Keeps any already-pending run. */
    fun ensureScheduled(context: Context) =
        enqueue(context, AutoGenSchedule.nextRunDelayMs(), ExistingWorkPolicy.KEEP)

    /** Called by the worker itself to perpetuate the chain (next Monday, or a short retry). */
    fun scheduleNext(context: Context, delayMs: Long) =
        enqueue(context, delayMs, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueue(context: Context, delayMs: Long, policy: ExistingWorkPolicy) {
        // Best-effort: a WorkManager failure must never break app start or the worker itself —
        // the open/resume trigger remains the guaranteed floor.
        runCatching {
            val request = OneTimeWorkRequestBuilder<WeeklyAutoGenWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
