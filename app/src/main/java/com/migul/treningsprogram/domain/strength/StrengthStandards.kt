package com.migul.treningsprogram.domain.strength

import com.migul.treningsprogram.data.MuscleClassifier

/**
 * Brief 02 (2026-08-07) — the population strength standards the level system is rated against, and
 * the catalogue of lifts that are allowed to move a rating.
 *
 * ## Why a shipped table (assumption A2)
 * Ratings must compute in aeroplane mode, so the standards are a static offline table. No network,
 * no API, no AI call.
 *
 * ## What the numbers are
 * Each entry is an **estimated 1RM expressed as a multiple of body weight**, at the five named
 * tiers, split by sex. They are the widely-published approximate strength standards for adult
 * lifters and are deliberately **rounded to two decimals** rather than carried at false precision:
 * the underlying population data varies by source, and the app then shows the result as a tier
 * name, not a number. Age is not a factor — the user chose to ignore it.
 *
 * Tier boundaries are a product surface, not an internal detail: once a user sits one kilo below
 * "Advanced", the exact number becomes very visible. That is why they are round, and why
 * `StrengthRating` reports what would cross them in the user's own kilos and reps.
 *
 * ## Which lifts qualify, and why the list is short
 * The user's rule was "only lifts with a real population standard count", because 40 kg on one
 * machine is not 40 kg on another. Applied honestly that excludes more than machines:
 *
 *  - **Machines, cables and bands** — no comparable population. Excluded by [DISQUALIFYING].
 *  - **Assisted variants** — an assisted pull-up is not a pull-up. Excluded.
 *  - **Dumbbells** — excluded deliberately, and not because standards are unavailable. A logged
 *    "30 kg" dumbbell press is ambiguous between per-dumbbell and total, and guessing wrong is a
 *    2× error in a rating the user is asked to trust. Barbell and body-weight loads carry no such
 *    ambiguity. This also matches the user's own words: "the classic barbell and bodyweight
 *    movements".
 *  - **Push-ups** — genuinely have population standards, but with no way to add load and Epley
 *    capped at [MAX_QUALIFYING_REPS], every possible push-up set lands between 0.64x and 0.90x body
 *    weight. Five tiers do not fit honestly in that band, so push-ups log and display as they
 *    always have but do not rate. Pull-ups, chin-ups and dips carry the body-weight route instead —
 *    they take added load, so they scale across the whole ladder.
 *
 * Everything excluded here still logs, still shows in history, stats and progress, and still earns
 * XP. It just never moves a rating.
 *
 * ## Group agreement
 * Each lift declares the broad group it rates. `StrengthStandardsTest` asserts that every one of
 * them matches what [MuscleClassifier.fromName] already returns, so the ratings speak the same
 * muscle-group language as the rest of the app (assumption A6) rather than inventing a second
 * taxonomy.
 */
object StrengthStandards {

    /** The six rated groups, in display order. Cardio is deliberately absent — it gets no level. */
    val RATED_GROUPS: List<String> =
        listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Core")

    /**
     * How much each group counts toward the total. The user asked for bigger groups to weigh more.
     * Unrated groups are dropped and the remainder renormalised, so an unrated group is *absent*
     * from the total rather than a zero dragging it down.
     */
    val GROUP_WEIGHTS: Map<String, Float> = mapOf(
        "Legs" to 3f, "Back" to 3f, "Chest" to 2f, "Shoulders" to 2f, "Arms" to 1f, "Core" to 1f
    )

    /**
     * Epley is exact at 1 rep and degrades as reps climb, so a 20-rep set is a poor 1RM estimate
     * (decision D6 — "lean on normal strength-range sets"). Sets above this are ignored for rating
     * purposes only.
     */
    const val MAX_QUALIFYING_REPS = 12

    /** "Current strength" — the user chose three months. */
    const val WINDOW_DAYS = 90L

    /**
     * A weigh-in older than the rating window cannot describe current body weight. Rather than
     * rate against a stale number the app says so and asks, following its own stated refusal to
     * substitute a "typical" value (`PreferencesManager` line 337).
     */
    const val MAX_WEIGHIN_AGE_DAYS = 90L

    enum class LiftRole { MAIN, ACCESSORY }

    /** e1RM-to-body-weight multiples at each tier boundary, ascending. */
    data class Standards(
        val beginner: Float,
        val novice: Float,
        val intermediate: Float,
        val advanced: Float,
        val elite: Float,
    ) {
        val thresholds: List<Float> get() = listOf(beginner, novice, intermediate, advanced, elite)
    }

    /**
     * @param bodyWeightFraction the share of body weight the movement itself loads. `0f` for a
     *   lift where the bar is the only load; `1f` for pull-ups, chin-ups and dips, where the
     *   athlete moves themselves and any belt plates are *added* to that. This is the answer to
     *   "what does a bodyweight lift contribute, having no kilos": it contributes body weight.
     * @param requiresAddedLoad true for the core lifts, which only rate when the user actually
     *   added weight — there is no defensible body-weight fraction for a rollout or a leg raise,
     *   and inventing one would fabricate a rating.
     */
    data class QualifyingLift(
        val id: String,
        val displayName: String,
        val group: String,
        val role: LiftRole,
        val bodyWeightFraction: Float,
        val male: Standards,
        val female: Standards,
        val keywords: List<String>,
        val excludes: List<String> = emptyList(),
        val requiresAddedLoad: Boolean = false,
    ) {
        fun standardsFor(sex: String): Standards? = when (sex) {
            SEX_MALE -> male
            SEX_FEMALE -> female
            else -> null
        }
    }

    /**
     * Deliberately the SAME constants the body-composition profile already stores, not a private
     * copy — `PreferencesManager.sex` writes these exact strings, and a second spelling would
     * silently leave every user unrated.
     */
    const val SEX_MALE = com.migul.treningsprogram.domain.BodyComposition.SEX_MALE
    const val SEX_FEMALE = com.migul.treningsprogram.domain.BodyComposition.SEX_FEMALE

    /**
     * Markers that disqualify a name outright, checked before any lift keyword. A single hit is
     * enough: "Smith Machine Bench Press" and "Assisted Pull-Up" must never rate.
     */
    private val DISQUALIFYING = listOf(
        "machine", "smith", "cable", "pulldown", "pec deck", "hack squat", "leg press",
        "assisted", "band", "dumbbell", "kettlebell", "single-arm", "single arm",
        "one-arm", "one arm", "landmine", "resistance", "goblet", "sissy", "sled",
    )

    /** Word-boundary "db" so "DB Bench Press" is caught without matching "deadlift" or similar. */
    private val DB_WORD = Regex("\\bdbs?\\b")

    /**
     * The catalogue, ordered **most specific first** — [identify] returns the first match, exactly
     * like `MuscleClassifier.fromName`. "Incline Bench Press" must beat "Bench Press", and
     * "Romanian Deadlift" must beat "Deadlift", or a lifter's accessory would be rated against
     * their main lift's standard.
     */
    val LIFTS: List<QualifyingLift> = listOf(

        // ── Legs ─────────────────────────────────────────────────────────────────────────────
        QualifyingLift(
            "front_squat", "Front Squat", "Legs", LiftRole.ACCESSORY, 0f,
            male = Standards(0.60f, 1.00f, 1.40f, 1.85f, 2.30f),
            female = Standards(0.40f, 0.65f, 0.95f, 1.30f, 1.70f),
            keywords = listOf("front squat"),
        ),
        QualifyingLift(
            "romanian_deadlift", "Romanian Deadlift", "Legs", LiftRole.ACCESSORY, 0f,
            male = Standards(0.75f, 1.15f, 1.60f, 2.10f, 2.55f),
            female = Standards(0.50f, 0.80f, 1.15f, 1.55f, 1.95f),
            keywords = listOf("romanian deadlift", "rdl", "stiff-leg deadlift",
                "stiff leg deadlift", "straight-leg deadlift", "straight leg deadlift"),
        ),
        QualifyingLift(
            "sumo_deadlift", "Sumo Deadlift", "Legs", LiftRole.ACCESSORY, 0f,
            male = Standards(1.00f, 1.50f, 2.00f, 2.60f, 3.10f),
            female = Standards(0.60f, 1.00f, 1.40f, 1.90f, 2.40f),
            keywords = listOf("sumo deadlift", "sumo"),
        ),
        QualifyingLift(
            "hip_thrust", "Hip Thrust", "Legs", LiftRole.ACCESSORY, 0f,
            male = Standards(0.90f, 1.50f, 2.20f, 3.00f, 3.80f),
            female = Standards(0.75f, 1.30f, 1.95f, 2.70f, 3.45f),
            keywords = listOf("hip thrust"),
        ),
        QualifyingLift(
            "back_squat", "Back Squat", "Legs", LiftRole.MAIN, 0f,
            male = Standards(0.75f, 1.25f, 1.75f, 2.35f, 2.90f),
            female = Standards(0.50f, 0.80f, 1.20f, 1.65f, 2.10f),
            keywords = listOf("back squat", "barbell squat", "squat"),
            excludes = listOf("front", "split", "bulgarian", "pistol", "wall",
                "jump", "zercher", "overhead", "box", "pause"),
        ),

        // ── Back ─────────────────────────────────────────────────────────────────────────────
        QualifyingLift(
            "barbell_row", "Barbell Row", "Back", LiftRole.ACCESSORY, 0f,
            male = Standards(0.50f, 0.75f, 1.05f, 1.40f, 1.75f),
            female = Standards(0.30f, 0.50f, 0.70f, 1.00f, 1.30f),
            keywords = listOf("barbell row", "bent over row", "bent-over row", "pendlay row"),
        ),
        QualifyingLift(
            "chin_up", "Chin-Up", "Back", LiftRole.ACCESSORY, 1f,
            male = Standards(0.95f, 1.15f, 1.40f, 1.70f, 2.05f),
            female = Standards(0.85f, 1.00f, 1.20f, 1.45f, 1.75f),
            keywords = listOf("chin-up", "chin up", "chinup"),
        ),
        QualifyingLift(
            "pull_up", "Pull-Up", "Back", LiftRole.ACCESSORY, 1f,
            male = Standards(0.95f, 1.10f, 1.35f, 1.65f, 2.00f),
            female = Standards(0.85f, 0.95f, 1.15f, 1.40f, 1.70f),
            keywords = listOf("pull-up", "pull up", "pullup"),
        ),
        QualifyingLift(
            "deadlift", "Deadlift", "Back", LiftRole.MAIN, 0f,
            male = Standards(1.00f, 1.50f, 2.00f, 2.60f, 3.10f),
            female = Standards(0.60f, 1.00f, 1.40f, 1.90f, 2.40f),
            keywords = listOf("deadlift"),
            excludes = listOf("romanian", "sumo", "stiff", "straight", "deficit", "trap bar",
                "hex bar", "snatch"),
        ),

        // ── Chest ────────────────────────────────────────────────────────────────────────────
        QualifyingLift(
            "incline_bench", "Incline Bench Press", "Chest", LiftRole.ACCESSORY, 0f,
            male = Standards(0.40f, 0.60f, 1.00f, 1.45f, 1.80f),
            female = Standards(0.25f, 0.35f, 0.60f, 0.85f, 1.10f),
            keywords = listOf("incline bench press", "incline barbell press", "incline bench"),
        ),
        QualifyingLift(
            "dip", "Dip", "Chest", LiftRole.ACCESSORY, 1f,
            male = Standards(0.95f, 1.10f, 1.35f, 1.65f, 2.00f),
            female = Standards(0.85f, 0.95f, 1.15f, 1.40f, 1.70f),
            keywords = listOf("dip"),
            // A bench/tricep dip is a different movement under a different load and would be
            // rated Chest here while the rest of the app calls it Arms. Leave it unrated.
            excludes = listOf("hip", "tricep", "bench dip"),
        ),
        QualifyingLift(
            "bench_press", "Bench Press", "Chest", LiftRole.MAIN, 0f,
            male = Standards(0.50f, 0.75f, 1.25f, 1.75f, 2.10f),
            female = Standards(0.30f, 0.45f, 0.75f, 1.05f, 1.35f),
            keywords = listOf("bench press", "barbell bench", "flat bench"),
            excludes = listOf("incline", "decline", "close", "floor", "board", "spoto", "pause"),
        ),

        // ── Shoulders ────────────────────────────────────────────────────────────────────────
        QualifyingLift(
            "push_press", "Push Press", "Shoulders", LiftRole.ACCESSORY, 0f,
            male = Standards(0.45f, 0.70f, 1.00f, 1.35f, 1.70f),
            female = Standards(0.28f, 0.45f, 0.65f, 0.92f, 1.20f),
            keywords = listOf("push press"),
        ),
        QualifyingLift(
            "overhead_press", "Overhead Press", "Shoulders", LiftRole.MAIN, 0f,
            male = Standards(0.35f, 0.55f, 0.80f, 1.10f, 1.40f),
            female = Standards(0.20f, 0.35f, 0.50f, 0.75f, 1.00f),
            // "Seated" barbell pressing is included on purpose: its standard sits a little below
            // standing, but excluding it would leave a shoulder-pressing user permanently unrated,
            // which is the worse error.
            keywords = listOf("overhead press", "ohp", "military press", "strict press",
                "shoulder press", "standing press"),
            excludes = listOf("arnold", "z-press", "z press"),
        ),

        // ── Arms ─────────────────────────────────────────────────────────────────────────────
        QualifyingLift(
            "skull_crusher", "Skull Crusher", "Arms", LiftRole.ACCESSORY, 0f,
            male = Standards(0.20f, 0.35f, 0.50f, 0.70f, 0.90f),
            female = Standards(0.12f, 0.22f, 0.33f, 0.48f, 0.65f),
            keywords = listOf("skull crusher", "skullcrusher", "lying triceps extension",
                "lying tricep extension"),
        ),
        QualifyingLift(
            "barbell_curl", "Barbell Curl", "Arms", LiftRole.MAIN, 0f,
            male = Standards(0.25f, 0.40f, 0.60f, 0.85f, 1.10f),
            female = Standards(0.15f, 0.25f, 0.40f, 0.55f, 0.75f),
            keywords = listOf("barbell curl", "ez bar curl", "ez-bar curl", "straight bar curl",
                "bicep curl", "biceps curl"),
            excludes = listOf("hammer", "preacher", "concentration", "spider", "reverse", "wrist"),
        ),

        // ── Core ─────────────────────────────────────────────────────────────────────────────
        // All three require added load. A body-weight rollout or leg raise has no defensible
        // body-weight fraction, and inventing one would fabricate a rating.
        QualifyingLift(
            "hanging_leg_raise", "Hanging Leg Raise", "Core", LiftRole.ACCESSORY, 0f,
            male = Standards(0.05f, 0.15f, 0.30f, 0.50f, 0.75f),
            female = Standards(0.03f, 0.10f, 0.20f, 0.35f, 0.55f),
            keywords = listOf("hanging leg raise", "hanging knee raise"),
            requiresAddedLoad = true,
        ),
        QualifyingLift(
            "ab_wheel", "Ab Wheel Rollout", "Core", LiftRole.ACCESSORY, 0f,
            male = Standards(0.05f, 0.15f, 0.30f, 0.50f, 0.75f),
            female = Standards(0.03f, 0.10f, 0.20f, 0.35f, 0.55f),
            keywords = listOf("ab wheel", "ab roller", "rollout", "roll-out"),
            requiresAddedLoad = true,
        ),
        QualifyingLift(
            "weighted_situp", "Weighted Sit-Up", "Core", LiftRole.MAIN, 0f,
            male = Standards(0.10f, 0.25f, 0.45f, 0.70f, 1.00f),
            female = Standards(0.06f, 0.16f, 0.30f, 0.48f, 0.70f),
            keywords = listOf("sit-up", "sit up", "situp", "crunch"),
            requiresAddedLoad = true,
        ),
    )

    /** The lift a group's unlock hint should name — its MAIN lift. */
    fun mainLiftFor(group: String): QualifyingLift? =
        LIFTS.firstOrNull { it.group == group && it.role == LiftRole.MAIN }

    fun liftsFor(group: String): List<QualifyingLift> = LIFTS.filter { it.group == group }

    /**
     * The qualifying lift [exerciseName] represents, or null if it does not rate.
     *
     * Null is the overwhelmingly common answer and is not a failure: machines, cables, dumbbells,
     * isolation work and anything unrecognised all return null and simply never move a rating.
     */
    fun identify(exerciseName: String): QualifyingLift? {
        val lower = exerciseName.lowercase().trim()
        if (lower.isEmpty()) return null
        if (DISQUALIFYING.any { lower.contains(it) }) return null
        if (DB_WORD.containsMatchIn(lower)) return null
        return LIFTS.firstOrNull { lift ->
            lift.keywords.any { lower.contains(it) } && lift.excludes.none { lower.contains(it) }
        }
    }
}
