package com.migul.treningsprogram.domain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Item 02 (per-gym "exercises to avoid"): pure parsing + name-matching for a gym preset's
 * exclusion list, and the deterministic post-parse filter that gives the HARD guarantee — an
 * excluded exercise never appears in a plan saved for that gym, even if the AI ignored the
 * prompt instruction.
 *
 * Matching contract (assumption in the brief: case/punctuation-insensitive, covering the obvious
 * phrasing variants of the SAME movement, never over-excluding different movements):
 *
 * Every name is normalised to tokens (lowercase, punctuation → space, light plural stem), and a
 * candidate matches an exclusion iff BOTH:
 *   1. the exclusion's CORE tokens (tokens minus equipment/position modifier words) all appear in
 *      the candidate's full token set, and
 *   2. the candidate's CORE tokens all appear in the exclusion's full token set.
 * So for the exclusion "Chest-Supported Dumbbell Row" (core: chest supported row):
 *   - "Dumbbell Chest-Supported Row", "Chest Supported Row", and
 *     "Dumbbell Incline Bench Chest Supported Row" all MATCH (same movement, phrasing/setup vary);
 *   - "Dumbbell Row" (core: row — misses chest/supported), "Seal Row" (seal is not in the
 *     exclusion), and "Chest-Supported Curl" (curl ≠ row) all DO NOT match — per the brief,
 *     other prone-on-bench exercises stay allowed.
 */
object GymExclusions {

    /** The pre-filled Home Gym exclusion (brief item 02.3), shared by seed + migration. */
    const val HOME_GYM_DEFAULT_JSON = """["Chest-Supported Dumbbell Row"]"""

    private val gson = Gson()

    /** Words that describe equipment/position/setup rather than the movement itself. */
    private val MODIFIER_WORDS = setOf(
        // equipment
        "dumbbell", "db", "barbell", "bb", "cable", "machine", "smith", "band", "kettlebell",
        "kb", "ez", "bar", "plate", "trap", "landmine",
        // bench/body position + angle
        "incline", "decline", "flat", "bench", "seated", "standing", "lying", "prone",
        "kneeling", "supine",
        // loading/style qualifiers
        "weighted", "assisted", "alternating", "unilateral", "bilateral",
        // filler
        "on", "an", "a", "the", "with", "using", "at", "in", "to",
    )

    /** Parse a preset's avoidExercisesJson (null/blank/malformed ⇒ empty list). */
    fun parse(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        }.getOrElse { emptyList() }
            .filter { it.isNotBlank() }
            .map { it.trim() }
    }

    /** Serialize an avoid list back to the stored JSON shape (empty ⇒ null = column default). */
    fun toJson(avoid: List<String>): String? {
        val cleaned = avoid.map { it.trim() }.filter { it.isNotBlank() }
        return if (cleaned.isEmpty()) null else gson.toJson(cleaned)
    }

    private fun tokens(name: String): List<String> =
        name.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
            .map { stem(it) }

    /** Light plural stem: "rows" → "row", "dumbbells" → "dumbbell"; never touches "press"/"ss". */
    private fun stem(word: String): String =
        if (word.length > 3 && word.endsWith("s") && !word.endsWith("ss")) word.dropLast(1) else word

    private fun core(tokens: List<String>): Set<String> =
        tokens.filterNot { it in MODIFIER_WORDS }.toSet()

    /** True iff [candidate] is (a phrasing variant of) one of [exclusions]. */
    fun matches(candidate: String, exclusions: List<String>): Boolean {
        if (exclusions.isEmpty()) return false
        val candTokens = tokens(candidate)
        if (candTokens.isEmpty()) return false
        val candSet = candTokens.toSet()
        val candCore = core(candTokens)
        return exclusions.any { exclusion ->
            val exclTokens = tokens(exclusion)
            if (exclTokens.isEmpty()) return@any false
            val exclSet = exclTokens.toSet()
            val exclCore = core(exclTokens)
            // A pure-modifier name (e.g. exclusion "Machine") has an empty core — require the
            // full token sets to be equal in that degenerate case rather than matching everything.
            if (exclCore.isEmpty() || candCore.isEmpty()) {
                exclSet == candSet
            } else {
                candSet.containsAll(exclCore) && exclSet.containsAll(candCore)
            }
        }
    }

    /** Deterministic enforcement: drop every planned exercise that matches an exclusion. */
    fun <T> filter(exercises: List<T>, exclusions: List<String>, nameOf: (T) -> String): List<T> =
        if (exclusions.isEmpty()) exercises
        else exercises.filterNot { matches(nameOf(it), exclusions) }

    /** The hard prompt line, distinct from the advisory anti-churn list. "" when nothing to say. */
    fun promptLine(exclusions: List<String>): String =
        if (exclusions.isEmpty()) ""
        else "UNAVAILABLE AT THIS GYM — the equipment cannot support these movements. " +
            "NEVER include them or any variant of the same movement: ${exclusions.joinToString(", ")}"
}
