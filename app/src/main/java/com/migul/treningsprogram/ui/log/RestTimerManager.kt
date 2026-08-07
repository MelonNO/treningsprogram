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

    /**
     * Brief 01 (2026-08-07): true only while a rest that was genuinely started in THIS process is
     * still entitled to announce itself. [RestTimerService] will not fire its completion alert
     * unless this is set — see [RestTimerAlertPolicy].
     *
     * Being ordinary singleton state is the whole point: a process kill wipes it, so the
     * `START_STICKY` restart that used to fire "Rest complete!" out of nowhere now finds it false.
     * `stop()` clears it, so a skip or a session ending can never alert either, no matter where the
     * countdown happened to be when it was stopped.
     */
    private val _completionArmed = MutableStateFlow(false)
    val completionArmed: StateFlow<Boolean> = _completionArmed.asStateFlow()

    private var timerJob: Job? = null

    fun start(durationMs: Long) {
        timerJob?.cancel()
        _totalMs.value = durationMs
        _remainingMs.value = durationMs
        _isRunning.value = true
        _completionArmed.value = true
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

    /**
     * Ends the rest immediately and **silently** — the Skip button, and (brief 01) every route by
     * which a workout session ends. There is no audible or haptic variant of stopping; silence is
     * the contract.
     */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
        _isRunning.value = false
        // Disarming is what makes the stop silent, and it does so unconditionally — including in
        // the ~100 ms race where the tick loop has just zeroed remainingMs as the user presses
        // Complete Workout. Before this flag existed, silence rested entirely on NOT zeroing
        // remainingMs below, which left that race open.
        _completionArmed.value = false
        // Still do NOT zero remainingMs: the service treats "no time left" as the completion
        // signal, and the next start() overwrites it anyway.
        try { context.stopService(Intent(context, RestTimerService::class.java)) } catch (_: Exception) {}
    }
}
