package com.migul.treningsprogram

import com.migul.treningsprogram.ui.log.RestTimerAlertPolicy
import com.migul.treningsprogram.ui.log.RestTimerAlertPolicy.Action
import com.migul.treningsprogram.ui.log.SessionEndRoute
import com.migul.treningsprogram.ui.log.SessionRestTimerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Brief 01 (2026-08-07) — the rest timer must end when the workout does, and the completion alert
 * must only ever fire for a rest that genuinely ran to zero.
 *
 * Both decisions are pure (see `ui/log/RestTimerPolicy.kt`), so they are hand-checkable here. What
 * these tests deliberately do NOT prove: that the notification actually leaves the shade, that the
 * phone does not vibrate, or that a real `START_STICKY` restart behaves as modelled — those are
 * Android-runtime behaviours and remain the user's device check.
 */
class RestTimerSessionEndTest {

    // ── The general rule: a session ending ends the rest timer ────────────────────────────────

    /**
     * The acceptance criterion the user actually asked for: not "the Finish button stops the
     * timer", but "a session ending stops the timer". Iterating the enum is what makes that a
     * standing guarantee — a new exit route must add a constant here, and it is covered the moment
     * it does.
     */
    @Test fun everySessionEndingStopsTheRestTimer() {
        SessionEndRoute.values().forEach { route ->
            var stops = 0
            SessionRestTimerRule { stops++ }.onSessionEnded(route)
            assertEquals("$route must end the rest timer", 1, stops)
        }
    }

    @Test fun completingAWorkoutWithATimerRunningStopsIt() {
        var stopped = false
        SessionRestTimerRule { stopped = true }.onSessionEnded(SessionEndRoute.COMPLETED)
        assertTrue(stopped)
    }

    @Test fun abandoningAWorkoutWithATimerRunningStopsIt() {
        var stopped = false
        SessionRestTimerRule { stopped = true }.onSessionEnded(SessionEndRoute.ABANDONED)
        assertTrue(stopped)
    }

    @Test fun discardingAnEmptySessionStopsItToo() {
        var stopped = false
        SessionRestTimerRule { stopped = true }.onSessionEnded(SessionEndRoute.DISCARDED_EMPTY)
        assertTrue(stopped)
    }

    /** Three routes today: Complete, Abandon, and Complete-with-nothing-logged. */
    @Test fun theThreeKnownExitRoutesAreAllDeclared() {
        assertEquals(3, SessionEndRoute.values().size)
    }

    // ── The completion alert may only fire for a rest that really ran out ─────────────────────

    /** The feature being protected: a rest that elapses DURING a live workout still announces. */
    @Test fun aRestThatReachesZeroMidSessionStillFiresTheFullAlert() {
        assertEquals(
            Action.FIRE_COMPLETION_ALERT,
            RestTimerAlertPolicy.decide(remainingMs = 0L, isRunning = false, completionArmed = true)
        )
    }

    @Test fun aLiveCountdownJustRefreshesTheNotification() {
        assertEquals(
            Action.UPDATE_COUNTDOWN,
            RestTimerAlertPolicy.decide(remainingMs = 45_000L, isRunning = true, completionArmed = true)
        )
    }

    /**
     * The second false alert. Android kills the process mid-rest; `START_STICKY` brings the service
     * back against a fresh singleton (remainingMs 0, isRunning false, nothing armed). That used to
     * satisfy the old `wasRunning = true` seed and fired vibrate + chime + "Rest complete!" with no
     * rest behind it.
     */
    @Test fun aStickyServiceRestartAfterAProcessKillNeverAlerts() {
        val action = RestTimerAlertPolicy.decide(
            remainingMs = 0L, isRunning = false, completionArmed = false
        )
        assertNotEquals(Action.FIRE_COMPLETION_ALERT, action)
        // ...and it must not strand an undismissable countdown either: the old code only ever
        // reached stopSelf() by way of the false alert.
        assertEquals(Action.STOP_IDLE_SERVICE, action)
    }

    /** Skip: stop() leaves time on the clock and disarms. Silent, exactly as before this change. */
    @Test fun skippingARestStaysSilent() {
        assertEquals(
            Action.DO_NOTHING,
            RestTimerAlertPolicy.decide(remainingMs = 30_000L, isRunning = false, completionArmed = false)
        )
    }

    /** Ending the session mid-rest is the same silent stop as Skip — the user was explicit. */
    @Test fun endingTheSessionMidRestStaysSilent() {
        assertEquals(
            Action.DO_NOTHING,
            RestTimerAlertPolicy.decide(remainingMs = 82_000L, isRunning = false, completionArmed = false)
        )
    }

    /**
     * The race the old code could not win: the tick loop zeroes remainingMs in the same instant the
     * user presses Complete Workout. Silence used to rest entirely on stop() not zeroing the clock,
     * which does nothing when the clock zeroed itself first. Disarming closes it.
     */
    @Test fun endingTheSessionExactlyAsTheRestHitsZeroStillDoesNotAlert() {
        assertNotEquals(
            Action.FIRE_COMPLETION_ALERT,
            RestTimerAlertPolicy.decide(remainingMs = 0L, isRunning = false, completionArmed = false)
        )
    }

    /** Zero on the clock but the loop has not flipped isRunning yet — wait, do not announce. */
    @Test fun zeroWhileStillFlaggedRunningWaitsOneEmission() {
        assertEquals(
            Action.DO_NOTHING,
            RestTimerAlertPolicy.decide(remainingMs = 0L, isRunning = true, completionArmed = true)
        )
    }

    /**
     * The invariant, over the whole input space rather than the cases above: nothing that is not
     * armed can ever make a sound. Arming happens only in `RestTimerManager.start()`.
     */
    @Test fun nothingUnarmedCanEverFireTheAlert() {
        listOf(-5_000L, 0L, 1L, 30_000L).forEach { ms ->
            listOf(true, false).forEach { running ->
                assertNotEquals(
                    "ms=$ms running=$running unarmed must never alert",
                    Action.FIRE_COMPLETION_ALERT,
                    RestTimerAlertPolicy.decide(ms, running, completionArmed = false)
                )
            }
        }
    }

    /** A clock that overshoots past zero is still a completed rest, not a stuck service. */
    @Test fun anOvershotClockIsTreatedAsCompletion() {
        assertEquals(
            Action.FIRE_COMPLETION_ALERT,
            RestTimerAlertPolicy.decide(remainingMs = -120L, isRunning = false, completionArmed = true)
        )
    }
}
