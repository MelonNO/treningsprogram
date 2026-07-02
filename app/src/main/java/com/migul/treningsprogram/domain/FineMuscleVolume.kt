package com.migul.treningsprogram.domain

import kotlin.math.roundToInt

/**
 * Stage-3 item 9 — the Recap "Muscles hit this session" card at FINE granularity
 * (Triceps, not Arms), using the same taxonomy and weighting philosophy as the Home
 * recovery panel (MuscleClassifier.finerMusclesFor: primary 1.0 / major 0.6 / minor 0.3).
 *
 * Pure: the resolver is injected so the derivation is unit-testable off-device; production
 * passes MuscleClassifier::finerMusclesFor — one taxonomy, no contradictions between screens.
 */
object FineMuscleVolume {

    /**
     * @param exerciseSets working-set count per exercise in the session (warm-ups excluded
     *   upstream), in first-appearance order.
     * @param resolve exercise name -> list of (fine label, weight 0..1).
     * @return (fine muscle, whole sets) rows, weighted contributions summed per label and
     *   rounded to whole sets (A-09a: readable, not fractional). Labels rounding to 0 are
     *   dropped; sorted by sets descending.
     */
    fun rows(
        exerciseSets: List<Pair<String, Int>>,
        resolve: (String) -> List<Pair<String, Float>>
    ): List<Pair<String, Int>> {
        val acc = LinkedHashMap<String, Float>()
        exerciseSets.forEach { (name, sets) ->
            if (sets <= 0) return@forEach
            resolve(name).forEach { (label, weight) ->
                acc[label] = (acc[label] ?: 0f) + sets * weight
            }
        }
        return acc.entries
            .map { it.key to it.value.roundToInt() }
            .filter { it.second >= 1 }
            .sortedByDescending { it.second }
    }
}
