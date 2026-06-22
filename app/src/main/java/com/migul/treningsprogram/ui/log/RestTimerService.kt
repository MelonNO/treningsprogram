package com.migul.treningsprogram.ui.log

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.migul.treningsprogram.MainActivity
import com.migul.treningsprogram.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class RestTimerService : Service() {

    @Inject lateinit var timerManager: RestTimerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // "rest_timer_v2": channel renamed so users upgrading from IMPORTANCE_LOW get a fresh channel
    private val CHANNEL_ID = "rest_timer_v2"
    private val CHANNEL_DONE_ID = "rest_timer_done"
    private val NOTIF_ID = 4242
    private val NOTIF_DONE_ID = 4243

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val initial = buildTimerNotification(0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, initial)
        }
        scope.launch {
            var wasRunning = true
            timerManager.remainingMs.collect { ms ->
                val running = timerManager.isRunning.value
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (ms <= 0L && !running && wasRunning) {
                    wasRunning = false
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                    vibrate()
                    playCompletionSound()
                    mgr.notify(NOTIF_DONE_ID, buildDoneNotification())
                    delay(2500)
                    mgr.cancel(NOTIF_DONE_ID)
                    stopSelf()
                } else if (ms > 0L) {
                    mgr.notify(NOTIF_ID, buildTimerNotification(ms))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val notifMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifMgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Rest Timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Rest timer countdown"
                setShowBadge(false)
                setSound(null, null)  // silent — no sound on each second tick
            }
        )
        notifMgr.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE_ID, "Rest Complete", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rest timer completion alert"
                setShowBadge(false)
                enableVibration(false)
            }
        )
    }

    private fun tapIntent() = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildTimerNotification(ms: Long): Notification {
        val secs = (ms / 1000).toInt()
        val title = if (ms <= 0L) "Starting…" else "%d:%02d".format(secs / 60, secs % 60)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Rest timer — tap to view")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(tapIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + ms)
            .build()
    }

    private fun buildDoneNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_DONE_ID)
            .setContentTitle("Rest complete!")
            .setContentText("Time to work!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(tapIntent())
            .setAutoCancel(true)
            .build()
    }

    private fun playCompletionSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, uri)?.play()
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
                .vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}
