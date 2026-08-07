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
    // QoL item 08: "rest_timer_done_v2" — the done channel is now SILENT (sound comes from our
    // own Ringtone with explicit notification attributes, see playCompletionSound). Channels are
    // immutable after creation, so silencing required a rename; the old channel is deleted below.
    private val CHANNEL_DONE_ID = "rest_timer_done_v2"
    private val OLD_CHANNEL_DONE_ID = "rest_timer_done"
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
            // Brief 01 (2026-08-07): the alert decision moved into the pure [RestTimerAlertPolicy]
            // so it is unit-testable. It replaces the old `var wasRunning = true` seed, which was
            // the bug: on a START_STICKY restart after a process kill, onCreate ran against a FRESH
            // singleton manager (remainingMs = 0, isRunning = false) and that seed made the very
            // first emission look like a completed rest — vibrate, chime and "Rest complete!" with
            // no rest ever having run.
            var alertFired = false
            timerManager.remainingMs.collect { ms ->
                val running = timerManager.isRunning.value
                if (running) alertFired = false   // a fresh rest re-arms this instance
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val armed = timerManager.completionArmed.value && !alertFired
                when (RestTimerAlertPolicy.decide(ms, running, armed)) {
                    RestTimerAlertPolicy.Action.UPDATE_COUNTDOWN ->
                        mgr.notify(NOTIF_ID, buildTimerNotification(ms))

                    RestTimerAlertPolicy.Action.FIRE_COMPLETION_ALERT -> {
                        alertFired = true
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                        vibrate()
                        playCompletionSound()
                        mgr.notify(NOTIF_DONE_ID, buildDoneNotification())
                        delay(2500)
                        mgr.cancel(NOTIF_DONE_ID)
                        stopSelf()
                    }

                    // Nothing running and nothing armed — an orphaned service. Suppressing the
                    // false alert is only half the fix: the old code reached stopSelf() THROUGH
                    // that alert, so simply staying quiet would strand an undismissable "Starting…"
                    // countdown in the shade forever. Take the service down instead.
                    RestTimerAlertPolicy.Action.STOP_IDLE_SERVICE -> {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                        stopSelf()
                    }

                    // Stopped early (Skip, or the session ended). Silent by contract: no alert, and
                    // no countdown refresh either — stopService() tears the notification down.
                    RestTimerAlertPolicy.Action.DO_NOTHING -> Unit
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
                // QoL item 08: the channel itself is silent. Completion used to fire TWO sounds
                // at once (this channel's default sound through the system notification path +
                // an explicitly played default ringtone), which muted music playing in other
                // apps. The single chime now comes only from playCompletionSound(), which never
                // requests audio focus — the music keeps playing underneath.
                setSound(null, null)
            }
        )
        // Remove the pre-QoL-08 done channel so upgrading installs lose its default sound.
        notifMgr.deleteNotificationChannel(OLD_CHANNEL_DONE_ID)
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

    /**
     * QoL item 08: the ONE completion chime. Played directly (the done channel is silent) with
     * explicit notification-stream attributes and deliberately NO audio-focus request, so music
     * playing in another app keeps playing audibly underneath — at most the OS may briefly duck
     * it (accepted A6), never pause or mute it.
     */
    private fun playCompletionSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, uri)?.apply {
                audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
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
