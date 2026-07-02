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
import com.migul.treningsprogram.data.db.dao.UserStatsDao
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
 * R2 — streak warning: fires once at the user's evening slot and posts a nudge IFF a real streak
 * (>= 2, per R1's schedule-aware semantics) would end tonight — today is a planned training day
 * whose session isn't logged yet. Rest days are streak-neutral under R1, so an empty plan stays
 * silent. Same graceful degradation as [WorkoutReminderReceiver]: missing permission, foreground
 * app, or any load failure ⇒ silent no-op. Condition logic lives in [NotificationGate].
 */
@AndroidEntryPoint
class StreakWarningReceiver : BroadcastReceiver() {

    @Inject lateinit var workoutRepository: WorkoutRepository
    @Inject lateinit var userStatsDao: UserStatsDao
    @Inject lateinit var prefs: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        if (!prefs.streakWarningEnabled) return
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
                val streak = runCatching { userStatsDao.get()?.currentStreak ?: 0 }.getOrDefault(0)
                if (!NotificationGate.streakWarningShouldFire(
                        enabled = true, // the toggle was checked above, before goAsync
                        plannedExerciseCount = plan.size,
                        allLogged = plan.isNotEmpty() && plan.all { it.isLogged },
                        currentStreak = streak,
                        isForeground = AppForegroundState.isForeground,
                    )
                ) return@launch
                postWarning(context, streak)
            } finally {
                result.finish()
            }
        }
    }

    private fun postWarning(context: Context, streak: Int) = runCatching {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Streak warnings", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Warns you in the evening before a streak would break" }
            )
        }
        val tap = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mgr.notify(
            NOTIF_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("🔥 Streak at risk")
                .setContentText("Your $streak-day streak ends tonight — there's still time.")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    companion object {
        const val CHANNEL_ID = "streak_warnings"
        private const val NOTIF_ID = 5155
    }
}
