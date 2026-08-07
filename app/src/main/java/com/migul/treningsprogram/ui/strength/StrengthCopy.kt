package com.migul.treningsprogram.ui.strength

import com.migul.treningsprogram.domain.strength.GroupRating
import com.migul.treningsprogram.domain.strength.NextStep
import com.migul.treningsprogram.domain.strength.StrengthProfile
import com.migul.treningsprogram.domain.strength.StrengthStandards
import com.migul.treningsprogram.domain.strength.StrengthTier
import com.migul.treningsprogram.domain.strength.UnratedReason
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Brief 02 (2026-08-07) — every user-facing sentence on the Strength breakdown screen.
 *
 * Deliberately **pure and Android-free**: the whole point of this screen is the wording, so the
 * wording is what gets unit-tested (`StrengthBreakdownCopyTest`) rather than a Fragment. The
 * Fragment and adapter below only place these strings into views.
 *
 * Two rules run through all of it:
 *  - **The user's own numbers.** "Add 5 kg" and "2 more reps at 80 kg" come from [NextStep], which
 *    solved them against the real standards. Nothing here re-derives or rounds a rating.
 *  - **Nothing is hidden and nothing is guessed.** An unrated group gets a sentence saying exactly
 *    what would unlock it, never a blank row and never an invented tier.
 *
 * Copy lives here as Kotlin constants rather than in `strings.xml` because that is what the
 * surrounding screens already do — `strings.xml` holds 15 strings for the whole app.
 */
object StrengthCopy {

    // ── The total ─────────────────────────────────────────────────────────────────────────────

    /** The total, as a tier NAME. Never a number — that was the product decision. */
    fun totalTierName(profile: StrengthProfile): String =
        profile.totalTier?.displayName ?: NOT_RATED

    fun totalCaption(profile: StrengthProfile): String = when {
        profile.isRated -> {
            val rated = profile.groups.count { it.isRated }
            "Weighted across your rated muscle groups — $rated of ${profile.groups.size} rated."
        }
        profile.profileUnratedReason != null -> unratedLine("", profile.profileUnratedReason)
        else ->
            "No qualifying lift in the last $WINDOW_MONTHS months yet. Log one of the lifts that " +
                "count and your rating appears here."
    }

    // ── One group row ─────────────────────────────────────────────────────────────────────────

    fun groupTierName(group: GroupRating): String = group.tier?.displayName ?: NOT_RATED

    /**
     * The driving lift and the best set behind the rating, e.g. `Bench Press · 5 reps × 100 kg`.
     * Body-weight lifts read as body weight rather than the "0 kg" the row actually stores —
     * [GroupRating.bestWeightKg] is the *added* load for those.
     */
    fun bestSetLine(group: GroupRating): String? {
        val lift = group.drivingLift ?: return null
        val weight = group.bestWeightKg ?: return null
        val reps = group.bestReps ?: return null
        val repWord = if (reps == 1) "1 rep" else "$reps reps"
        return when {
            !group.isBodyWeightLift -> "$lift · $repWord × ${kg(weight)} kg"
            weight <= 0f -> "$lift · $repWord at body weight"
            else -> "$lift · $repWord × body weight + ${kg(weight)} kg"
        }
    }

    /** "What would move this", in the user's own kilos and reps. */
    fun nextStepLine(group: GroupRating): String {
        val step = group.nextStep
        if (step != null) return nextStepLine(step)
        // No step. Either the top of the ladder, or the blend means no single lift can do it.
        if (group.tier == StrengthTier.ELITE) return AT_TOP_TIER
        val target = group.tier?.next?.displayName
        val lift = group.drivingLift
        return if (target != null && lift != null) {
            "$lift alone can't carry this group to $target — the other rated lifts here have to " +
                "come up too."
        } else {
            "No single lift moves this on its own right now."
        }
    }

    fun nextStepLine(step: NextStep): String {
        val routes = mutableListOf<String>()
        step.addedKg?.let { routes += weightRoute(it, step.isBodyWeightLift) }
        step.addedReps?.let { routes += repsRoute(it, step.currentWeightKg, step.isBodyWeightLift) }
        if (routes.isEmpty()) return "No single lift moves this on its own right now."
        val joined = routes.joinToString(", or ")
        // Two routes need the comma before "to reach"; one route reads better without it.
        val comma = if (routes.size > 1) "," else ""
        return "${step.liftName}: $joined$comma to reach ${step.targetTier.displayName}."
    }

    /** Added kilos are *added* load on a body-weight lift — belt or vest, not a bar. */
    private fun weightRoute(addedKg: Float, isBodyWeightLift: Boolean): String =
        if (isBodyWeightLift) "add ${kg(addedKg)} kg on a belt or vest"
        else "add ${kg(addedKg)} kg"

    private fun repsRoute(addedReps: Int, currentWeightKg: Float, isBodyWeightLift: Boolean): String {
        val reps = if (addedReps == 1) "1 more rep" else "$addedReps more reps"
        return when {
            isBodyWeightLift && currentWeightKg <= 0f -> "get $reps at body weight"
            isBodyWeightLift -> "get $reps with ${kg(currentWeightKg)} kg added"
            currentWeightKg > 0f -> "get $reps at ${kg(currentWeightKg)} kg"
            else -> "get $reps at your current load"
        }
    }

    // ── Unrated states — always shown, always explained ───────────────────────────────────────

    fun unratedLine(group: String, reason: UnratedReason?): String = when (reason) {
        UnratedReason.NO_SEX ->
            "Set your sex in Settings → Training. Strength standards differ by sex and there is no " +
                "honest default to assume."
        UnratedReason.NO_BODY_WEIGHT ->
            "Log a body weight in History → Body. These ratings are relative to what you weigh, so " +
                "there is no rating without it."
        UnratedReason.STALE_BODY_WEIGHT ->
            "Your last weigh-in is more than ${StrengthStandards.MAX_WEIGHIN_AGE_DAYS} days old, so " +
                "it can't describe your body weight now. Log a body weight in History → Body."
        UnratedReason.NO_QUALIFYING_LIFT -> qualifyingLiftHint(group)
        null -> "Not rated yet."
    }

    /**
     * What would unlock a group that has simply never had a qualifying lift logged. Names the
     * group's MAIN lift specifically — "log something for shoulders" is not actionable, "log an
     * overhead press" is.
     */
    fun qualifyingLiftHint(group: String): String {
        val main = StrengthStandards.mainLiftFor(group)
            ?: return "No rated lift covers $group."
        val all = StrengthStandards.liftsFor(group)
        val others = all.filter { it.id != main.id }.map { it.displayName }
        val sb = StringBuilder("Log ${withArticle(main.displayName)} to get a $group rating.")
        if (others.isNotEmpty()) {
            sb.append(" ${joinNames(others)} ${if (others.size == 1) "also counts" else "also count"}.")
        }
        // Core's lifts only rate with weight added — a bodyweight sit-up has no defensible load.
        val loaded = all.filter { it.requiresAddedLoad }.map { it.displayName }
        if (loaded.size == all.size) {
            sb.append(" These only count with weight added — bodyweight reps don't rate.")
        } else if (loaded.isNotEmpty()) {
            sb.append(" ${joinNames(loaded)} only ${if (loaded.size == 1) "counts" else "count"} " +
                "with weight added.")
        }
        return sb.toString()
    }

    // ── The weakest rated group — informational only ──────────────────────────────────────────

    fun weakestLine(weakestRatedGroup: String?): String? =
        weakestRatedGroup?.let { "$it is your weakest rated group right now." }

    /**
     * The app must never edit the user's priority muscles for them (the engine's own comment says
     * the same). This screen points; the user decides.
     */
    const val WEAKEST_NOTE =
        "Nothing has been changed for you. If you want your program to push this group harder, " +
            "add it to your priority muscles yourself."

    const val WEAKEST_CTA = "Open priority muscles"

    // ── The lifts that count ──────────────────────────────────────────────────────────────────

    /** One rated group's lifts, for the "lifts that count" section. */
    data class LiftSection(val group: String, val lines: List<String>)

    /** Every entry in [StrengthStandards.LIFTS], grouped and ordered like the rest of the screen. */
    fun liftSections(): List<LiftSection> = StrengthStandards.RATED_GROUPS.map { group ->
        LiftSection(
            group = group,
            lines = StrengthStandards.liftsFor(group)
                .sortedBy { if (it.role == StrengthStandards.LiftRole.MAIN) 0 else 1 }
                .map { liftLine(it) },
        )
    }

    fun liftLine(lift: StrengthStandards.QualifyingLift): String {
        val role = if (lift.role == StrengthStandards.LiftRole.MAIN) "main lift" else "accessory"
        val load = when {
            lift.requiresAddedLoad -> ", weight added"
            lift.bodyWeightFraction > 0f -> ", body weight + any added load"
            else -> ""
        }
        return "${lift.displayName} — $role$load"
    }

    const val LIFTS_HEADER = "Lifts that count"

    const val LIFTS_NOTE =
        "Only these move a rating, because only these have a population standard behind them. " +
            "Machines, cables, dumbbells, assisted variants and push-ups still log, still show in " +
            "history and stats, and still earn XP — they just don't rate. Sets above " +
            "${StrengthStandards.MAX_QUALIFYING_REPS} reps don't rate either."

    // ── The honest explainer ──────────────────────────────────────────────────────────────────

    const val EXPLAINER_HEADER = "How this works"

    val EXPLAINER: String =
        "A rating compares your best qualifying set from the last $WINDOW_MONTHS months against " +
            "strength standards for your body weight and your sex. It is not a running total, so " +
            "it can go down: it falls if $WINDOW_MONTHS months pass without a qualifying set, and " +
            "gaining body weight lowers it even when your lifts haven't changed. That is what " +
            "relative strength means, and it is working as intended."

    // ── Progress within a tier ────────────────────────────────────────────────────────────────

    /**
     * How far through the current tier a score sits, 0..100. `score` is continuous 0..5 and the
     * tier is `floor(score)`, so the fraction is the progress.
     *
     * Elite is special-cased to a full bar: a maxed-out score of exactly 5.0 has a zero fraction,
     * and an empty bar next to "Elite" would read as no progress at all.
     */
    fun tierProgressPercent(score: Float): Int = when {
        score >= 5f -> 100
        score <= 0f -> 0
        else -> ((score - floor(score)) * 100f).roundToInt().coerceIn(0, 100)
    }

    // ── Shared bits ───────────────────────────────────────────────────────────────────────────

    const val NOT_RATED = "Not rated"

    const val AT_TOP_TIER = "You're at the top tier here — Elite is as high as the scale goes."

    /** The rating window in whole months, so the copy tracks the engine's constant. */
    private val WINDOW_MONTHS: Long get() = StrengthStandards.WINDOW_DAYS / 30

    /** House weight format: integers bare, otherwise one decimal. */
    fun kg(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString()
        else String.format(Locale.ROOT, "%.1f", v)

    fun withArticle(name: String): String =
        if (name.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) "an $name"
        else "a $name"

    fun joinNames(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}
