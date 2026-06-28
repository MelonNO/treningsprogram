package com.migul.treningsprogram

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.migul.treningsprogram.data.CrashLog
import com.migul.treningsprogram.data.ExerciseCatalog
import com.migul.treningsprogram.notify.AppForegroundState
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class TreningsprogramApp : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CrashLogEntryPoint {
        fun crashLog(): CrashLog
    }

    override fun onCreate() {
        super.onCreate()
        ExerciseCatalog.initialize(this)
        installCrashHandler()
        registerForegroundTracking()
    }

    // P3: maintain the process-wide foreground flag so GenerationNotifier only posts a
    // generation-complete notification when the app is backgrounded.
    private fun registerForegroundTracking() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = AppForegroundState.onActivityStarted()
            override fun onActivityStopped(activity: Activity) = AppForegroundState.onActivityStopped()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val ep = EntryPointAccessors.fromApplication(
                    applicationContext, CrashLogEntryPoint::class.java
                )
                ep.crashLog().add(thread.name, throwable)
            } catch (_: Exception) {}
            previous?.uncaughtException(thread, throwable)
        }
    }
}
