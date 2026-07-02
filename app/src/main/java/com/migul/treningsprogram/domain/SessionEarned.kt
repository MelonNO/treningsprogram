package com.migul.treningsprogram.domain

/**
 * Stage-3 item 14 — attributes achievement unlocks to the workout session that earned them.
 *
 * Unlocks are only ever stamped by GamificationRepository.processWorkoutCompletion, which runs
 * at the completion moment of a specific session — so an unlock's [unlockedAtMs] falls inside
 * that session's completion window (start .. start + duration, plus a small grace for
 * completion-processing latency and duration rounding). Backup restore preserves the ORIGINAL
 * unlock timestamps (merge keeps the earliest) and never re-stamps, so this holds across restores.
 *
 * A-14a: omit-when-unsure — a moment matching zero or MULTIPLE session windows returns null
 * (no attribution) rather than guessing. Never wrong-positive.
 */
object SessionEarned {

    /** Grace period after a session's recorded end (processing latency + minute rounding). */
    const val GRACE_MS = 15 * 60_000L

    /** One completed real session's attribution window inputs. */
    data class Window(val sessionId: Long, val startMs: Long, val durationMinutes: Int)

    /**
     * The single session whose completion window contains [unlockedAtMs], or null when the
     * moment is unstamped (<= 0), matches no window, or ambiguously matches several.
     */
    fun attributeTo(unlockedAtMs: Long, windows: List<Window>): Long? {
        if (unlockedAtMs <= 0L) return null
        val matches = windows.filter { w ->
            val end = w.startMs + w.durationMinutes.coerceAtLeast(0) * 60_000L + GRACE_MS
            unlockedAtMs in w.startMs..end
        }
        return if (matches.size == 1) matches[0].sessionId else null
    }
}
