package com.migul.treningsprogram

import com.migul.treningsprogram.domain.WeekDelta
import org.junit.Assert.assertEquals
import org.junit.Test

/** Stats "this week vs last week" pulse — Monday-based logical-week counting. */
class WeekDeltaTest {

    private val MON = 4L          // Monday 1970-01-05 (epoch day 0 = Thursday)
    private val PREV = MON - 7

    @Test fun `counts sets and sessions in the right weeks`() {
        val d = WeekDelta.compute(
            setDayEpochs = listOf(MON, MON, MON + 2, PREV, PREV + 3, PREV + 3),
            trainingDayEpochs = listOf(MON, MON + 2, PREV, PREV + 3),
            todayEpochDay = MON + 4,
        )
        assertEquals(3, d.sets)
        assertEquals(3, d.setsPrev)
        assertEquals(2, d.sessions)
        assertEquals(2, d.sessionsPrev)
    }

    @Test fun `days outside both weeks are ignored`() {
        val d = WeekDelta.compute(
            setDayEpochs = listOf(MON - 20, MON + 20),
            trainingDayEpochs = listOf(MON - 20),
            todayEpochDay = MON,
        )
        assertEquals(0, d.sets)
        assertEquals(0, d.setsPrev)
        assertEquals(0, d.sessions)
    }

    @Test fun `delta labels format sign correctly`() {
        assertEquals("+3", WeekDelta.deltaLabel(5, 2))
        assertEquals("−2", WeekDelta.deltaLabel(2, 4))
        assertEquals("±0", WeekDelta.deltaLabel(4, 4))
    }
}
