package com.migul.treningsprogram.domain.strength

import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.strength.StrengthStandards.LiftRole
import com.migul.treningsprogram.domain.strength.StrengthStandards.QualifyingLift
import com.migul.treningsprogram.domain.strength.StrengthStandards.Standards
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** The six names a rating is ever shown as. The user chose names over numbers. */
enum class StrengthTier(val displayName: String) {
    UNTRAINED("Untrained"),
    BEGINNER("Beginner"),
    NOVICE("Novice"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
    ELITE("Elite");

    val next: StrengthTier? get() = values().getOrNull(ordinal + 1)
}

/** Why something has no rating. Never a hidden blank — every one of these is shown and explained. */
enum class UnratedReason {
    /** Ratings compare against people of the user's sex; there is no honest default. */
    NO_SEX,

    /** No weigh-in at all. A relative-strength rating without body weight is not a rating. */
    NO_BODY_WEIGHT,

    /** The newest weigh-in predates the rating window, so it cannot describe current body weight. */
    STALE_BODY_WEIGHT,

    /** Nothing with a population standard was logged for this group inside the window. */
    NO_QUALIFYING_LIFT,
}

/** One working set, reduced to what a rating needs. Warm-ups are excluded by the caller. */
data class RatingSet(
    val exerciseName: String,
    val weightKg: Float,
    val reps: Int,
    val dateMs: Long,
)

/**
 * "What would move this" — in the user's own numbers, not a description of the method.
 * Both routes are offered where both are possible: add load, or add reps at the load you use now.
 */
data class NextStep(
    val liftName: String,
    val targetTier: StrengthTier,
    /** Extra kilos on the bar — or on the belt, for a body-weight lift. Null if unreachable. */
    val addedKg: Float?,
    /** Extra reps at the current load. Null when that would exceed the qualifying rep range. */
    val addedReps: Int?,
    val currentWeightKg: Float,
    val currentReps: Int,
    val isBodyWeightLift: Boolean,
)

data class GroupRating(
    val group: String,
    /** Null means unrated — read [unratedReason]. */
    val tier: StrengthTier?,
    /** 0..5, continuous. Internal; the user only ever sees [tier]. */
    val score: Float,
    val drivingLift: String?,
    val bestWeightKg: Float?,
    val bestReps: Int?,
    val nextStep: NextStep?,
    val unratedReason: UnratedReason?,
) {
    val isRated: Boolean get() = tier != null
}

data class StrengthProfile(
    /** Always all six rated groups, in display order, rated or not. */
    val groups: List<GroupRating>,
    val totalTier: StrengthTier?,
    val totalScore: Float,
    /**
     * 0..100 — [totalScore] on a per-cent scale. This is the single number the gamification layer
     * keys on, and it is what let all 27 level-keyed achievements keep their ids AND their original
     * thresholds when the XP level was retired: "level 60" simply became "strength score 60",
     * i.e. Intermediate overall. Nothing already earned had to move.
     */
    val strengthScore: Int,
    /** Informational only — the app never changes the user's priority muscles by itself. */
    val weakestRatedGroup: String?,
    /** Set when the whole profile is unrated: no sex, or no usable weigh-in. */
    val profileUnratedReason: UnratedReason?,
    val bodyWeightKg: Float?,
) {
    val isRated: Boolean get() = totalTier != null
}

/**
 * Brief 02 (2026-08-07) — "level" means strength.
 *
 * A rating answers "how do I compare to people my size and sex", from the best qualifying set in
 * the last three months. It is **recomputed from logged history every time** and stored nowhere
 * (assumption A3), following the same precedent the app set for body fat in backup v9: derived
 * values are not serialised, they are recalculated. That is what makes backup parity free — there
 * is no stored rating to drift, and the import path's `StatsRecomputer` has nothing extra to
 * rebuild, so an exported and re-imported history necessarily produces the same ratings.
 *
 * Consequences the user was shown and accepted:
 *  - ratings **fall** if three months pass without a qualifying set;
 *  - **gaining body weight lowers a rating** even with unchanged lifts. That is what relative
 *    strength means and it must not be "corrected".
 *
 * Pure and Android-free, so every value is hand-checkable in JVM tests.
 *
 * ## A note on "best qualifying set"
 * The best set is picked **in Kotlin, over raw rows**. It is deliberately not a SQL
 * `MAX(weightKg)` alongside a bare `reps` column — that exact shape shipped as a bug in v1.36.0,
 * where SQLite returned the reps of an unrelated row and hid rep progress app-wide. Here "best"
 * also is not "heaviest": it is the highest e1RM, so 80 kg x 8 correctly beats 85 kg x 3.
 */
object StrengthRating {

    /** The user asked for the main lift to dominate and accessories to nudge. This is that. */
    const val MAIN_WEIGHT = 0.75f
    const val ACCESSORY_WEIGHT = 0.25f

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** A rated lift, its best qualifying set in the window, and what that set scores. */
    private data class LiftBest(
        val lift: QualifyingLift,
        val set: RatingSet,
        val totalLoadKg: Float,
        val score: Float,
    )

    fun rate(
        sets: List<RatingSet>,
        bodyWeightKg: Float?,
        bodyWeightDateMs: Long?,
        sex: String,
        nowMs: Long,
    ): StrengthProfile {
        // ── No guessed ratings. The app already refuses to substitute a typical height or sex
        //    rather than fabricate a body-fat figure; the same rule applies here (decision D4).
        val blocked: UnratedReason? = when {
            sex != StrengthStandards.SEX_MALE && sex != StrengthStandards.SEX_FEMALE ->
                UnratedReason.NO_SEX
            bodyWeightKg == null || bodyWeightKg <= 0f ->
                UnratedReason.NO_BODY_WEIGHT
            bodyWeightDateMs == null ||
                nowMs - bodyWeightDateMs > StrengthStandards.MAX_WEIGHIN_AGE_DAYS * DAY_MS ->
                UnratedReason.STALE_BODY_WEIGHT
            else -> null
        }
        if (blocked != null) return allUnrated(blocked, bodyWeightKg)

        val bw = bodyWeightKg!!
        val since = nowMs - StrengthStandards.WINDOW_DAYS * DAY_MS

        // Best qualifying set per lift, by e1RM — computed here, in Kotlin, over raw rows.
        val bestByLift = HashMap<String, LiftBest>()
        for (s in sets) {
            if (s.dateMs < since) continue
            if (s.reps < 1 || s.reps > StrengthStandards.MAX_QUALIFYING_REPS) continue
            val lift = StrengthStandards.identify(s.exerciseName) ?: continue
            if (lift.requiresAddedLoad && s.weightKg <= 0f) continue
            val totalLoad = lift.bodyWeightFraction * bw + s.weightKg
            if (totalLoad <= 0f) continue
            val std = lift.standardsFor(sex) ?: continue
            val e1rm = Epley.estimate(totalLoad, s.reps)
            if (e1rm <= 0.0) continue
            val score = liftScore((e1rm / bw).toFloat(), std)
            val prev = bestByLift[lift.id]
            if (prev == null || score > prev.score) {
                bestByLift[lift.id] = LiftBest(lift, s, totalLoad, score)
            }
        }

        val groups = StrengthStandards.RATED_GROUPS.map { group ->
            rateGroup(group, bestByLift.values.filter { it.lift.group == group }, bw, sex)
        }

        val rated = groups.filter { it.isRated }
        if (rated.isEmpty()) {
            return StrengthProfile(
                groups = groups, totalTier = null, totalScore = 0f, strengthScore = 0,
                weakestRatedGroup = null, profileUnratedReason = null, bodyWeightKg = bw,
            )
        }

        // Bigger groups weigh more; unrated groups are absent rather than zero, which is what the
        // renormalising denominator does.
        var weighted = 0f
        var weightSum = 0f
        rated.forEach { g ->
            val w = StrengthStandards.GROUP_WEIGHTS[g.group] ?: 1f
            weighted += w * g.score
            weightSum += w
        }
        val totalScore = weighted / weightSum

        return StrengthProfile(
            groups = groups,
            totalTier = tierFor(totalScore),
            totalScore = totalScore,
            strengthScore = (totalScore * 20f).roundToInt().coerceIn(0, 100),
            weakestRatedGroup = rated.minByOrNull { it.score }?.group,
            profileUnratedReason = null,
            bodyWeightKg = bw,
        )
    }

    private fun allUnrated(reason: UnratedReason, bw: Float?) = StrengthProfile(
        groups = StrengthStandards.RATED_GROUPS.map {
            GroupRating(it, null, 0f, null, null, null, null, reason)
        },
        totalTier = null, totalScore = 0f, strengthScore = 0,
        weakestRatedGroup = null, profileUnratedReason = reason, bodyWeightKg = bw,
    )

    private fun rateGroup(
        group: String,
        bests: List<LiftBest>,
        bw: Float,
        sex: String,
    ): GroupRating {
        if (bests.isEmpty()) {
            return GroupRating(group, null, 0f, null, null, null, null,
                UnratedReason.NO_QUALIFYING_LIFT)
        }
        val main = bests.filter { it.lift.role == LiftRole.MAIN }.maxByOrNull { it.score }
        val accessory = bests.filter { it.lift.role == LiftRole.ACCESSORY }.maxByOrNull { it.score }

        // Main dominates, accessories nudge. With both present the main carries three quarters of
        // the rating, so a strong accessory lifts it and a weak one pulls it down — but neither
        // can overturn it. With only one of the two, that one simply is the rating.
        val score = when {
            main != null && accessory != null ->
                MAIN_WEIGHT * main.score + ACCESSORY_WEIGHT * accessory.score
            else -> (main ?: accessory)!!.score
        }
        val driving = main ?: accessory!!
        return GroupRating(
            group = group,
            tier = tierFor(score),
            score = score,
            drivingLift = driving.lift.displayName,
            bestWeightKg = driving.set.weightKg,
            bestReps = driving.set.reps,
            nextStep = nextStep(
                groupScore = score,
                driving = driving,
                otherScore = if (main != null) accessory?.score else null,
                bw = bw,
                sex = sex,
            ),
            unratedReason = null,
        )
    }

    /**
     * What it would take to reach the next tier, expressed on the lift that drives the rating.
     *
     * Because the group score is a blend, the target is solved for properly: the accessory
     * contribution is held fixed and the main lift's required score is what gets converted back
     * into kilos and reps. Explaining it as "get the main lift to its own next tier" would be a
     * lie whenever accessories are dragging the group.
     */
    private fun nextStep(
        groupScore: Float,
        driving: LiftBest,
        otherScore: Float?,
        bw: Float,
        sex: String,
    ): NextStep? {
        val target = tierFor(groupScore).next ?: return null   // already Elite
        val targetScore = target.ordinal.toFloat()
        val needScore =
            if (otherScore != null) (targetScore - ACCESSORY_WEIGHT * otherScore) / MAIN_WEIGHT
            else targetScore
        // Beyond Elite on this lift alone: the accessories are what is holding the group back, so
        // there is no honest single-lift instruction to give.
        if (needScore > 5f) return null

        val std = driving.lift.standardsFor(sex) ?: return null
        val neededE1rm = ratioForScore(needScore, std) * bw
        val reps = driving.set.reps
        val current = driving.totalLoadKg

        val neededLoad = neededE1rm / (1.0 + reps / 30.0)
        val addedKg = ceilToHalf((neededLoad - current).toFloat()).takeIf { it > 0f }

        val neededRepsExact = 30.0 * (neededE1rm / current - 1.0)
        val addedReps = if (neededRepsExact <= StrengthStandards.MAX_QUALIFYING_REPS) {
            (ceil(neededRepsExact).toInt() - reps).coerceAtLeast(1)
        } else null

        if (addedKg == null && addedReps == null) return null
        return NextStep(
            liftName = driving.lift.displayName,
            targetTier = target,
            addedKg = addedKg,
            addedReps = addedReps,
            currentWeightKg = driving.set.weightKg,
            currentReps = reps,
            isBodyWeightLift = driving.lift.bodyWeightFraction > 0f,
        )
    }

    /**
     * Where a lift's body-weight ratio sits on the 0..5 tier scale, interpolated between
     * thresholds. A ratio sitting exactly ON a boundary counts as the HIGHER tier — hitting the
     * Advanced number makes you Advanced.
     */
    fun liftScore(ratio: Float, s: Standards): Float {
        if (ratio <= 0f) return 0f
        val t = s.thresholds
        if (ratio < t[0]) return (ratio / t[0]).coerceIn(0f, 1f)
        for (k in 0 until t.size - 1) {
            if (ratio < t[k + 1]) return (k + 1) + (ratio - t[k]) / (t[k + 1] - t[k])
        }
        return 5f
    }

    /** The inverse of [liftScore]: the ratio a lift must reach to score [score]. */
    fun ratioForScore(score: Float, s: Standards): Float {
        val t = s.thresholds
        val c = score.coerceIn(0f, 5f)
        if (c <= 1f) return t[0] * c
        if (c >= 5f) return t[4]
        val k = floor(c).toInt()
        return t[k - 1] + (c - k) * (t[k] - t[k - 1])
    }

    fun tierFor(score: Float): StrengthTier =
        StrengthTier.values()[floor(score).toInt().coerceIn(0, 5)]

    private fun ceilToHalf(v: Float): Float = (ceil(v * 2f) / 2f)
}
