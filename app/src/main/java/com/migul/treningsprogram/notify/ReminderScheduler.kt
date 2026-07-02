package com.migul.treningsprogram.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.migul.treningsprogram.data.preferences.PreferencesManager
import java.util.Calendar

/**
 * F3 — schedules the daily workout-reminder alarm. Inexact repeating (battery
 * friendly; Doze may defer it into a maintenance window, which is fine for a
 * reminder). The receiver re-checks all conditions at fire time, so a stale
 * alarm after a settings change is harmless — but we still cancel eagerly.
 *
 * [sync] is idempotent and safe to call on every app start: alarms don't
 * survive reboots or some OEM force-stops, and re-setting an alarm with the
 * same PendingIntent replaces the old one.
 */
object ReminderScheduler {

    private const val REQUEST_CODE = 5151

    fun sync(context: Context, prefs: PreferencesManager) {
        if (prefs.workoutRemindersEnabled) {
            schedule(context, prefs.reminderHour, prefs.reminderMinute)
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextTrigger(hour, minute),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    /** Today at hour:minute if still ahead, otherwise tomorrow. */
    internal fun nextTrigger(hour: Int, minute: Int, nowMs: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().run {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMs) add(Calendar.DAY_OF_MONTH, 1)
            timeInMillis
        }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, WorkoutReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
