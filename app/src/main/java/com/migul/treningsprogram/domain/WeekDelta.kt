package com.migul.treningsprogram.domain

/**
 * Stats-tab "this week vs last week" summary. Pure math over logical epoch-days
 * (Monday-based weeks via [RecapGraphs.mondayOfEpochDay]) so it is unit-testable
 * and consistent with the heatmap/overview grids.
 */
object WeekDelta {

    data class Delta(
        val sets: Int, val setsPrev: Int,
        val sessions: Int, val sessionsPrev: Int,
    )

    /**
     * @param setDayEpochs      one logical epoch-day per completed working set (dupes expected)
     * @param trainingDayEpochs distinct logical epoch-days with a completed session
     * @param todayEpochDay     the current logical epoch-day
     */
    fun compute(setDayEpochs: List<Long>, trainingDayEpochs: List<Long>, todayEpochDay: Long): Delta {
        val monday = RecapGraphs.mondayOfEpochDay(todayEpochDay)
        val prevMonday = monday - 7
        fun countIn(days: List<Long>, start: Long) = days.count { it in start until start + 7 }
        return Delta(
            sets = countIn(setDayEpochs, monday),
            setsPrev = countIn(setDayEpochs, prevMonday),
            sessions = countIn(trainingDayEpochs.distinct(), monday),
            sessionsPrev = countIn(trainingDayEpochs.distinct(), prevMonday),
        )
    }

    /** "+3", "−2" or "±0" vs last week — rendered next to the current number. */
    fun deltaLabel(current: Int, previous: Int): String = when {
        current > previous -> "+${current - previous}"
        current < previous -> "−${previous - current}"
        else -> "±0"
    }
}
