package com.migul.treningsprogram.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.migul.treningsprogram.MainActivity
import com.migul.treningsprogram.R

/**
 * Item 05: a thin foreground-service shell held by
 * [com.migul.treningsprogram.domain.GenerationRunner] while an AI generation is in flight, so the
 * process is not cached/frozen/killed when the user backgrounds the app mid-generation. It runs no
 * work itself — the generation coroutine lives in the runner's app scope; this service only pins
 * the process's importance (same dataSync pattern as [com.migul.treningsprogram.ui.log.RestTimerService]).
 *
 * START_NOT_STICKY: if the system ever kills the service, the generation died with the process —
 * resurrecting an empty service would only dangle a meaningless notification.
 *
 * startForeground is called from BOTH onCreate and onStartCommand so the
 * "startForegroundService() did not then call startForeground()" window is as small as possible
 * even when a very fast generation stops the service almost immediately.
 */
class GenerationForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        enterForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground()
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        ensureChannel()
        val notification = build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Plan generation progress",
                    NotificationManager.IMPORTANCE_LOW   // silent, no heads-up — purely informational
                ).apply {
                    description = "Shown while an AI workout plan is being generated"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun build(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Generating your workout plan…")
            .setContentText("You can leave the app — it will finish on its own.")
            .setOngoing(true)
            .setContentIntent(tapIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun tapIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val CHANNEL_ID = "generation_progress"
        /** 5150–5156 are taken by the other notifiers/receivers; RestTimer uses 4242. */
        private const val NOTIF_ID = 5157
    }
}
