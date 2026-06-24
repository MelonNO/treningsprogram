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
 *
 * ── Finer taxonomy (U1) ───────────────────────────────────────────────────────────────
 * [finerMusclesFor] maps an exercise name to one or more fine-grained muscle labels
 * with weighted contributions (0.0–1.0). The fine labels are a superset of the 7 broad
 * groups; each fine label maps back to its broad group via [broadGroupFor].
 *
 * Chosen fine-taxonomy (14 labels):
 *   Chest (1)      — "Chest"
 *   Back (2)       — "Upper Back", "Lower Back"
 *   Shoulders (3)  — "Front Delts", "Side Delts", "Rear Delts"
 *   Arms (2)       — "Biceps", "Triceps"
 *   Legs (4)       — "Quads", "Hamstrings", "Glutes", "Calves"
 *   Core (1)       — "Core"
 *   Cardio (1)     — "Cardio"
 *
 * Rationale: these are the sub-groups a typical training log distinguishes. Back is
 * split because upper-back (rows, pulldowns) and lower-back (deadlifts, hyperextensions)
 * have independent recovery patterns and frequency limits. Shoulders are split at the
 * three-head level because pressing taxes Front Delts far more than Rear/Side (the
 * typical cause of anterior-posterior imbalance). Arms split biceps/triceps because
 * they are opposing muscles trained by distinct exercise patterns. Legs split into the
 * four conventional heads: quad-dominant (squats, leg press), hip-hinge/hamstring
 * (RDLs, curls), glute-dominant (hip thrust, kickback), and calves.
 *
 * ── Per-exercise weighting rationale ─────────────────────────────────────────────────
 * Weights are grounded in EMG literature consensus (Contreras B. "Bodyweight Strength
 * Training Anatomy", 2013; Bret Contreras et al. EMG work on hip-dominant exercises;
 * Lehecka BJ et al. 2017 "Isolated shoulder exercise" J Hum Kinet; Boeckh-Behrens &
 * Buskies 2000, widely cited German EMG reference):
 *   • PRIMARY mover: 1.0 — the muscle the exercise is specifically designed to train.
 *   • MAJOR synergist: 0.6 — substantial activation (>=~50% of primary), integral to
 *     the movement (e.g. triceps in a bench press).
 *   • MINOR synergist/stabiliser: 0.3 — meaningful but secondary contribution (e.g.
 *     rear delts in a row, core in a deadlift).
 *   • NEGLIGIBLE: omitted — <15% activation, not worth tracking for recovery.
 *
 * Three-tier weight set (1.0 / 0.6 / 0.3) is intentionally coarse: fine-grained EMG
 * values vary by load, grip and individual, so a three-tier model is both defensible and
 * more robust than false-precision decimals.
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

    // ── Fine taxonomy (U1) ──────────────────────────────────────────────────────────

    /**
     * All canonical fine-grain muscle labels in display order (head-to-toe,
     * push/pull split within sections). Used by the recovery view to enumerate muscles.
     */
    val ALL_FINE_MUSCLES: List<String> = listOf(
        "Chest",
        "Front Delts", "Side Delts", "Rear Delts",
        "Triceps", "Biceps",
        "Upper Back", "Lower Back",
        "Core",
        "Quads", "Hamstrings", "Glutes", "Calves",
        "Cardio"
    )

    /**
     * Maps a fine-grain label back to its broad group (the 7 labels that [fromName]
     * returns). Used for consistent coloring and broad-group fallback.
     */
    fun broadGroupFor(fineLabel: String): String = when (fineLabel) {
        "Front Delts", "Side Delts", "Rear Delts" -> "Shoulders"
        "Biceps", "Triceps"                        -> "Arms"
        "Upper Back", "Lower Back"                 -> "Back"
        "Quads", "Hamstrings", "Glutes", "Calves"  -> "Legs"
        else                                       -> fineLabel   // Chest, Core, Cardio map to themselves
    }

    /**
     * Returns the fine-grain muscles taxed by [exerciseName], each with a weight:
     *   1.0 = primary mover, 0.6 = major synergist, 0.3 = minor synergist/stabiliser.
     *
     * Never returns an empty list for a classifiable exercise: falls back to the broad
     * group mapped to a single fine label at weight 1.0 if no specific rule matches.
     *
     * Rules are ordered most-specific -> least-specific so "incline bench" beats "bench".
     */
    fun finerMusclesFor(exerciseName: String): List<Pair<String, Float>> {
        val lower = exerciseName.lowercase()
        return when {
            // ── Cardio ──────────────────────────────────────────────────────────────────
            lower.containsAny(
                "run", "jog", "sprint", "cardio", "hiit", "bike", "cycling", "treadmill",
                "burpee", "mountain climber", "high knee", "jump rope", "tempo", "interval"
            ) -> listOf("Cardio" to 1.0f)

            // ── Chest / pressing ─────────────────────────────────────────────────────────
            lower.containsAny("incline bench", "incline press", "incline push") ->
                listOf("Chest" to 1.0f, "Front Delts" to 0.6f, "Triceps" to 0.6f)
            lower.containsAny("decline bench", "decline press", "decline push") ->
                listOf("Chest" to 1.0f, "Triceps" to 0.6f, "Front Delts" to 0.3f)
            lower.containsAny("cable cross", "cable fly", "chest fly", "pec fly",
                               "chest flye", "pec flye") ->
                listOf("Chest" to 1.0f, "Front Delts" to 0.3f)
            lower.containsAny("bench", "chest press", "pec deck") ->
                listOf("Chest" to 1.0f, "Front Delts" to 0.6f, "Triceps" to 0.6f)
            lower.containsAny("push-up", "pushup") ->
                listOf("Chest" to 1.0f, "Triceps" to 0.6f, "Front Delts" to 0.6f, "Core" to 0.3f)
            lower.containsAny("dip") ->
                listOf("Chest" to 1.0f, "Triceps" to 0.6f, "Front Delts" to 0.6f)

            // ── Overhead / shoulder pressing ─────────────────────────────────────────────
            lower.containsAny("overhead press", "ohp", "military press",
                               "shoulder press", "push press") ->
                listOf("Front Delts" to 1.0f, "Side Delts" to 0.6f,
                       "Triceps" to 0.6f, "Upper Back" to 0.3f)
            lower.containsAny("arnold") ->
                listOf("Front Delts" to 1.0f, "Side Delts" to 0.6f,
                       "Rear Delts" to 0.3f, "Triceps" to 0.3f)
            lower.containsAny("lateral raise") ->
                listOf("Side Delts" to 1.0f, "Front Delts" to 0.3f)
            lower.containsAny("front raise") ->
                listOf("Front Delts" to 1.0f, "Side Delts" to 0.3f)
            lower.containsAny("face pull") ->
                listOf("Rear Delts" to 1.0f, "Upper Back" to 0.6f, "Biceps" to 0.3f)
            lower.containsAny("rear delt", "rear fly", "reverse fly",
                               "reverse pec", "bent over fly") ->
                listOf("Rear Delts" to 1.0f, "Upper Back" to 0.6f)
            lower.containsAny("upright row") ->
                listOf("Side Delts" to 1.0f, "Front Delts" to 0.6f, "Biceps" to 0.3f)

            // ── Pull / back ───────────────────────────────────────────────────────────────
            lower.containsAny("pull-up", "pullup", "chin-up", "chinup") ->
                listOf("Upper Back" to 1.0f, "Biceps" to 0.6f, "Rear Delts" to 0.3f)
            lower.containsAny("lat pulldown", "pulldown") ->
                listOf("Upper Back" to 1.0f, "Biceps" to 0.6f, "Rear Delts" to 0.3f)
            lower.containsAny("seated row", "cable row", "machine row") ->
                listOf("Upper Back" to 1.0f, "Biceps" to 0.6f, "Rear Delts" to 0.6f)
            lower.containsAny("bent over row", "barbell row", "dumbbell row",
                               "t-bar row", "chest supported row") ->
                listOf("Upper Back" to 1.0f, "Lower Back" to 0.6f,
                       "Biceps" to 0.6f, "Rear Delts" to 0.3f)
            lower.containsAny("shrug") ->
                listOf("Upper Back" to 1.0f, "Side Delts" to 0.3f)
            lower.containsAny("scapular", "dead hang", "band pull apart") ->
                listOf("Upper Back" to 1.0f, "Rear Delts" to 0.3f)

            // ── Deadlifts ─────────────────────────────────────────────────────────────────
            lower.containsAny("romanian deadlift", "rdl",
                               "stiff-leg deadlift", "straight-leg deadlift") ->
                listOf("Hamstrings" to 1.0f, "Glutes" to 0.6f, "Lower Back" to 0.6f)
            lower.containsAny("sumo deadlift") ->
                listOf("Glutes" to 1.0f, "Quads" to 0.6f, "Hamstrings" to 0.6f,
                       "Lower Back" to 0.6f, "Upper Back" to 0.3f)
            lower.containsAny("deadlift") ->   // conventional — after sumo/RDL
                listOf("Lower Back" to 1.0f, "Glutes" to 0.6f, "Hamstrings" to 0.6f,
                       "Upper Back" to 0.6f, "Quads" to 0.3f)

            // ── Squat patterns ────────────────────────────────────────────────────────────
            lower.containsAny("front squat", "goblet squat", "hack squat", "leg press") ->
                listOf("Quads" to 1.0f, "Glutes" to 0.6f, "Hamstrings" to 0.3f)
            lower.containsAny("squat", "split squat", "lunge", "step up",
                               "step-up", "box jump", "pistol") ->
                listOf("Quads" to 1.0f, "Glutes" to 0.6f,
                       "Hamstrings" to 0.3f, "Core" to 0.3f)
            lower.containsAny("wall sit") ->
                listOf("Quads" to 1.0f, "Glutes" to 0.3f)

            // ── Glute/hip dominant ────────────────────────────────────────────────────────
            lower.containsAny("hip thrust", "glute bridge", "glute kickback", "donkey kick") ->
                listOf("Glutes" to 1.0f, "Hamstrings" to 0.6f, "Lower Back" to 0.3f)

            // ── Hamstrings ────────────────────────────────────────────────────────────────
            lower.containsAny("leg curl", "hamstring curl", "nordic curl", "hamstring") ->
                listOf("Hamstrings" to 1.0f, "Glutes" to 0.3f)

            // ── Quads ──────────────────────────────────────────────────────────────────────
            lower.containsAny("leg extension", "quad") ->
                listOf("Quads" to 1.0f)

            // ── Calves ────────────────────────────────────────────────────────────────────
            lower.containsAny("calf raise", "calf", "seated calf", "standing calf") ->
                listOf("Calves" to 1.0f)

            // ── Hip hinge (generic) ───────────────────────────────────────────────────────
            lower.containsAny("hip hinge", "kettlebell swing", "kb swing") ->
                listOf("Glutes" to 1.0f, "Hamstrings" to 0.6f, "Lower Back" to 0.6f)

            // ── Arms – triceps ────────────────────────────────────────────────────────────
            lower.containsAny("tricep", "skull crusher", "close grip bench",
                               "overhead extension", "cable pushdown", "tricep pushdown") ->
                listOf("Triceps" to 1.0f)

            // ── Arms – biceps ─────────────────────────────────────────────────────────────
            lower.containsAny("curl", "bicep", "hammer curl", "preacher curl",
                               "concentration curl", "spider curl") ->
                listOf("Biceps" to 1.0f, "Front Delts" to 0.3f)

            // ── Core ──────────────────────────────────────────────────────────────────────
            lower.containsAny(
                "plank", "crunch", "ab ", "abs", "core", "sit-up", "sit up", "russian",
                "leg raise", "dead bug", "l-sit", "dragon flag", "hollow"
            ) -> listOf("Core" to 1.0f)

            // ── Fallback: resolve broad group -> single fine label ─────────────────────────
            else -> {
                val broad = fromName(exerciseName)
                if (broad.isEmpty()) emptyList()
                else {
                    // Map broad group to one fine label at 1.0
                    val fineLabel = when (broad) {
                        "Back"      -> "Upper Back"
                        "Legs"      -> "Quads"
                        "Shoulders" -> "Front Delts"
                        "Arms"      -> "Biceps"
                        else        -> broad   // Chest, Core, Cardio
                    }
                    listOf(fineLabel to 1.0f)
                }
            }
        }
    }

    private fun String.containsAny(vararg keywords: String) = keywords.any { this.contains(it) }
}
