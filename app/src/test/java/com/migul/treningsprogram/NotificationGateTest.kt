package com.migul.treningsprogram

import com.migul.treningsprogram.notify.NotificationGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R2 — pure fire-time gates for every notification type (brief acceptance criteria). */
class NotificationGateTest {

    // ── training-day reminder ──────────────────────────────────────────────────────────────────

    @Test fun workoutReminder_firesOnUnloggedTrainingDay() {
        assertTrue(NotificationGate.workoutReminderShouldFire(true, 5, false, false))
    }

    @Test fun workoutReminder_suppressedOnRestDay() {
        assertFalse(NotificationGate.workoutReminderShouldFire(true, 0, false, false))
    }

    @Test fun workoutReminder_suppressedWhenFullyLogged() {
        assertFalse(NotificationGate.workoutReminderShouldFire(true, 5, true, false))
    }

    @Test fun workoutReminder_suppressedInForegroundOrDisabled() {
        assertFalse(NotificationGate.workoutReminderShouldFire(true, 5, false, true))
        assertFalse(NotificationGate.workoutReminderShouldFire(false, 5, false, false))
    }

    // ── streak warning ─────────────────────────────────────────────────────────────────────────

    @Test fun streakWarning_firesWhenRealStreakAtRisk() {
        assertTrue(NotificationGate.streakWarningShouldFire(true, 5, false, 12, false))
        assertTrue(NotificationGate.streakWarningShouldFire(true, 1, false, 2, false))
    }

    @Test fun streakWarning_neverAtStreakZeroOrOne() {
        assertFalse(NotificationGate.streakWarningShouldFire(true, 5, false, 0, false))
        assertFalse(NotificationGate.streakWarningShouldFire(true, 5, false, 1, false))
    }

    @Test fun streakWarning_suppressedOnRestDay() {
        // R1 semantics: an empty plan (rest day) cannot break a streak — no warning.
        assertFalse(NotificationGate.streakWarningShouldFire(true, 0, false, 12, false))
    }

    @Test fun streakWarning_suppressedWhenDoneDisabledOrForeground() {
        assertFalse(NotificationGate.streakWarningShouldFire(true, 5, true, 12, false))
        assertFalse(NotificationGate.streakWarningShouldFire(false, 5, false, 12, false))
        assertFalse(NotificationGate.streakWarningShouldFire(true, 5, false, 12, true))
    }

    // ── weigh-in reminder ──────────────────────────────────────────────────────────────────────

    @Test fun weighIn_firesWhenNotLoggedToday() {
        assertTrue(NotificationGate.weighInShouldFire(true, false, false))
    }

    @Test fun weighIn_suppressedWhenAlreadyLoggedToday() {
        assertFalse(NotificationGate.weighInShouldFire(true, true, false))
    }

    @Test fun weighIn_suppressedWhenDisabledOrForeground() {
        assertFalse(NotificationGate.weighInShouldFire(false, false, false))
        assertFalse(NotificationGate.weighInShouldFire(true, false, true))
    }
}
