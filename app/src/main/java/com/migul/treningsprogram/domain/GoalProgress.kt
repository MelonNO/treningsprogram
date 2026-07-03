package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.WorkoutSet
import java.util.Locale

/**
 * N5 — pure goal math: progress toward a lift goal, reach detection (A-G2), and the Home
 * nudge decision.
 *
 * A goal targets either a top WORKING-SET weight or an estimated 1RM ([Epley] — the app's
 * single formula). Reach = a logged working set meets/exceeds the target (or the session's
 * best e1RM does, for e1RM goals) — the same strictness style as PR detection; warm-ups
 * NEVER count ([sessionBests] filters them). Goals are absolute weights — no body-weight
 * data is ever needed.
 */
object GoalProgress {

    /** One "normal progression step" — the Home nudge fires within this distance (kg). */
    const val NUDGE_STEP_KG = 2.5f

    /** The session's best working weight and best working-set e1RM (warm-ups excluded). */
    data class SessionBests(val bestWeightKg: Float?, val bestE1rm: Double?)

    fun sessionBests(sets: List<WorkoutSet>): SessionBests {
        val working = sets.filter { !it.isWarmup }
        return SessionBests(
            bestWeightKg = working.maxOfOrNull { it.weightKg },
            bestE1rm = working.maxOfOrNull { Epley.estimate(it.weightKg, it.reps) }
        )
    }

    /** A-G2: did this session's logged work reach the target? */
    fun isReached(isE1rm: Boolean, targetKg: Float, bests: SessionBests): Boolean =
        if (isE1rm) (bests.bestE1rm ?: 0.0) >= targetKg.toDouble()
        else (bests.bestWeightKg ?: 0f) >= targetKg

    /** Progress toward the target, clamped 0..100. Null/zero best = 0. */
    fun progressPercent(currentBest: Float?, targetKg: Float): Int {
        if (targetKg <= 0f) return 0
        val best = currentBest ?: return 0
        return ((best / targetKg) * 100f).toInt().coerceIn(0, 100)
    }

    /** "87% of the way to 100 kg" — the chart-side readout. */
    fun progressLine(currentBest: Float?, targetKg: Float): String =
        "${progressPercent(currentBest, targetKg)}% of the way to ${fmt(targetKg)} kg"

    /**
     * The quiet Home nudge (brief §3): non-null when the goal is within one normal progression
     * step of the current best — the attempt should be planned, not accidental. Already-met
     * targets (goal set at/below an existing best) nudge too: the next qualifying set takes it.
     */
    fun nudgeLine(exerciseName: String, currentBest: Float?, targetKg: Float, isE1rm: Boolean): String? {
        val best = currentBest ?: return null
        val gap = targetKg - best
        if (gap > NUDGE_STEP_KG) return null
        val what = if (isE1rm) "est. 1RM goal" else "goal"
        return if (gap <= 0f) {
            "🎯 ${exerciseName}: your ${fmt(targetKg)} kg $what is within reach — take it this session!"
        } else {
            "🎯 ${exerciseName}: ${fmt(gap)} kg from your ${fmt(targetKg)} kg $what — plan the attempt."
        }
    }

    /** Optional target-date flavor (A-G5): "by October" / "by October 2027". Never a deadline. */
    fun dateFlavor(targetDateMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
        if (targetDateMs <= 0L) return null
        val target = DayBoundary.logicalDate(targetDateMs)
        val now = DayBoundary.logicalDate(nowMs)
        val month = target.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
        return if (target.year == now.year) "by $month" else "by $month ${target.year}"
    }

    private fun fmt(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString()
        else String.format(Locale.US, "%.1f", w)
}
