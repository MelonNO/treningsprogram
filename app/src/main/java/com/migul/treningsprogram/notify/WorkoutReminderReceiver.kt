package com.migul.treningsprogram.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.migul.treningsprogram.MainActivity
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.data.repository.thisMonday
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * F3 — fires once a day at the user's chosen time and posts a reminder IFF:
 * reminders are enabled, today (by the app's 04:00-style logical day) is a
 * scheduled training day, and the session hasn't been fully logged yet.
 * Silent no-op in every other case — including when the notification
 * permission is missing (same graceful degradation as [GenerationNotifier]).
 */
@AndroidEntryPoint
class WorkoutReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var workoutRepository: WorkoutRepository
    @Inject lateinit var prefs: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        if (!prefs.workoutRemindersEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val plan = runCatching {
                    workoutRepository.getPlannedForDay(thisMonday(), currentDayOfWeek()).first()
                }.getOrDefault(emptyList())
                // Rest day or already done → no nagging. AppForegroundState: someone actively
                // using the app doesn't need a status-bar poke either.
                if (plan.isEmpty() || plan.all { it.isLogged } || AppForegroundState.isForeground) return@launch
                postReminder(context, plan.size)
            } finally {
                result.finish()
            }
        }
    }

    private fun postReminder(context: Context, exerciseCount: Int) = runCatching {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Reminds you on scheduled training days" }
            )
        }
        val tap = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mgr.notify(
            NOTIF_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Training day")
                .setContentText(
                    if (exerciseCount == 1) "1 exercise planned today — tap to start."
                    else "$exerciseCount exercises planned today — tap to start."
                )
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    companion object {
        const val CHANNEL_ID = "workout_reminders"
        private const val NOTIF_ID = 5152
    }
}
