package com.migul.treningsprogram

import com.migul.treningsprogram.domain.WidgetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B10 — the widget's streak/challenge data-selection: what shows for given stats/challenge
 * states, matching the in-app presentation (flame scaling at 7+, x/y challenge summary),
 * and a clean NOTHING for empty states (no zeros, no empty bars).
 */
class B10WidgetStatusTest {

    @Test fun `no streak and no challenges - line disappears entirely`() {
        assertNull(WidgetStatus.line(streak = 0, challengesDone = 0, challengesTotal = 0))
    }

    @Test fun `streak only`() {
        assertEquals("🔥 3-day streak", WidgetStatus.line(3, 0, 0))
    }

    @Test fun `double flame at seven plus - matches the in-app streak presentation`() {
        assertEquals("🔥🔥 7-day streak", WidgetStatus.line(7, 0, 0))
        assertEquals("🔥 6-day streak", WidgetStatus.line(6, 0, 0))
    }

    @Test fun `challenges only - zero streak contributes nothing`() {
        assertEquals("🎯 Challenges 1/3", WidgetStatus.line(0, 1, 3))
        assertEquals("🎯 Challenges 0/3", WidgetStatus.line(0, 0, 3))
    }

    @Test fun `both parts joined compactly`() {
        assertEquals("🔥 5-day streak  ·  🎯 Challenges 2/3", WidgetStatus.line(5, 2, 3))
    }
}
