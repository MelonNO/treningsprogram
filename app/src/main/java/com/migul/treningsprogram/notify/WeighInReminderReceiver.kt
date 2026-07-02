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
import com.migul.treningsprogram.data.db.dao.BodyMeasurementDao
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.domain.DayBoundary
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * R2 — weekly weigh-in reminder: fires at the user's chosen weekday+time and posts a gentle nudge
 * IFF no body-weight entry was logged on today's logical day. Same graceful degradation as the
 * other receivers: missing permission, foreground app, or a load failure ⇒ silent no-op.
 * Condition logic lives in [NotificationGate].
 */
@AndroidEntryPoint
class WeighInReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var bodyMeasurementDao: BodyMeasurementDao
    @Inject lateinit var prefs: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        if (!prefs.weighInReminderEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = DayBoundary.todayEpochDay()
                val loggedToday = runCatching {
                    bodyMeasurementDao.getAllOnce().any { DayBoundary.logicalEpochDay(it.dateMs) == today }
                }.getOrDefault(false)
                if (!NotificationGate.weighInShouldFire(
                        enabled = true, // the toggle was checked above, before goAsync
                        weighInLoggedToday = loggedToday,
                        isForeground = AppForegroundState.isForeground,
                    )
                ) return@launch
                postReminder(context)
            } finally {
                result.finish()
            }
        }
    }

    private fun postReminder(context: Context) = runCatching {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Weigh-in reminders", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "A weekly reminder to log your body weight" }
            )
        }
        val tap = PendingIntent.getActivity(
            context, 4,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mgr.notify(
            NOTIF_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Weigh-in day")
                .setContentText("A quick weigh-in keeps your trend accurate — log it on Home.")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    companion object {
        const val CHANNEL_ID = "weigh_in_reminders"
        private const val NOTIF_ID = 5156
    }
}
