package com.migul.treningsprogram

import android.app.Application
import com.migul.treningsprogram.data.CrashLog
import com.migul.treningsprogram.data.ExerciseCatalog
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
