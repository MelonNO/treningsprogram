package com.migul.treningsprogram.ui.library

import com.migul.treningsprogram.data.DbExerciseEntry

/**
 * Pure, JVM-testable filter logic for the exercise library browser (E3).
 *
 * Kept free of any Android dependency so it can be unit-tested directly. Operates on
 * the bundled [DbExerciseEntry] catalog. Used by [ExerciseLibraryViewModel].
 */
object ExerciseLibraryFilter {

    /**
     * Returns the distinct, sorted set of muscle groups present across [entries]
     * (primary + secondary), each capitalized for display. Used to populate the
     * muscle-group filter chips/dropdown.
     */
    fun muscleGroups(entries: List<DbExerciseEntry>): List<String> =
        entries
            .flatMap { it.primaryMuscles + it.secondaryMuscles }
            .filter { it.isNotBlank() }
            .map { it.trim().lowercase() }
            .distinct()
            .sorted()
            .map { it.replaceFirstChar(Char::uppercaseChar) }

    /**
     * Returns the distinct, sorted set of equipment values present across [entries],
     * each capitalized for display. Entries with no equipment are skipped.
     */
    fun equipmentOptions(entries: List<DbExerciseEntry>): List<String> =
        entries
            .mapNotNull { it.equipment?.trim()?.takeIf { e -> e.isNotBlank() } }
            .map { it.lowercase() }
            .distinct()
            .sorted()
            .map { it.replaceFirstChar(Char::uppercaseChar) }

    /**
     * Core filter. All criteria are AND-combined; a null/blank criterion is ignored.
     *
     * - [query]: substring match against the exercise name (case-insensitive). Empty/blank
     *   returns all (subject to the other filters).
     * - [muscle]: matches if the (lowercased) value appears in primary OR secondary muscles.
     * - [equipment]: case-insensitive exact match against the entry's equipment.
     *
     * Gracefully handles entries missing fields (empty muscle lists, null equipment, blank name).
     * Results are sorted by name for stable display.
     */
    fun filter(
        entries: List<DbExerciseEntry>,
        query: String? = null,
        muscle: String? = null,
        equipment: String? = null
    ): List<DbExerciseEntry> {
        val q = query?.trim()?.lowercase().orEmpty()
        val m = muscle?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val eq = equipment?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        return entries
            .filter { entry ->
                val nameMatch = q.isEmpty() || entry.name.lowercase().contains(q)
                val muscleMatch = m == null ||
                    entry.primaryMuscles.any { it.equals(m, ignoreCase = true) } ||
                    entry.secondaryMuscles.any { it.equals(m, ignoreCase = true) }
                val equipMatch = eq == null ||
                    (entry.equipment?.equals(eq, ignoreCase = true) == true)
                nameMatch && muscleMatch && equipMatch
            }
            .sortedBy { it.name.lowercase() }
    }
}
