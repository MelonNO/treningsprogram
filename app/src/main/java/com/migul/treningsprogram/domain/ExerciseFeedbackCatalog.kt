package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.ExerciseFeedback

/**
 * Item 05 (2026-08-06) — per-exercise feedback: the reason set the user picks from, and the prompt
 * block that carries it to the program generator.
 *
 * ── What makes this different from the three channels that already exist ──────────────────────
 * The app already had three ways to steer exercise selection, and all three say *"never give me
 * this"*: the global "Exercises to exclude" list, the per-gym avoid list (a HARD post-parse filter,
 * see [GymExclusions]), and the free-text injury box. None of them can say *"I can't do this **yet**
 * — help me get there"*, which is exactly what the user asked for.
 *
 * So feedback is deliberately a **hint and never a filter** (confirmed decision 5c). Nothing here
 * removes an exercise from a plan; this object only produces text for the prompt. The default
 * response to "too hard" is **keep the exercise and program toward it** (confirmed decision 5a-ii)
 * — that instruction is the whole point of the feature and is stated explicitly per entry, because
 * "work up to it" is a genuinely hard thing for a model to honour by implication.
 *
 * Feedback applies to **that exercise only** (5e) and at **every gym** — it is about the user, not
 * the venue — so nothing here reasons about gyms or about similar movements.
 *
 * ── Why reasons are stored as stable keys ─────────────────────────────────────────────────────
 * The user picks a label, but what is persisted is [Reason.key]. Display wording and AI guidance
 * can then be reworded in any later release without rewriting stored rows or breaking old backups.
 * Unknown keys (a row from a newer build restored into an older one) are skipped rather than
 * crashing or being rendered raw into the prompt.
 *
 * Pure and Android-free so the whole block is JVM-unit-testable.
 */
object ExerciseFeedbackCatalog {

    /**
     * One selectable reason.
     *
     * @param key stable identifier persisted in the DB and in backups. Never change these.
     * @param label what the user sees and taps.
     * @param aiGuidance the instruction handed to the generator for this reason. Phrased as an
     *   action the model can actually take, not as a sentiment.
     */
    data class Reason(val key: String, val label: String, val aiGuidance: String)

    const val TOO_HARD = "TOO_HARD"
    const val TOO_EASY = "TOO_EASY"
    const val PAIN = "PAIN"
    const val EQUIPMENT = "EQUIPMENT"
    const val DISLIKE = "DISLIKE"
    const val LOVE = "LOVE"

    /**
     * The selectable reasons, in the order they are offered. Assumption A1: this set was shown to
     * the user and drew no objection, but was never ratified item by item.
     */
    val REASONS: List<Reason> = listOf(
        Reason(
            TOO_HARD, "Too hard — can't do it properly yet",
            "KEEP this exercise in the plan and program TOWARD it — add a regression or a " +
                "supporting movement that builds the missing strength, balance or mobility, and " +
                "progress it over the coming weeks. Do NOT silently drop it."
        ),
        Reason(
            TOO_EASY, "Too easy",
            "Progress this exercise — add load, add reps, or move to a harder variation."
        ),
        Reason(
            PAIN, "Causes pain or discomfort",
            "Prefer a pain-free alternative that trains the same muscle, or adjust the range / " +
                "loading. Never program through pain."
        ),
        Reason(
            EQUIPMENT, "Equipment not available or always busy",
            "Prefer an alternative that trains the same muscle with different equipment."
        ),
        Reason(
            DISLIKE, "Don't enjoy it",
            "Prefer an equivalent alternative for the same muscle — adherence matters more than " +
                "any single exercise choice."
        ),
        Reason(
            LOVE, "Love it — keep it coming",
            "Keep programming this exercise; the user wants to see it."
        ),
    )

    private val BY_KEY: Map<String, Reason> = REASONS.associateBy { it.key }

    fun reasonFor(key: String?): Reason? = key?.let { BY_KEY[it] }

    /** Display label for a stored key; falls back to the raw key so nothing renders blank. */
    fun labelFor(key: String?): String = reasonFor(key)?.label ?: key.orEmpty()

    /**
     * How many entries are carried into the prompt, most recent first.
     *
     * Bounded on purpose: four separate channels now shape exercise selection, and this app has a
     * documented history of generation timeouts and duration-gate trouble driven by prompt size.
     * Feedback also persists until the user removes it (decision D3), so without a cap the block
     * would grow without limit over months of use.
     */
    const val MAX_PROMPT_ENTRIES = 12

    /** Beyond this age the block tells the model the feedback may be out of date (decision D3). */
    const val STALE_AFTER_DAYS = 120

    private const val DAY_MS = 86_400_000L

    /** "today" / "3 days ago" / "…, may be out of date" — decision D3: the AI is told WHEN. */
    internal fun agePhrase(givenAtMs: Long, nowMs: Long): String {
        val days = ((nowMs - givenAtMs).coerceAtLeast(0L) / DAY_MS).toInt()
        val base = when (days) {
            0 -> "given today"
            1 -> "given yesterday"
            else -> "given $days days ago"
        }
        return if (days >= STALE_AFTER_DAYS) {
            "$base — old, the user may well have moved past this, weigh it lightly"
        } else base
    }

    /**
     * The prompt block, or "" when there is no feedback (so a user who has never used the feature
     * sees a byte-identical prompt to before the feature existed).
     *
     * [nowMs] is injected rather than read from the clock so the output is deterministic in tests.
     */
    fun promptBlock(feedback: List<ExerciseFeedback>, nowMs: Long): String {
        val usable = feedback
            .filter { it.exerciseName.isNotBlank() && reasonFor(it.reasonKey) != null }
            .sortedByDescending { it.updatedAtMs }
            .take(MAX_PROMPT_ENTRIES)
        if (usable.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine(
                "USER FEEDBACK ON SPECIFIC EXERCISES — this is a HINT to weigh alongside " +
                    "everything else, NOT a filter. None of these exercises is banned, and " +
                    "feedback applies ONLY to the exact exercise named, not to similar movements:"
            )
            usable.forEach { f ->
                val reason = reasonFor(f.reasonKey)!!
                appendLine("  ${f.exerciseName} — \"${reason.label}\" (${agePhrase(f.updatedAtMs, nowMs)})")
                appendLine("    → ${reason.aiGuidance}")
                val note = f.note.trim()
                if (note.isNotEmpty()) {
                    appendLine("    User's own words: \"$note\" — act on this if it asks for a change or a swap.")
                }
            }
        }.trimEnd()
    }
}
