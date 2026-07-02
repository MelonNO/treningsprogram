package com.migul.treningsprogram.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.migul.treningsprogram.data.preferences.PreferencesManager
import java.util.Calendar

/**
 * F3/R2 — schedules the app's notification alarms. Inexact repeating (battery
 * friendly; Doze may defer them into a maintenance window, which is fine for
 * reminders). Every receiver re-checks all conditions at fire time, so a stale
 * alarm after a settings change is harmless — but we still cancel eagerly so
 * disabling a type stops it immediately.
 *
 * Three alarms, one per notification type that needs a schedule (the fourth
 * type, program-ready, is event-driven — see [GenerationNotifier]):
 *  - training-day reminder (daily, existing F3 behavior, request code 5151)
 *  - streak warning (daily evening slot, request code 5153)
 *  - weigh-in reminder (weekly weekday+time slot, request code 5154)
 *
 * [sync] is idempotent and safe to call on every app start: alarms don't
 * survive reboots or some OEM force-stops, and re-setting an alarm with the
 * same PendingIntent replaces the old one. MainActivity.onStart and
 * [ReminderBootReceiver] both call it, covering all three types at once.
 */
object ReminderScheduler {

    private const val REQUEST_CODE_WORKOUT = 5151
    private const val REQUEST_CODE_STREAK = 5153
    private const val REQUEST_CODE_WEIGH_IN = 5154

    fun sync(context: Context, prefs: PreferencesManager) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (prefs.workoutRemindersEnabled) {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextTrigger(prefs.reminderHour, prefs.reminderMinute),
                AlarmManager.INTERVAL_DAY,
                workoutIntent(context),
            )
        } else {
            alarmManager.cancel(workoutIntent(context))
        }

        if (prefs.streakWarningEnabled) {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextTrigger(prefs.streakWarningHour, prefs.streakWarningMinute),
                AlarmManager.INTERVAL_DAY,
                streakIntent(context),
            )
        } else {
            alarmManager.cancel(streakIntent(context))
        }

        if (prefs.weighInReminderEnabled) {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextWeeklyTrigger(prefs.weighInDayOfWeek, prefs.weighInHour, prefs.weighInMinute),
                AlarmManager.INTERVAL_DAY * 7,
                weighInIntent(context),
            )
        } else {
            alarmManager.cancel(weighInIntent(context))
        }
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

    /**
     * The next occurrence of [dayOfWeek] (app convention: 1 = Monday … 7 = Sunday) at
     * hour:minute that lies strictly in the future — up to 7 days out when this week's
     * slot has just passed.
     */
    internal fun nextWeeklyTrigger(
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Long =
        Calendar.getInstance().run {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Convert app weekday (1=Mon…7=Sun) to Calendar's (SUNDAY=1…SATURDAY=7).
            val target = if (dayOfWeek == 7) Calendar.SUNDAY else dayOfWeek + 1
            // Walk forward until the weekday matches AND the instant is in the future
            // (a matching weekday whose time already passed walks the full 7 days).
            while (get(Calendar.DAY_OF_WEEK) != target || timeInMillis <= nowMs) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
            timeInMillis
        }

    private fun workoutIntent(context: Context): PendingIntent =
        broadcast(context, REQUEST_CODE_WORKOUT, WorkoutReminderReceiver::class.java)

    private fun streakIntent(context: Context): PendingIntent =
        broadcast(context, REQUEST_CODE_STREAK, StreakWarningReceiver::class.java)

    private fun weighInIntent(context: Context): PendingIntent =
        broadcast(context, REQUEST_CODE_WEIGH_IN, WeighInReminderReceiver::class.java)

    private fun broadcast(context: Context, requestCode: Int, receiver: Class<*>): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, receiver),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
