package com.migul.treningsprogram.data

/**
 * Single source of truth for deriving a canonical muscle-group label from an exercise
 * NAME, for cases where a structured muscle group is not otherwise available.
 *
 * Used at set-WRITE time so that sets logged for exercises NOT in
 * [com.migul.treningsprogram.data.db.AppDatabase.DEFAULT_EXERCISES] — e.g. a swapped
 * calisthenics progression variant ("Archer Push-Up", "Pistol Squat") or a custom
 * "Add anyway" exercise — still carry a muscle group. Without this, those sets stored
 * muscleGroup = "" and were silently dropped from muscle-volume stats, the session
 * recap's "muscles hit" section, and muscle-based daily challenges.
 *
 * Returns one of the canonical labels used by DEFAULT_EXERCISES
 * (Chest, Back, Legs, Shoulders, Arms, Core, Cardio) or "" when the name cannot be
 * confidently classified — never a synthetic bucket, so the existing
 * `WHERE muscleGroup != ''` filtering stays meaningful.
 *
 * Keyword ordering mirrors ProgramFragment.getMuscleGroup: Legs is checked before Back
 * so "Romanian Deadlift" / "Stiff-Leg Deadlift" resolve to Legs, not Back.
 */
object MuscleClassifier {
    fun fromName(exerciseName: String): String {
        val lower = exerciseName.lowercase()
        return when {
            lower.containsAny(
                "run", "jog", "sprint", "cardio", "hiit", "bike", "cycling", "treadmill",
                "burpee", "mountain climber", "high knee", "jump rope", "tempo", "interval"
            ) -> "Cardio"
            lower.containsAny("bench", "chest", "fly", "flye", "pec", "push-up", "pushup", "dip") -> "Chest"
            // Legs before Back so "Romanian Deadlift", "Back Squat", "Stiff-Leg Deadlift" resolve correctly
            lower.containsAny(
                "squat", "leg press", "lunge", "calf", "hamstring", "quad", "romanian", "rdl",
                "glute", "hip thrust", "leg curl", "leg extension", "hip hinge", "step up",
                "step-up", "box jump", "split squat", "wall sit", "sumo", "pistol"
            ) -> "Legs"
            lower.containsAny(
                "row", "pulldown", "pull-up", "pullup", "chin-up", "chinup", "lat ",
                "deadlift", "shrug", "back", "scapular", "dead hang"
            ) -> "Back"
            lower.containsAny("shoulder", "overhead", "lateral raise", "face pull", "delt", "military") -> "Shoulders"
            lower.containsAny("curl", "tricep", "bicep", "arm") -> "Arms"
            lower.containsAny(
                "plank", "crunch", "ab ", "abs", "core", "sit-up", "sit up", "russian",
                "leg raise", "dead bug", "l-sit", "dragon flag", "hollow"
            ) -> "Core"
            else -> ""
        }
    }

    /**
     * Human-readable badge label for an exercise name. Same classification as [fromName],
     * but an unclassifiable name shows the generic "Training" label rather than blank —
     * for UI badges only (storage still uses [fromName], which returns "").
     */
    fun displayName(exerciseName: String): String =
        fromName(exerciseName).ifEmpty { "Training" }

    /**
     * Canonical badge/banner color for a muscle [group] (one of the labels [fromName]
     * returns). Unknown / "Training" groups fall back to [fallbackColor], so each screen
     * keeps its own neutral accent for un-grouped exercises. Single source of truth for
     * the 7 group colors, replacing the per-screen copies that had drifted.
     */
    fun colorFor(group: String, fallbackColor: String): String = when (group) {
        "Cardio"    -> "#00BCD4"
        "Chest"     -> "#E91E63"
        "Back"      -> "#2196F3"
        "Legs"      -> "#4CAF50"
        "Shoulders" -> "#9C27B0"
        "Arms"      -> "#FF5722"
        "Core"      -> "#FF9800"
        else        -> fallbackColor
    }

    private fun String.containsAny(vararg keywords: String) = keywords.any { this.contains(it) }
}
