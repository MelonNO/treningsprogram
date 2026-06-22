package com.migul.treningsprogram.data

import javax.inject.Inject
import javax.inject.Singleton

enum class MatchSource { EXACT, ALIAS, FUZZY, LLM, NONE }

data class ResolveResult(
    val dbId: String,
    val confidence: Float,
    val source: MatchSource
)

data class ResolveHints(val muscle: String? = null, val equipment: String? = null)

@Singleton
class ExerciseDbResolver @Inject constructor(
    private val resolutionLog: ExerciseResolutionLog
) {

    // Curated aliases for common exercise names that the fuzzy resolver can't reach.
    // Keys are post-stemming normalized names (ExerciseCatalog.normalizeName output).
    private val seedAliases: Map<String, String> = mapOf(
        // ── Chest ──────────────────────────────────────────────────────────────
        "bench press"            to "Barbell_Bench_Press_-_Medium_Grip",
        "barbell bench press"    to "Barbell_Bench_Press_-_Medium_Grip",
        "dumbbell bench press"   to "Dumbbell_Bench_Press",
        "incline bench press"    to "Barbell_Incline_Bench_Press_-_Medium_Grip",
        "incline barbell press"  to "Barbell_Incline_Bench_Press_-_Medium_Grip",
        "cable flye"             to "Low_Cable_Crossover",
        "push up"                to "Pushups",
        "push ups"               to "Pushups",
        // ── Back ───────────────────────────────────────────────────────────────
        "deadlift"               to "Barbell_Deadlift",
        "barbell row"            to "Bent_Over_Barbell_Row",
        "bent over row"          to "Bent_Over_Barbell_Row",
        "bent over barbell row"  to "Bent_Over_Barbell_Row",
        "dumbbell row"           to "Bent_Over_Two-Dumbbell_Row",
        "pull up"                to "Wide-Grip_Rear_Pull-Up",
        "pull ups"               to "Wide-Grip_Rear_Pull-Up",
        "lat pulldown"           to "Wide-Grip_Lat_Pulldown",
        "cable row"              to "Seated_Cable_Rows",
        "seated cable row"       to "Seated_Cable_Rows",
        // ── Legs ───────────────────────────────────────────────────────────────
        "squat"                  to "Barbell_Squat",
        "leg curl"               to "Seated_Leg_Curl",
        "leg extension"          to "Leg_Extensions",
        "calf raise"             to "Standing_Calf_Raises",
        "hip thrust"             to "Barbell_Hip_Thrust",
        // ── Shoulders ──────────────────────────────────────────────────────────
        "overhead press"         to "Barbell_Shoulder_Press",
        "barbell overhead press" to "Barbell_Shoulder_Press",
        "military press"         to "Barbell_Shoulder_Press",
        "standing overhead press" to "Barbell_Shoulder_Press",
        "lateral raise"          to "Side_Lateral_Raise",
        "dumbbell lateral raise" to "Side_Lateral_Raise",
        // ── Arms ───────────────────────────────────────────────────────────────
        "bicep curl"             to "Dumbbell_Bicep_Curl",
        "barbell curl"           to "Barbell_Curl",
        "skull crusher"          to "EZ-Bar_Skullcrusher",
        "tricep rope pushdown"   to "Triceps_Pushdown_-_Rope_Attachment",
        "rope pushdown"          to "Triceps_Pushdown_-_Rope_Attachment",
        // ── Core / Cardio ──────────────────────────────────────────────────────
        "leg raise"              to "Flat_Bench_Lying_Leg_Raise",
        "ab wheel rollout"       to "Ab_Roller",
        "ab wheel"               to "Ab_Roller",
        "ab rollout"             to "Ab_Roller",
        // ── Chest (fly variants) ───────────────────────────────────────────────
        "dumbbell chest fly"     to "Dumbbell_Flyes",
        "dumbbell fly"           to "Dumbbell_Flyes",
        "chest fly"              to "Dumbbell_Flyes",
        "cable chest fly"        to "Low_Cable_Crossover",
        "cable fly"              to "Low_Cable_Crossover",
        "incline dumbbell fly"   to "Incline_Dumbbell_Flyes",
        "incline fly"            to "Incline_Dumbbell_Flyes",
        // ── Shoulders (rear delt) ──────────────────────────────────────────────
        "rear delt dumbbell fly" to "Seated_Bent-Over_Rear_Delt_Raise",
        "rear delt fly"          to "Seated_Bent-Over_Rear_Delt_Raise",
        "rear deltoid fly"       to "Seated_Bent-Over_Rear_Delt_Raise",
        "rear delt raise"        to "Seated_Bent-Over_Rear_Delt_Raise",
        // ── Shoulders (incline lateral) ────────────────────────────────────────
        "dumbbell incline lateral raise" to "One-Arm_Incline_Lateral_Raise",
        "incline lateral raise"  to "One-Arm_Incline_Lateral_Raise",
        // ── Chest (close-grip) ─────────────────────────────────────────────────
        "close grip bench press" to "Close-Grip_Barbell_Bench_Press",
        // ── Legs (single-leg calf) ─────────────────────────────────────────────
        "single leg calf raise"  to "Dumbbell_Seated_One-Leg_Calf_Raise",
        // ── Shoulders (seated press / seated lateral) ──────────────────────────
        "seated dumbbell shoulder press" to "Dumbbell_Shoulder_Press",
        "seated dumbbell lateral raise"  to "Seated_Side_Lateral_Raise",
        "seated lateral raise"           to "Seated_Side_Lateral_Raise",
        // ── Shoulders (rear delt — with equipment prefix) ──────────────────────
        "dumbbell rear delt fly"   to "Seated_Bent-Over_Rear_Delt_Raise",
        "dumbbell rear delt raise" to "Seated_Bent-Over_Rear_Delt_Raise",
        "bent over rear delt raise" to "Seated_Bent-Over_Rear_Delt_Raise",
        // ── Arms (overhead / seated tricep) ───────────────────────────────────
        "overhead dumbbell tricep extension" to "Standing_Dumbbell_Triceps_Extension",
        "dumbbell overhead tricep extension" to "Standing_Dumbbell_Triceps_Extension",
        "overhead tricep extension"          to "Standing_Dumbbell_Triceps_Extension",
        "seated tricep extension"            to "Seated_Triceps_Press",
        // ── Back (pullover) ────────────────────────────────────────────────────
        "dumbbell pullover"  to "Straight-Arm_Dumbbell_Pullover",
        // ── Core (hanging knee / leg raise) ───────────────────────────────────
        "hanging knee raise" to "Hanging_Leg_Raise",
        // ── Legs (hip thrust / glute bridge variants) ──────────────────────────
        "dumbbell hip thrust"  to "Barbell_Hip_Thrust",
        "glute bridge"         to "Barbell_Glute_Bridge",
        "dumbbell glute bridge" to "Barbell_Glute_Bridge",
        "barbell glute bridge" to "Barbell_Glute_Bridge",
        // ── Cardio ─────────────────────────────────────────────────────────────
        "easy jog"  to "Jogging_Treadmill",
        "jog"       to "Jogging_Treadmill",
        "jogging"   to "Jogging_Treadmill",
        // ── Ankle mobility ─────────────────────────────────────────────────────
        "ankle circle"                                     to "Ankle_Circles",
        "seated ankle circle dorsiflexion hold"            to "Ankle_Circles",
        "seated ankle alphabet ankle circle"               to "Ankle_Circles",
        "seated ankle dorsiflexion stretch towel toe curl" to "Ankle_On_The_Knee",
        // ── Cardio / Conditioning ──────────────────────────────────────────────
        "treadmill run"                              to "Running_Treadmill",
        "running treadmill"                          to "Running_Treadmill",
        "outdoor run"                                to "Trail_Running_Walking",
        "interval run"                               to "Running_Treadmill",
        "tempo run"                                  to "Running_Treadmill",
        "stationary bike"                            to "Bicycling_Stationary",
        "bike"                                       to "Bicycling_Stationary",
        "cycling"                                    to "Bicycling",
        "jump rope"                                  to "Rope_Jumping",
        "skipping"                                   to "Rope_Jumping",
        "skip"                                       to "Rope_Jumping",
        "high knees"                                 to "Running_Treadmill",
        "burpees"                                    to "Pullups",
        "burpee"                                     to "Pullups",
        // ── Back ───────────────────────────────────────────────────────────────
        "barbell pendlay row"                        to "Bent_Over_Barbell_Row",
        "pendlay row"                                to "Bent_Over_Barbell_Row",
        "dumbbell chest supported row"               to "Dumbbell_Incline_Row",
        "chest supported row"                        to "Dumbbell_Incline_Row",
        "dumbbell incline bench chest supported row" to "Dumbbell_Incline_Row",
        "dumbbell seal row"                          to "Dumbbell_Incline_Row",
        "seal row"                                   to "Dumbbell_Incline_Row",
        // ── Chest ──────────────────────────────────────────────────────────────
        "dumbbell flat bench fly"                    to "Dumbbell_Flyes",
        "dumbbell decline chest fly"                 to "Decline_Dumbbell_Flyes",
        "decline chest fly"                          to "Decline_Dumbbell_Flyes",
        "dumbbell incline chest fly"                 to "Incline_Dumbbell_Flyes",
        "dumbbell single arm flat bench press"       to "Dumbbell_Bench_Press",
        "single arm flat bench press"                to "Dumbbell_Bench_Press",
        // ── Shoulders ──────────────────────────────────────────────────────────
        "barbell seated behind neck press"           to "Standing_Barbell_Press_Behind_Neck",
        "barbell behind neck press"                  to "Standing_Barbell_Press_Behind_Neck",
        "barbell behind neck seated press"           to "Standing_Barbell_Press_Behind_Neck",
        "behind neck press"                          to "Standing_Barbell_Press_Behind_Neck",
        // ── Arms ───────────────────────────────────────────────────────────────
        "dumbbell zottman curl"                      to "Zottman_Curl",
        "zottman curl"                               to "Zottman_Curl",
        "dumbbell cross body hammer curl"            to "Cross_Body_Hammer_Curl",
        "cross body hammer curl"                     to "Cross_Body_Hammer_Curl",
        "dumbbell skull crusher"                     to "EZ-Bar_Skullcrusher",
        "dumbbell skullcrusher"                      to "EZ-Bar_Skullcrusher",
        "barbell preacher curl"                      to "Preacher_Curl",
        "preacher curl"                              to "Preacher_Curl",
        "dumbbell preacher curl"                     to "One_Arm_Dumbbell_Preacher_Curl",
        "concentration curl"                         to "Concentration_Curls",
        "dumbbell concentration curl"                to "Concentration_Curls",
        // ── Pull-ups ───────────────────────────────────────────────────────────
        "neutral grip pull up"                       to "V-Bar_Pullup",
        "neutral grip pullup"                        to "V-Bar_Pullup",
        "weighted pull up"                           to "Weighted_Pull_Ups",
        "weighted pullup"                            to "Weighted_Pull_Ups",
        "weighted pull ups"                          to "Weighted_Pull_Ups",
        // ── Legs ───────────────────────────────────────────────────────────────
        "dumbbell step up"                           to "Dumbbell_Step_Ups",
        "step up"                                    to "Barbell_Step_Ups",
        "dumbbell romanian deadlift"                 to "Romanian_Deadlift",
        "dumbbell rdl"                               to "Romanian_Deadlift",
        "single leg romanian deadlift"               to "Romanian_Deadlift",
        "dumbbell single leg romanian deadlift"      to "Romanian_Deadlift",
        "seated dumbbell calf raise"                 to "Barbell_Seated_Calf_Raise",
        "seated calf raise"                          to "Barbell_Seated_Calf_Raise",
        "good morning"                               to "Good_Morning",
        "dumbbell good morning"                      to "Good_Morning",
        // ── Back / Pullover ────────────────────────────────────────────────────
        "dumbbell lying pullover"                    to "Straight-Arm_Dumbbell_Pullover",
        "lying dumbbell pullover"                    to "Straight-Arm_Dumbbell_Pullover",
        // ── Chest / Press variants ─────────────────────────────────────────────
        "barbell floor press"                        to "Floor_Press",
        "floor press"                                to "Floor_Press",
        "dumbbell floor press"                       to "Dumbbell_Floor_Press",
        // ── Push-ups ───────────────────────────────────────────────────────────
        "diamond push up"                            to "Close-Grip_Barbell_Bench_Press",
        "diamond pushup"                             to "Close-Grip_Barbell_Bench_Press",
        "decline push up"                            to "Decline_Push-Up",
        "decline pushup"                             to "Decline_Push-Up",
        // ── Shrugs ─────────────────────────────────────────────────────────────
        "dumbbell shrug"                             to "Dumbbell_Shrug",
        "dumbbell single arm shrug"                  to "Dumbbell_Shrug",
        // ── Romanian / Conventional Deadlifts ──────────────────────────────────
        "barbell romanian deadlift"                  to "Romanian_Deadlift",
        "romanian deadlift"                          to "Romanian_Deadlift",
        "conventional deadlift"                      to "Barbell_Deadlift",
        "conventional deadlift barbell"              to "Barbell_Deadlift",
        // ── Hammer Curls ───────────────────────────────────────────────────────
        "hammer curl"                                to "Hammer_Curls",
        "hammer curls"                               to "Hammer_Curls",
        "dumbbell hammer curl"                       to "Hammer_Curls",
        "dumbbell hammer curl alternating standing"  to "Alternate_Hammer_Curl",
        "alternating hammer curl"                    to "Alternate_Hammer_Curl",
        "incline hammer curl"                        to "Incline_Hammer_Curls",
        "dumbbell incline hammer curl"               to "Incline_Hammer_Curls",
        // ── Seal Row (barbell) ─────────────────────────────────────────────────
        "barbell seal row"                           to "Bent_Over_Barbell_Row",
        // ── Core / Plank variants ──────────────────────────────────────────────
        "side plank"                                 to "Plank",
        "plank with shoulder tap"                    to "Plank",
        "hollow body hold"                           to "Plank",
        // ── Ankle / Foot prehab ────────────────────────────────────────────────
        "ankle dorsiflexion"                         to "Ankle_Circles",
        "banded ankle dorsiflexion"                  to "Ankle_Circles",
        "banded ankle dorsiflexion mobilisation"     to "Ankle_Circles",
        "ankle alphabet"                             to "Ankle_Circles",
        "ankle alphabet right foot"                  to "Ankle_Circles",
        "ankle alphabet prehab"                      to "Ankle_Circles",
        "towel toe curl"                             to "Ankle_On_The_Knee",
        "toe curl"                                   to "Ankle_On_The_Knee",
        "towel toe curl intrinsic foot strengthening" to "Ankle_On_The_Knee",
        // ── Single-leg calf raise variants ────────────────────────────────────
        "dumbbell single leg calf raise"             to "Dumbbell_Seated_One-Leg_Calf_Raise",
        "dumbbell single leg calf raise left leg only" to "Dumbbell_Seated_One-Leg_Calf_Raise",
        "single leg calf raise right leg only"       to "Dumbbell_Seated_One-Leg_Calf_Raise",
        // ── Wrist curls ────────────────────────────────────────────────────────
        "wrist curl"                                 to "Palms-Up_Dumbbell_Wrist_Curl_Over_A_Bench",
        "dumbbell wrist curl"                        to "Palms-Up_Dumbbell_Wrist_Curl_Over_A_Bench",
        // ── Pallof / anti-rotation ─────────────────────────────────────────────
        "pallof press"                               to "Pallof_Press",
        "pallof press isometric hold"                to "Pallof_Press",
        "anti rotation hold"                         to "Pallof_Press",
        "dumbbell anti rotation hold"                to "Pallof_Press",
        // ── Incline curl (prone / chest-supported) ─────────────────────────────
        "prone incline curl"                         to "Dumbbell_Prone_Incline_Curl",
        "dumbbell chest supported curl"              to "Dumbbell_Prone_Incline_Curl",
        "dumbbell chest supported curl prone on incline bench" to "Dumbbell_Prone_Incline_Curl",
        // ── Dumbbell Incline Bench Press ───────────────────────────────────────
        "dumbbell incline bench press"               to "Incline_Dumbbell_Bench_With_Palms_Facing_In",
        "incline dumbbell bench press"               to "Incline_Dumbbell_Bench_With_Palms_Facing_In",
        "incline dumbbell press"                     to "Incline_Dumbbell_Bench_With_Palms_Facing_In",
        // ── Ab Roller variants ─────────────────────────────────────────────────
        "ab roller rollout"                          to "Ab_Roller",
        "ab roller rollout slow eccentric"           to "Ab_Roller",
        // ── Flat bench press (dumbbell) ────────────────────────────────────────
        "dumbbell flat bench press"                  to "Dumbbell_Bench_Press",
        // ── Nordic / kneeling hamstring curls ──────────────────────────────────
        "nordic hamstring curl"                      to "Lying_Leg_Curls",
        "dumbbell nordic hamstring curl"             to "Lying_Leg_Curls",
        "nordic style hamstring curl"                to "Lying_Leg_Curls",
        "dumbbell nordic style hamstring curl"       to "Lying_Leg_Curls",
        "kneeling hamstring curl"                    to "Lying_Leg_Curls",
        "dumbbell kneeling hamstring curl"           to "Lying_Leg_Curls",
        "prone hamstring curl"                       to "Lying_Leg_Curls",
        "dumbbell prone hamstring curl"              to "Lying_Leg_Curls",
        // ── Rear delt row ──────────────────────────────────────────────────────
        "rear delt row"                              to "Dumbbell_Incline_Row",
        "dumbbell rear delt row"                     to "Dumbbell_Incline_Row",
        // ── Frog pump / glute ──────────────────────────────────────────────────
        "frog pump"                                  to "Barbell_Hip_Thrust",
        "dumbbell frog pump"                         to "Barbell_Hip_Thrust",
        // ── Goblet squat ───────────────────────────────────────────────────────
        "goblet squat"                               to "Goblet_Squat",
        "dumbbell goblet squat"                      to "Goblet_Squat",
        // ── Reverse fly (bent-over / prone) ────────────────────────────────────
        "reverse fly"                                to "Reverse_Flyes",
        "bent over reverse fly"                      to "Reverse_Flyes",
        "dumbbell bent over reverse fly"             to "Reverse_Flyes",
        // ── Lateral step-up ────────────────────────────────────────────────────
        "lateral step up"                            to "Dumbbell_Step_Ups",
        "dumbbell lateral step up"                   to "Dumbbell_Step_Ups"
    )

    // Discriminator terms — presence on one side but not the other disqualifies a candidate
    private val discriminatorGroups: List<Set<String>> = listOf(
        setOf("incline", "decline", "flat"),
        setOf("close grip", "wide grip", "neutral grip", "reverse grip"),
        setOf("barbell", "dumbbell", "cable", "machine", "smith", "bodyweight", "band"),
        setOf("single arm", "unilateral", "alternate"),
        setOf("seated", "standing"),
        setOf("front", "back"),
        setOf("pronated", "supinated", "hammer")
    )

    // For these groups, one side having a modifier while the other has none also disqualifies.
    // Equipment is handled the same way: "Bench Press" must not match "Close-Grip Bench Press".
    private val exclusiveGroups: Set<Int> = setOf(0, 1)  // incline/decline/flat, grip modifiers only

    fun resolve(name: String, hints: ResolveHints = ResolveHints()): ResolveResult? {
        if (ExerciseCatalog.entries.isEmpty()) return null

        // Step 1: Normalize
        val norm = ExerciseCatalog.normalizeName(name)

        // Step 2: Exact match on normalized name
        val exact = ExerciseCatalog.findByNormalizedName(norm)
        if (exact != null) return ResolveResult(exact.id, 1.0f, MatchSource.EXACT)

        // Step 3a: Seed aliases (built-in, checked before user-defined alias table)
        val seedId = seedAliases[norm]
        if (seedId != null && ExerciseCatalog.byId.containsKey(seedId)) {
            return ResolveResult(seedId, 1.0f, MatchSource.ALIAS)
        }

        // Step 3b: User alias table (SharedPreferences)
        val aliasId = resolutionLog.getAliasId(norm)
        if (aliasId != null && ExerciseCatalog.byId.containsKey(aliasId)) {
            return ResolveResult(aliasId, 1.0f, MatchSource.ALIAS)
        }

        // Step 4: Gated fuzzy
        val fuzzyResult = fuzzyResolve(norm, hints)
        if (fuzzyResult != null) {
            if (fuzzyResult.confidence < 0.70f) {
                resolutionLog.recordLowConf(name, fuzzyResult.dbId, fuzzyResult.confidence)
            }
            return fuzzyResult
        }

        // Step 5: Queue for LLM fallback (async, deferred)
        resolutionLog.enqueuePendingLlm(name)
        resolutionLog.recordMiss(name)

        return null
    }

    private fun fuzzyResolve(norm: String, hints: ResolveHints): ResolveResult? {
        val inputTokens = norm.split(" ").filter { it.length > 1 }.toSet()
        if (inputTokens.isEmpty()) return null

        data class Candidate(val entry: DbExerciseEntry, val score: Float)

        val candidates = ExerciseCatalog.entries.mapNotNull { entry ->
            val score = tokenOverlapScore(inputTokens, entry.tokenSet)
            if (score > 0.30f) Candidate(entry, score) else null
        }

        if (candidates.isEmpty()) return null

        // Apply discriminator gate
        val gated = candidates.filter { c ->
            !isDisqualifiedByGate(norm, c.entry.normalizedName)
        }

        if (gated.isEmpty()) return null

        // Sort by score descending
        val sorted = gated.sortedByDescending { it.score }
        val best = sorted[0]
        val secondScore = sorted.getOrNull(1)?.score ?: 0f

        // Must clear threshold and be a clear winner
        if (best.score < 0.55f) return null
        if (sorted.size > 1 && best.score < secondScore * 1.20f) {
            // Tie — try to break with hints
            val hintWinner = breakTieWithHints(sorted.take(3).map { it.entry }, hints)
                ?: return null
            return ResolveResult(hintWinner.id, best.score, MatchSource.FUZZY)
        }

        return ResolveResult(best.entry.id, best.score, MatchSource.FUZZY)
    }

    private fun tokenOverlapScore(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val intersection = a.intersect(b).size.toFloat()
        val union = (a + b).size.toFloat()
        return intersection / union  // Jaccard similarity
    }

    private fun isDisqualifiedByGate(inputNorm: String, candidateNorm: String): Boolean {
        discriminatorGroups.forEachIndexed { idx, group ->
            val inputTerms = group.filter { term -> inputNorm.contains(term) }.toSet()
            val candidateTerms = group.filter { term -> candidateNorm.contains(term) }.toSet()
            // Different modifier on each side → disqualify
            if (inputTerms.isNotEmpty() && candidateTerms.isNotEmpty() && inputTerms != candidateTerms) {
                return true
            }
            // For exclusive groups: modifier on one side but absent on the other → disqualify
            // (prevents "Bench Press" from matching "Close-Grip Bench Press", etc.)
            if (idx in exclusiveGroups) {
                if (inputTerms.isNotEmpty() && candidateTerms.isEmpty()) return true
                if (inputTerms.isEmpty() && candidateTerms.isNotEmpty()) return true
            }
        }
        return false
    }

    private fun breakTieWithHints(candidates: List<DbExerciseEntry>, hints: ResolveHints): DbExerciseEntry? {
        if (hints.muscle == null && hints.equipment == null) return null
        val scored = candidates.map { entry ->
            var score = 0
            if (hints.muscle != null && entry.primaryMuscles.any { it.contains(hints.muscle, ignoreCase = true) }) score++
            if (hints.equipment != null && entry.equipment?.contains(hints.equipment, ignoreCase = true) == true) score++
            entry to score
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        val second = scored.filter { it.first != best.first }.maxByOrNull { it.second }
        return if (second == null || best.second > second.second) best.first else null
    }

    fun getImageSource(dbId: String, frame: Int = 0): String =
        ExerciseCatalog.getImageSource(dbId, frame)

    fun getPlaceholderSource(): String = ""  // empty string → caller shows placeholder drawable
}
