package com.migul.treningsprogram.ui.log

/**
 * Brief 01 (2026-08-07) — the rest timer's **lifetime** relative to a workout session, and its
 * **permission to make noise**, both stated once and in pure code so they are hand-checkable in
 * JVM tests (the house convention: the decision is unit-tested, the Android plumbing around it is
 * the user's device check).
 *
 * Two distinct false alerts motivated this file, and they share the completion-alert path:
 *
 *  1. The timer is application-scoped ([RestTimerManager] is a `@Singleton`) and, before this
 *     change, the ONLY caller of [RestTimerManager.stop] in the whole app was the Skip button.
 *     Finishing or abandoning a workout therefore left the countdown running, and it announced
 *     "Rest complete!" — vibrate + chime + heads-up — after the session was over.
 *  2. [RestTimerService] is `START_STICKY`. After a process kill Android restarts it against a
 *     FRESH singleton manager whose `remainingMs` is 0 and `isRunning` is false. The service's old
 *     `wasRunning = true` seed made that first emission look exactly like a completed rest, so the
 *     same alert fired with no rest ever having run.
 */

/**
 * Every way a live workout session can end.
 *
 * This enum is the mandatory declaration point for a session ending: [SessionRestTimerRule] and
 * `LogWorkoutViewModel.endSession` both take one, so a new exit route added later cannot be wired
 * up without naming itself here — and the moment it does, it inherits the timer stop for free.
 * `RestTimerSessionEndTest` iterates every constant, so the rule cannot quietly grow an exception.
 */
enum class SessionEndRoute {
    /** "Complete Workout" with at least one working set — the session is saved. */
    COMPLETED,

    /** "Yes, abandon" — the session is deleted, or restored if it was reopened only to append. */
    ABANDONED,

    /** "Complete Workout" with no working sets logged — the empty session is discarded. */
    DISCARDED_EMPTY,
}

/**
 * The general rule the user asked for, in one place: **a workout session ending ends the rest
 * timer** — silently, immediately, and regardless of how it ended.
 *
 * [route] is deliberately unused. That is the point: the rule is unconditional, so there is no
 * per-route branch for a future exit route to be accidentally left out of. The stop action is held
 * behind a lambda purely so the rule can be exercised in a JVM test without an Android service.
 *
 * Silence is load-bearing and comes from [RestTimerManager.stop], which disarms the completion
 * alert instead of zeroing the countdown — see [RestTimerAlertPolicy].
 */
class SessionRestTimerRule(private val stopTimer: () -> Unit) {
    fun onSessionEnded(@Suppress("UNUSED_PARAMETER") route: SessionEndRoute) {
        stopTimer()
    }
}

/**
 * Gates everything [RestTimerService] does on one emission of the timer state, so that the
 * completion alert can fire **only** when a rest that was genuinely running genuinely reached zero.
 *
 * `completionArmed` is the guard that makes that provable. [RestTimerManager] arms it in `start()`
 * and disarms it in `stop()`, and — being ordinary singleton state — it is `false` in a freshly
 * created process. So a `START_STICKY` restart cannot satisfy the alert condition, and neither can
 * a skip or a session ending, however the countdown happened to be positioned when it was stopped.
 */
object RestTimerAlertPolicy {

    /** What the service should do about a single (remainingMs, isRunning, armed) observation. */
    enum class Action {
        /** A rest is counting down — refresh the ongoing countdown notification. */
        UPDATE_COUNTDOWN,

        /** A genuinely running rest reached zero — vibrate, chime, post "Rest complete!". */
        FIRE_COMPLETION_ALERT,

        /** Nothing is running and nothing was armed: an orphaned service. Take it down. */
        STOP_IDLE_SERVICE,

        /** Stopped early (skip, or the session ended) — leave the shade alone and stay silent. */
        DO_NOTHING,
    }

    fun decide(remainingMs: Long, isRunning: Boolean, completionArmed: Boolean): Action = when {
        // A live countdown. The common case.
        isRunning && remainingMs > 0L -> Action.UPDATE_COUNTDOWN

        // Flagged running but already at zero: the tick loop has not yet flipped isRunning.
        // Waiting one emission costs nothing and keeps the alert tied to the real end of the run.
        isRunning -> Action.DO_NOTHING

        // Not running with time still on the clock: stop() was called — Skip, or the session
        // ended. The user asked for this to be silent, and the countdown is torn down by the
        // service being stopped, not by us posting anything here.
        remainingMs > 0L -> Action.DO_NOTHING

        // Zero, not running, and a rest really was started in this process: the honest completion.
        completionArmed -> Action.FIRE_COMPLETION_ALERT

        // Zero, not running, nothing armed. Only reachable when the process was killed and
        // START_STICKY brought the service back with no rest behind it. Never alert; just leave.
        else -> Action.STOP_IDLE_SERVICE
    }
}
