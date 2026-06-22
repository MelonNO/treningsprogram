package com.migul.treningsprogram.data

data class ProgressionFamily(
    val name: String,
    val exercises: List<String>  // ordered easiest → hardest
)

object CalisthenicsProgressionMap {

    val families = listOf(
        ProgressionFamily("Push-up progression", listOf(
            "Wall Push-Up",
            "Knee Push-Up",
            "Push-ups",
            "Close-Grip Push-Up",
            "Decline Push-Up",
            "Diamond Push-Up",
            "Archer Push-Up",
            "One-Arm Push-Up"
        )),
        ProgressionFamily("Pull-up progression", listOf(
            "Dead Hang",
            "Scapular Pull-Up",
            "Negative Pull-Up",
            "Assisted Pull-Up",
            "Pull-ups",
            "Chin-Up",
            "Weighted Pull-Up",
            "L-Sit Pull-Up"
        )),
        ProgressionFamily("Squat progression", listOf(
            "Assisted Squat",
            "Box Squat",
            "Bodyweight Squat",
            "Bulgarian Split Squat",
            "Pistol Squat Negatives",
            "Pistol Squat"
        )),
        ProgressionFamily("Dip progression", listOf(
            "Bench Dip",
            "Tricep Dip",
            "Dip",
            "Weighted Dip",
            "Ring Dip"
        )),
        ProgressionFamily("Core progression", listOf(
            "Dead Bug",
            "Plank",
            "RKC Plank",
            "L-Sit",
            "Dragon Flag"
        )),
        ProgressionFamily("Row progression", listOf(
            "Incline Row",
            "Australian Pull-Up",
            "Inverted Row",
            "Feet-Elevated Row"
        ))
    )

    /** Find the progression family containing this exercise (exact match first, then pattern match). */
    fun findFamily(exerciseName: String): ProgressionFamily? {
        val lower = exerciseName.lowercase()
        // Exact match first
        val exactMatch = families.firstOrNull { family ->
            family.exercises.any { ex -> ex.lowercase() == lower }
        }
        if (exactMatch != null) return exactMatch
        // Pattern match for exercises whose names weren't found exactly but belong to a family
        return families.firstOrNull { family ->
            when {
                family.name.contains("Push") && (lower.contains("push-up") || lower.contains("push up") || lower.contains("pushup")) -> true
                family.name.contains("Pull") && (lower.contains("pull-up") || lower.contains("pull up") || lower.contains("pullup") || lower.contains("chin-up") || lower.contains("chin up")) -> true
                family.name.contains("Squat") && lower.contains("squat") && !lower.contains("barbell") -> true
                family.name.contains("Dip") && lower.contains("dip") -> true
                family.name.contains("Core") && (lower.contains("plank") || lower.contains("l-sit")) -> true
                else -> false
            }
        }
    }

    fun getEasierOptions(exerciseName: String): List<String> {
        val family = findFamily(exerciseName) ?: return emptyList()
        val idx = family.exercises.indexOfFirst { it.lowercase() == exerciseName.lowercase() }
        if (idx < 0) {
            // Pattern-matched but not in list exactly — return first half of family
            return family.exercises.take(family.exercises.size / 2)
        }
        return if (idx > 0) family.exercises.subList(maxOf(0, idx - 2), idx) else emptyList()
    }

    fun getHarderOptions(exerciseName: String): List<String> {
        val family = findFamily(exerciseName) ?: return emptyList()
        val idx = family.exercises.indexOfFirst { it.lowercase() == exerciseName.lowercase() }
        if (idx < 0) {
            // Pattern-matched but not in list exactly — return second half of family
            return family.exercises.drop(family.exercises.size / 2)
        }
        val end = minOf(family.exercises.size, idx + 3)
        return if (idx >= 0 && idx < family.exercises.size - 1) family.exercises.subList(idx + 1, end) else emptyList()
    }

    fun isCalisthenics(exerciseName: String): Boolean = findFamily(exerciseName) != null

    /** Also detect by name patterns for exercises not explicitly in families. */
    fun looksLikeCalisthenics(exerciseName: String): Boolean {
        val lower = exerciseName.lowercase()
        return isCalisthenics(exerciseName) ||
            lower.contains("push-up") || lower.contains("push up") || lower.contains("pushup") ||
            lower.contains("pull-up") || lower.contains("pull up") || lower.contains("pullup") ||
            lower.contains("chin-up") || lower.contains("chin up") ||
            lower.contains("dip") || lower.contains("plank") ||
            lower.contains("bodyweight") || lower.contains("pistol") ||
            lower.contains("burpee")
    }
}
