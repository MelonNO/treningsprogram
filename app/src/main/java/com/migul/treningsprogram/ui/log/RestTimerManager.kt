package com.migul.treningsprogram.ui.log

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestTimerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _totalMs = MutableStateFlow(0L)
    val totalMs: StateFlow<Long> = _totalMs.asStateFlow()

    private var timerJob: Job? = null

    fun start(durationMs: Long) {
        timerJob?.cancel()
        _totalMs.value = durationMs
        _remainingMs.value = durationMs
        _isRunning.value = true
        try {
            context.startForegroundService(Intent(context, RestTimerService::class.java))
        } catch (_: Exception) {}
        timerJob = scope.launch {
            val endTime = System.currentTimeMillis() + durationMs
            while (isActive) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    _remainingMs.value = 0L
                    _isRunning.value = false
                    break
                }
                _remainingMs.value = remaining
                delay(100)
            }
        }
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
        _isRunning.value = false
        // Do NOT zero remainingMs here — setting it to 0 triggers the service's completion
        // handler (vibrate + notification) even on a manual skip. The next start() overwrites it.
        try { context.stopService(Intent(context, RestTimerService::class.java)) } catch (_: Exception) {}
    }
}
