package com.migul.treningsprogram.ui.log

import com.migul.treningsprogram.domain.RestTimePolicy
import kotlin.math.floor

/**
 * B1 — the warm-up ladder suggestion for heavy compounds.
 *
 * Policy (A-W1): ≈40% × 5, 60% × 3, 80% × 2 of TODAY's working weight (A-W3 — the weight
 * currently pre-filled/typed for the first working set), each step rounded DOWN to a weight
 * actually loadable on the ACTIVE gym preset ([PlateMath.PlateProfile]):
 *  - barbell lifts decompose against the profile's bar + plate pairs; a step that lands below
 *    the empty bar is SKIPPED (A-W1);
 *  - plate-loaded dumbbells decompose against the handle weight; fixed dumbbells round to a
 *    generic 2.5 kg grid;
 *  - other loaded compounds (machines) round to the generic 2.5 kg grid.
 * Steps that collapse to the same weight (very light working weights) are merged, so light
 * days produce a shorter ladder or none.
 *
 * Applicability (A-W2): the SAME heavy-compound classification the rest-time categories use
 * ([RestTimePolicy.isHeavyCompound]) — the two features agree on what "heavy" means — plus a
 * real working weight (> 0). Isolation, cardio and bodyweight work get no suggestion.
 *
 * Pure: the caller logs accepted steps as ordinary warm-up sets; nothing here touches state.
 */
object WarmupRamp {

    data class Step(val weightKg: Float, val reps: Int)

    /** A-W1 ladder: (fraction of working weight, reps). */
    private val LADDER = listOf(0.40f to 5, 0.60f to 3, 0.80f to 2)

    /** Rounding grid for loads that aren't plate-decomposable (machines, fixed dumbbells). */
    private const val GENERIC_INCREMENT_KG = 2.5f

    fun isApplicable(exerciseName: String, workingWeightKg: Float): Boolean =
        workingWeightKg > 0f && RestTimePolicy.isHeavyCompound(exerciseName)

    /** The offered ladder — empty when not applicable or every step rounds away. */
    fun stepsFor(
        exerciseName: String,
        workingWeightKg: Float,
        profile: PlateMath.PlateProfile
    ): List<Step> {
        if (!isApplicable(exerciseName, workingWeightKg)) return emptyList()
        val steps = mutableListOf<Step>()
        for ((fraction, reps) in LADDER) {
            val rounded = roundLoadable(workingWeightKg * fraction, exerciseName, profile) ?: continue
            if (rounded >= workingWeightKg) continue        // never "warm up" at/above the work weight
            if (steps.any { it.weightKg == rounded }) continue  // collapse duplicate steps
            steps.add(Step(rounded, reps))
        }
        return steps
    }

    /** Nearest actually-loadable weight at or below [target], or null when the step is unloadable. */
    private fun roundLoadable(
        target: Float,
        exerciseName: String,
        profile: PlateMath.PlateProfile
    ): Float? = when {
        PlateMath.isDumbbellExercise(exerciseName) ->
            if (profile.loadableDumbbells)
                PlateMath.perSide(target, profile.dumbbellBarKg, profile.plates)?.achievableTotal
            else roundDownToGrid(target)
        PlateMath.isBarbellExercise(exerciseName) ->
            // null = target below the empty bar → the step is skipped (A-W1).
            PlateMath.perSide(target, profile.barKg, profile.plates)?.achievableTotal
        else -> roundDownToGrid(target)
    }?.takeIf { it > 0f }

    private fun roundDownToGrid(target: Float): Float =
        floor(target / GENERIC_INCREMENT_KG).toInt() * GENERIC_INCREMENT_KG
}
