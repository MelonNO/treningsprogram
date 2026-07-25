package com.migul.treningsprogram.domain

/**
 * QoL item 04 — PR/baseline marking for the History week-browser, extracted pure so it is
 * unit-testable off-device.
 *
 * RATIFIED PROJECT RULE (mirrors GamificationRepository.isWeightPr via [BeatTarget.isPr]):
 * a PR needs a REAL prior best, strictly beaten — a first-ever lift only establishes the
 * baseline and is NEVER a PR. Warm-ups and zero-weight (bodyweight) sets never earn or
 * carry a PR flag; callers must already have excluded them from the max rows they feed in.
 *
 * Granularity matches the official award in the completion flow
 * (GamificationRepository.detectPersonalRecords): at most ONE PR per exercise per session —
 * the session's heaviest working weight vs the best across all prior sessions.
 */
object HistoryPrFlags {

    /** One exercise's heaviest weighted working set within one session. */
    data class ExerciseSessionMax(
        val exerciseName: String,
        val sessionId: Long,
        val dateMs: Long,
        val maxWeightKg: Float
    )

    /**
     * The derived history context for one (exercise, session):
     * [priorMaxKg] = best working weight across all EARLIER sessions (null = first-ever →
     * baseline), [isPr] = this session's max strictly beat that real prior best.
     */
    data class Flag(val priorMaxKg: Float?, val isPr: Boolean)

    /**
     * Walks each exercise's sessions chronologically (dateMs, sessionId tie-break) and flags
     * the sessions whose max strictly beat the running prior best. Rows may arrive in any order.
     */
    fun flag(rows: List<ExerciseSessionMax>): Map<Pair<String, Long>, Flag> {
        val out = HashMap<Pair<String, Long>, Flag>(rows.size)
        rows.groupBy { it.exerciseName }.forEach { (name, list) ->
            var prior: Float? = null
            list.sortedWith(compareBy({ it.dateMs }, { it.sessionId })).forEach { r ->
                out[name to r.sessionId] = Flag(prior, BeatTarget.isPr(r.maxWeightKg, prior))
                prior = maxOf(prior ?: r.maxWeightKg, r.maxWeightKg)
            }
        }
        return out
    }

    /**
     * Which set of one exercise's sets (within one session) is THE PR set to badge: the first
     * working set that reaches the session max, and only when that max strictly beats
     * [priorMaxKg]. Warm-ups and zero-weight sets are never PR sets. Returns -1 for "none"
     * (including the baseline case: [priorMaxKg] == null).
     */
    fun prSetIndex(weights: FloatArray, warmups: BooleanArray, priorMaxKg: Float?): Int {
        var max = 0f
        for (i in weights.indices) if (!warmups[i] && weights[i] > max) max = weights[i]
        if (max <= 0f || !BeatTarget.isPr(max, priorMaxKg)) return -1
        for (i in weights.indices) if (!warmups[i] && weights[i] == max) return i
        return -1
    }
}
