package com.migul.treningsprogram.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.migul.treningsprogram.MainActivity
import com.migul.treningsprogram.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P3: posts a system notification when an AI PROGRAM generation reaches a TERMINAL outcome — a
 * successful plan OR a terminal failure (after all attempts) — but ONLY when the app is backgrounded.
 * Covers every program-generation entry point because it is invoked from the two AiRepository methods
 * all of them funnel through (full weekly [generateAdaptedProgram] — used by auto-gen, Settings, the
 * setup wizard, and the P1/P2 rebalances — and the single-day [generateSingleDayProgram]).
 *
 * Graceful degradation ([P3-A2]): if the runtime POST_NOTIFICATIONS permission is not granted, nothing
 * is posted and nothing crashes. Foreground at completion ⇒ nothing posted (the on-screen status is
 * enough). Tapping the notification opens the app to the Program tab.
 */
@Singleton
class GenerationNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "generation_complete"
        private const val NOTIF_ID = 5150
        /** Intent extra read by MainActivity to deep-link the Program tab on tap. */
        const val EXTRA_OPEN_PROGRAM = "open_program_tab"
    }

    fun notifyProgramGenerationComplete(success: Boolean) {
        // Only when backgrounded — a foreground completion already shows its status on screen.
        if (AppForegroundState.isForeground) return
        // Android 13+: no runtime permission ⇒ post nothing (no crash, no nagging).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            ensureChannel()
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(NOTIF_ID, build(success))
        }
    }

    private fun ensureChannel() {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Plan generation",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when an AI workout plan finishes generating"
                    setShowBadge(true)
                }
            )
        }
    }

    private fun build(success: Boolean): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (success) "Your workout plan is ready" else "Plan generation didn't finish")
            .setContentText(
                if (success) "Tap to open it on the Program tab."
                else "Couldn't generate your plan — tap to try again."
            )
            .setContentIntent(tapIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    private fun tapIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_OPEN_PROGRAM, true)
        }
        return PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
