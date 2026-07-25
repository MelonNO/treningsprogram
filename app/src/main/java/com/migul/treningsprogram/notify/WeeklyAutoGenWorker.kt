package com.migul.treningsprogram.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.domain.AutoGenSchedule
import com.migul.treningsprogram.domain.WeeklyAutoGenerator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Item 06 (facet b): the unattended Monday-morning generation. WorkManager wakes the process (with
 * a wakelock and the network constraint satisfied), [WeeklyAutoGenerator.runIfDue] re-evaluates the
 * exact same guards as the in-app triggers, and on success the plan is saved and the existing
 * "plan ready" notification fires from inside AiRepository (app is backgrounded, so
 * [GenerationNotifier] posts). Genuine terminal failures post the existing failure notification and
 * count against the same weekly failure cap as launch attempts.
 *
 * Scheduling is self-perpetuating: every run enqueues the next one — the following logical-Monday
 * slot normally, a short retry when the failure was transient and the cap not yet reached
 * (guards make the retry a no-op if the app resolved the week in the meantime).
 *
 * Plain (non-Hilt) worker on purpose: dependencies come from the singleton component via an
 * [EntryPoint], which avoids the androidx.hilt:hilt-work + custom WorkManager Configuration wiring.
 */
class WeeklyAutoGenWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun weeklyAutoGenerator(): WeeklyAutoGenerator
        fun prefs(): PreferencesManager
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        // Constructing PreferencesManager seeds DayBoundary.cutoffHour in a fresh background
        // process, so both the week key inside runIfDue and the next-run computation below see the
        // user's configured day boundary.
        deps.prefs()

        val outcome = runCatching { deps.weeklyAutoGenerator().runIfDue() }
            .getOrDefault(WeeklyAutoGenerator.Outcome.FAILED_TRANSIENT)

        val delayMs = when (outcome) {
            WeeklyAutoGenerator.Outcome.FAILED_TRANSIENT,
            WeeklyAutoGenerator.Outcome.SKIPPED_BUSY -> AutoGenSchedule.RETRY_DELAY_MS
            else -> AutoGenSchedule.nextRunDelayMs()
        }
        AutoGenWorkScheduler.scheduleNext(applicationContext, delayMs)

        // Retry semantics are owned by the failure cap + the explicit reschedule above — never by
        // WorkManager's own retry/backoff (a Result.retry would fight the self-scheduling chain).
        return Result.success()
    }
}
