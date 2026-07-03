package com.migul.treningsprogram.domain

/**
 * N4 — the compact per-lift effort signal fed to program generation.
 *
 * Derives, per exercise, how hard its recent WORKING sets felt from the logged RPE labels
 * ("Easy" / "Moderate" / "Hard" — the exact vocabulary the logging UI writes into
 * WorkoutSet.rpeLabel) and whether that effort is trending harder or easier. Sets without a
 * label contribute nothing; an exercise with no labelled sets in the window produces no line;
 * a history with no labelled sets at all produces NO block — the prompt then stays structurally
 * identical to the pre-N4 prompt (regression guard for old data).
 *
 * The signal is CONTEXT for the model (A-E1): the block's header carries the guidance
 * (moderate progression on grinding lifts, push lifts logged easy); it adds no validator rule,
 * no response-schema change, and no new lookback (A-E2 — callers feed it the same recent
 * sessions the trends block already uses).
 *
 * Pure and Android-free so it is JVM-unit-testable off-API.
 */
object EffortTrend {

    /** One labelled working set: when it happened and what its logged label was. */
    data class LabelledSet(val dateMs: Long, val rpeLabel: String)

    private const val TREND_DELTA = 0.5

    /** "easy" = 1, "moderate" = 2, "hard" = 3; anything else (blank/unknown) = null. */
    internal fun score(label: String): Int? = when (label.trim().lowercase()) {
        "easy" -> 1
        "moderate" -> 2
        "hard" -> 3
        else -> null
    }

    /**
     * One compact line for one exercise, or null when it has no scorable labels.
     * Example: "Bench Press: mostly Hard (5 of 6 labelled sets), trending harder".
     */
    fun line(exercise: String, sets: List<LabelledSet>): String? {
        val scored = sets.mapNotNull { s -> score(s.rpeLabel)?.let { s.dateMs to it } }
            .sortedBy { it.first }
        if (scored.isEmpty()) return null

        // Majority label; ties resolve to the harder read (safer for progression decisions).
        val counts = scored.groupingBy { it.second }.eachCount()
        val majorityScore = counts.entries.maxWithOrNull(
            compareBy({ it.value }, { it.key })
        )!!.key
        val majorityLabel = when (majorityScore) { 1 -> "Easy"; 2 -> "Moderate"; else -> "Hard" }
        val majorityCount = counts[majorityScore] ?: 0

        // Trend: average effort of the newer half vs the older half.
        val trend = if (scored.size < 4) "" else {
            val half = scored.size / 2
            val older = scored.take(half).map { it.second }.average()
            val newer = scored.takeLast(half).map { it.second }.average()
            when {
                newer - older >= TREND_DELTA -> ", trending harder"
                older - newer >= TREND_DELTA -> ", trending easier"
                else -> ", steady"
            }
        }
        return "$exercise: mostly $majorityLabel ($majorityCount of ${scored.size} labelled sets)$trend"
    }

    /**
     * The whole prompt block, or "" when NO exercise has any labelled recent working set —
     * empty means the prompt is emitted without this section at all (no empty header).
     */
    fun promptBlock(perExercise: Map<String, List<LabelledSet>>): String {
        val lines = perExercise.entries
            .sortedByDescending { it.value.size }
            .mapNotNull { (exercise, sets) -> line(exercise, sets) }
            .take(12)
        if (lines.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine(
                "LOGGED EFFORT (how hard recent working sets FELT — context, not a hard rule: " +
                    "moderate the progression on lifts logged mostly Hard/grinding, and push " +
                    "lifts consistently logged Easy a bit more assertively):"
            )
            lines.forEach { appendLine("  $it") }
        }.trimEnd()
    }
}
