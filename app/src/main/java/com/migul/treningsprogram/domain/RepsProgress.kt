package com.migul.treningsprogram.domain

/**
 * Stage-3 item 1 — reps progression for bodyweight exercises on the Progress tab.
 *
 * The existing strength history (max WEIGHT per session) deliberately excludes 0-kg sets, so a
 * pull-up trained at body weight was invisible. This helper derives the complementary series from
 * per-session working-set summaries that INCLUDE bodyweight sets.
 */
object RepsProgress {

    /**
     * One completed session's working-set summary for an exercise:
     * [maxWeightKg] = heaviest working-set load (0 = the whole session was at body weight),
     * [bestReps] = best working-set reps in the session regardless of load (A-01a).
     */
    data class Point(val dateMs: Long, val maxWeightKg: Float, val bestReps: Int)

    /**
     * A-01b: an exercise is treated as bodyweight when its logged history contains at least one
     * completed session performed entirely at body weight (session max weight 0). No hand
     * -maintained list; any exercise the user performs unloaded qualifies. The reps chart shows
     * exactly for these exercises; for an exercise with mixed history the weight chart appears in
     * addition (its visibility is driven by the existing weighted-only strength history).
     */
    fun showRepsChart(history: List<Point>): Boolean = history.any { it.maxWeightKg <= 0f }
}
