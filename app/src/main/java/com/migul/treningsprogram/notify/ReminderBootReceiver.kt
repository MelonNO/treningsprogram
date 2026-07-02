package com.migul.treningsprogram.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.migul.treningsprogram.data.preferences.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** F3 — alarms don't survive reboots; re-assert the reminder schedule after boot. */
@AndroidEntryPoint
class ReminderBootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderScheduler.sync(context, prefs)
    }
}
