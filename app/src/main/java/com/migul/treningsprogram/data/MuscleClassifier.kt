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

    /**
     * 2026-08 fix: the Cardio keyword "run" must only match at a WORD START ("Run",
     * "Outdoor Run", "Running", "Trail Run"), never inside another word — the plain
     * substring matched "c-run-ch", so "Cable Crunch"/"Bicycle Crunch" classified as
     * Cardio (and the time estimator then counted them as 30-minute cardio entries).
     * Shared by [fromName] and [finerMusclesFor] so both stay consistent.
     */
    private val RUN_WORD_START = Regex("\\brun")

    fun fromName(exerciseName: String): String {
        val lower = exerciseName.lowercase()
        // Rules are ordered by SPECIFICITY, not by anatomy: a movement's own keyword must win over
        // incidental setup words that appear in verbose AI-generated names (e.g. "Seated on Bench",
        // "Hand on Bench", "Upper Back on Bench", "Held at Chest", "Chest-Supported"). Those setup
        // phrases used to hijack the broad Chest/Back rules, so a shoulder press "on a bench" or a
        // calf raise "on a bench" was mislabelled Chest. Each specific movement family is therefore
        // resolved BEFORE the generic Chest ("bench"/"chest") catch-all.
        return when {
            // 0. Pure ankle/foot rehab, balance & mobility → un-grouped (intentional "").
            //    No trackable hypertrophy load, so excluded from muscle-volume/recovery stats (same
            //    decision as the v1.10.2 pass). These are still RECOGNISED — ExerciseDbResolver maps
            //    them to real ankle-mobility library entries — so they never show as "unrecognized".
            //    LOADED lower-leg work (calf/tibialis/heel RAISE) is caught by the Legs rule below.
            lower.containsAny(
                "balance hold", "balance reach", "balance drill", "alphabet", "ankle circle",
                "foot circle", "dorsiflexion", "inversion", "eversion", "proprioception",
                "heel-toe", "heel to toe", "heel-to-toe", "ankle tilt", "toe scrunch", "toe-scrunch",
                "scrunch", "toe spread", "toe curl", "4-way isometric", "ankle 4-way"
            ) -> ""
            // 1. Genuine cardio. NOTE: "tempo"/"interval" deliberately REMOVED (they are strength
            //    modifiers; genuine "Interval Run"/"Tempo Run" still match via "run"). "walk" handled
            //    at the END so "walking lunge"/"walking plank" classify by their strength movement first.
            //    "run" is word-start-anchored ([RUN_WORD_START]) so "Cable Crunch" is NOT cardio.
            lower.containsAny(
                "jog", "sprint", "cardio", "hiit", "bike", "cycling", "treadmill",
                "burpee", "mountain climber", "high knee", "jump rope"
            ) || RUN_WORD_START.containsMatchIn(lower) -> "Cardio"
            // 2. Posterior-/rear-delt target moves → Shoulders. MUST precede rows and Chest.
            //    "pull-apart"/"pull apart" (band pull-aparts) are rear-delt work (2026-08 addition).
            lower.containsAny(
                "rear delt", "rear-delt", "reverse fly", "reverse flye", "rear fly", "reverse pec",
                "bent over fly", "bent-over fly", "face pull", "y-raise", " y raise",
                "pull-apart", "pull apart"
            ) -> "Shoulders"
            // 3. Overhead / shoulder pressing + delt raises + upright row → Shoulders (before Chest's
            //    incidental "bench"). Bare "overhead" is NOT here (it also appears in "overhead tricep
            //    extension" → Arms); only explicit shoulder-press phrases. "landmine press" is the
            //    app's overhead-press substitute for shoulder injuries (2026-08 addition).
            lower.containsAny(
                "overhead press", "shoulder press", "arnold", "z-press", "z press", "military",
                "push press", "ohp", "upright row", "lateral raise", "front raise", "landmine press"
            ) -> "Shoulders"
            // 4. Shrug (traps) → Back (before Chest; e.g. "Chest-Supported Shrug").
            lower.contains("shrug") -> "Back"
            // 5. Back / hyper extension → Back (before Legs' "hip hinge" and Chest's "bench").
            lower.containsAny("back extension", "hyperextension", "hyper extension") -> "Back"
            // 6. Any row → Back. MUST precede Chest so a "chest-supported row" is a Back row, not Chest.
            lower.contains("row") -> "Back"
            // 7. Legs (before Chest/Arms). Catches loaded calf/tibialis/heel raise, squats, hinges,
            //    sumo, good morning. + tibialis + good morning + heel raise (loaded).
            //    "pull-through"/"pull through" (cable pull-through) is a glute/hinge movement
            //    (2026-08 addition; must precede the Back rule's "pull-up" family).
            lower.containsAny(
                "squat", "leg press", "lunge", "calf", "tibialis", "hamstring", "quad", "romanian", "rdl",
                "glute", "hip thrust", "leg curl", "leg extension", "hip hinge", "step up",
                "step-up", "box jump", "split squat", "wall sit", "sumo", "pistol",
                "good morning", "heel raise", "pull-through", "pull through"
            ) -> "Legs"
            // 8. Arms (before Chest; e.g. a curl/tricep-extension performed "on a bench"). Bare "arm"
            //    is intentionally NOT a keyword — "Single-Arm Bench Press" must stay Chest.
            lower.containsAny(
                "curl", "tricep", "bicep", "skull crusher", "skullcrusher", "overhead extension"
            ) -> "Arms"
            // 9. Core (before Chest; e.g. Pallof "held at chest", Decline Plank "on bench", carries).
            //    "ab" is word-boundary-guarded (startsWith / " ab ") so it does NOT fire on the very
            //    common "Ankle Rehab"/"Ankle Prehab" qualifier (which ends in "…ab").
            lower.containsAny(
                "plank", "crunch", "abs", "core", "sit-up", "sit up", "russian",
                "leg raise", "dead bug", "l-sit", "dragon flag", "hollow", "pallof", "anti-rotation",
                "anti rotation", "carry"
            ) || lower.startsWith("ab ") || lower.contains(" ab ") -> "Core"
            // 9b. Incline/decline PRESSES → Chest (2026-08 addition). "Incline Barbell Press" /
            //     "Incline Dumbbell Press" / "Decline Dumbbell Press" contain neither "bench" nor
            //     "chest", so they fell through every rule and stored "" (dropped from volume/
            //     recovery stats). Placed immediately BEFORE the generic Chest catch-all so all
            //     earlier specific rules ("shoulder press" → Shoulders, etc.) still win;
            //     "Incline Bench Press" keeps hitting Chest via "bench" either way.
            (lower.contains("incline") || lower.contains("decline")) && lower.contains("press") -> "Chest"
            // 10. Chest (generic pressing/fly catch-all, after all specific movements above).
            lower.containsAny(
                "bench", "chest", "fly", "flye", "pec", "push-up", "pushup", "dip", "squeeze press"
            ) -> "Chest"
            // 11. Back (remaining). "pullover" (straight-arm/dumbbell pullover) is lat-focused
            //     (2026-08 addition).
            lower.containsAny(
                "pulldown", "pull-up", "pullup", "chin-up", "chinup", "lat ",
                "deadlift", "back", "scapular", "dead hang", "pullover"
            ) -> "Back"
            // 12. Shoulders (remaining).
            lower.containsAny("shoulder", "overhead", "delt", "military", "external rotation") -> "Shoulders"
            // 13. Walk → Cardio (low priority; "walking lunge"/"walking plank" already classified above).
            lower.contains("walk") -> "Cardio"
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
        "Cardio"    -> "#9B8CFF"
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
            // ── Cardio ("run" word-start-anchored, same fix as fromName: "Cable Crunch" must
            //    fall through to the Core rule, not match "c-run-ch") ──────────────────────
            lower.containsAny(
                "jog", "sprint", "cardio", "hiit", "bike", "cycling", "treadmill",
                "burpee", "mountain climber", "high knee", "jump rope"
            ) || RUN_WORD_START.containsMatchIn(lower) -> listOf("Cardio" to 1.0f)

            // ── Posterior / rear-delt (MUST precede Chest/pressing so a rear-delt move named
            //    with "incline bench"/"bench"/"fly" is not captured as Chest) ───────────────
            lower.containsAny("face pull") ->
                listOf("Rear Delts" to 1.0f, "Upper Back" to 0.6f, "Biceps" to 0.3f)
            // "pull-apart"/"pull apart" generalised from the old space-only "band pull apart"
            // keyword (which lived in the scapular rule below and missed "Band Pull-Apart").
            // Weights chosen as Rear Delts 1.0 / Upper Back 0.6 — the rear delts are the target
            // of a pull-apart — keeping the finer taxonomy consistent (via broadGroupFor) with
            // fromName's broad classification of pull-aparts as Shoulders.
            lower.containsAny("rear delt", "rear fly", "reverse fly",
                               "reverse pec", "bent over fly", "bent-over fly",
                               "y-raise", " y raise", "pull-apart", "pull apart") ->
                listOf("Rear Delts" to 1.0f, "Upper Back" to 0.6f)

            // ── Chest / pressing ─────────────────────────────────────────────────────────
            // Contains-both match (2026-08 fix): the old containsAny("incline bench",
            // "incline press", …) missed "Incline Barbell Press" / "Incline Dumbbell Press"
            // (a word sits between the qualifier and the movement), sending them to the
            // broad-group fallback which returned empty.
            lower.contains("incline") && lower.containsAny("bench", "press", "push") && !lower.contains("row") ->
                listOf("Chest" to 1.0f, "Front Delts" to 0.6f, "Triceps" to 0.6f)
            lower.contains("decline") && lower.containsAny("bench", "press", "push") && !lower.contains("row") ->
                listOf("Chest" to 1.0f, "Triceps" to 0.6f, "Front Delts" to 0.3f)
            lower.containsAny("cable cross", "cable fly", "chest fly", "pec fly",
                               "chest flye", "pec flye") ->
                listOf("Chest" to 1.0f, "Front Delts" to 0.3f)
            lower.containsAny("bench", "chest press", "pec deck") && !lower.contains("row") ->
                listOf("Chest" to 1.0f, "Front Delts" to 0.6f, "Triceps" to 0.6f)
            lower.containsAny("push-up", "pushup") ->
                listOf("Chest" to 1.0f, "Triceps" to 0.6f, "Front Delts" to 0.6f, "Core" to 0.3f)
            lower.containsAny("dip") ->
                listOf("Chest" to 1.0f, "Triceps" to 0.6f, "Front Delts" to 0.6f)

            // ── Overhead / shoulder pressing ─────────────────────────────────────────────
            // Landmine press (2026-08 addition): the app's overhead-press substitute for
            // shoulder injuries — front-delt primary, at an angle that recruits upper chest.
            lower.containsAny("landmine press") ->
                listOf("Front Delts" to 1.0f, "Chest" to 0.6f, "Triceps" to 0.6f)
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
            lower.containsAny("upright row") ->
                listOf("Side Delts" to 1.0f, "Front Delts" to 0.6f, "Biceps" to 0.3f)

            // ── Pull / back ───────────────────────────────────────────────────────────────
            // Pullover (2026-08 addition): lat-focused straight-arm movement ("Dumbbell
            // Pullover", "Straight-Arm Cable Pullover"); chest/long-head triceps assist.
            lower.containsAny("pullover") ->
                listOf("Upper Back" to 1.0f, "Chest" to 0.3f, "Triceps" to 0.3f)
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
            // NOTE: "band pull apart" moved UP into the rear-delt rule (2026-08) — pull-aparts
            // are rear-delt-primary and now classify broad-Shoulders, not broad-Back.
            lower.containsAny("scapular", "dead hang") ->
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
            // Cable pull-through (2026-08 addition): a glute/hinge movement, matching
            // fromName's broad-Legs classification.
            lower.containsAny("pull-through", "pull through") ->
                listOf("Glutes" to 1.0f, "Hamstrings" to 0.6f, "Lower Back" to 0.6f)
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
