package com.migul.treningsprogram

import com.migul.treningsprogram.domain.ChartAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * UX1 readability pass: guards the pure X-axis date-label selection used by StrengthChartView.
 * Only the label-count / pattern-by-span logic is tested here (rendering is the View's job).
 *
 * Tests are written against the device default Locale (which ChartAxis uses) so they assert the
 * SAME formatter the production code produces, rather than a hard-coded locale-specific string.
 */
class ChartAxisTest {

    private val DAY = 86_400_000L
    private val YEAR = 365L * DAY

    /** Re-derive the expected label the exact way ChartAxis does, for the chosen pattern. */
    private fun expect(ms: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(java.util.Date(ms))

    @Test fun empty_returnsEmpty() {
        assertTrue(ChartAxis.dateLabels(emptyList()).isEmpty())
    }

    @Test fun single_returnsOneLabel() {
        val labels = ChartAxis.dateLabels(listOf(0L))
        assertEquals(1, labels.size)
        assertEquals(expect(0L, "d MMM"), labels[0])
    }

    @Test fun two_returnsFirstAndLast() {
        val a = 0L
        val b = 10 * DAY
        val labels = ChartAxis.dateLabels(listOf(b, a))   // unsorted input
        assertEquals(2, labels.size)
        assertEquals(expect(a, "d MMM"), labels[0])
        assertEquals(expect(b, "d MMM"), labels[1])
    }

    @Test fun threeOrMore_returnsFirstMidLast() {
        val days = listOf(0L, 5 * DAY, 10 * DAY, 20 * DAY, 30 * DAY)
        val labels = ChartAxis.dateLabels(days)
        assertEquals(3, labels.size)
        assertEquals(expect(0L, "d MMM"), labels[0])
        assertEquals(expect(days[days.size / 2], "d MMM"), labels[1])   // median by position
        assertEquals(expect(30 * DAY, "d MMM"), labels[2])
    }

    @Test fun shortSpan_usesDayMonthPattern() {
        val labels = ChartAxis.dateLabels(listOf(0L, 30 * DAY))
        // "d MMM" has no year digits; "MMM yy" would. A short span must use the day-month form.
        assertEquals(expect(0L, "d MMM"), labels[0])
    }

    @Test fun longSpan_usesMonthYearPattern() {
        val labels = ChartAxis.dateLabels(listOf(0L, 2 * YEAR))
        assertEquals(expect(0L, "MMM yy"), labels[0])
        assertEquals(expect(2 * YEAR, "MMM yy"), labels.last())
    }

    @Test fun spanExactlyOneYear_usesMonthYearPattern() {
        // Boundary: span >= one year switches to the year-bearing pattern.
        val labels = ChartAxis.dateLabels(listOf(0L, YEAR))
        assertEquals(expect(0L, "MMM yy"), labels[0])
    }

    @Test fun outputOrderIsChronological() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val days = listOf(20 * DAY, 0L, 10 * DAY)
        val labels = ChartAxis.dateLabels(days)
        // first label corresponds to the earliest timestamp, last to the latest.
        assertEquals(expect(0L, "d MMM"), labels.first())
        assertEquals(expect(20 * DAY, "d MMM"), labels.last())
    }
}
