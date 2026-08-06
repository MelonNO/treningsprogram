package com.migul.treningsprogram.domain

import com.migul.treningsprogram.ui.log.PlateMath
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cluster A / brief 01 — what the weight field's external **−** and **+** buttons do.
 *
 * The old behaviour was a flat 2.5 kg for every exercise at every gym. This replaces it with a
 * step that is **size-scaled** and, wherever the app actually knows the inventory, **guaranteed
 * loadable at the ACTIVE gym**:
 *
 *  1. **It aims at a size-scaled weight.** [STEP_FRACTION] of the current load, clamped to
 *     [MIN_STEP_KG]…[MAX_STEP_KG]. A 100 kg squat aims 5 kg away, an 8 kg lateral raise 1 kg.
 *     Deliberately arithmetic, not a list of exercise names — the app's programs are full of
 *     AI-generated names no fixed list would cover. The [MIN_STEP_KG] floor is what makes
 *     "a bench press is never offered a 0.5 kg step, even at a gym that stocks 0.5 kg plates" a
 *     property of the rule rather than a special case.
 *  2. **It lands on a weight the gym can genuinely make.** Of the loadable weights past the one
 *     you are on, it takes **the next one** — unless the aim clears more than one, in which case
 *     it takes the one nearest the aim. Skipping the *only* rung inside the aim would double the
 *     intended step; skipping nothing when a gym stocks 0.1 kg increments would neuter the button.
 *
 * The same rule covers an odd hand-typed value with no special case: from 61 kg at a gym whose
 * rungs are 60 / 62.5 / 65, the aim (≈64) clears only 62.5, so **+** lands there and **−** lands
 * on 60 — the next loadable weight either side, never 61 ± the step, which nothing could load.
 *
 * "Loadable" is defined as *what [PlateMath.perSide] can decompose exactly* — the very same call
 * the per-side readout under the field renders. That is what keeps brief 01 and brief 02 in
 * agreement: every weight these buttons produce is a weight the readout shows an **exact**
 * breakdown for (never the "≈ … (59.9 kg)" form).
 *
 * Where the app has no inventory it says so instead of pretending: fixed (non-plate-loaded)
 * dumbbells step on a [FIXED_DUMBBELL_INCREMENT_KG] grid and machines/cables/anything unclassified
 * on a [GENERIC_INCREMENT_KG] grid. No gym-setup UI was added for this — explicitly ruled out.
 *
 * Pure (Android-free, Room-free) so the whole rule is unit-testable off-device.
 */
object WeightStep {

    /** The step aims for this fraction of the current load before snapping to what's loadable. */
    private const val STEP_FRACTION = 0.05f

    /**
     * Floor on the aimed-at step, and the smallest move that may be made just because it is the
     * next rung: a gym stocking 0.5 kg plates cannot talk a heavy lift into a 0.5 kg step.
     */
    const val MIN_STEP_KG = 1f

    /** Ceiling on the aimed-at step, so a 200 kg deadlift doesn't jump 10 kg. */
    const val MAX_STEP_KG = 5f

    /** Fallback grid for fixed (non-plate-loaded) dumbbell racks — no inventory is known. */
    const val FIXED_DUMBBELL_INCREMENT_KG = 2f

    /** Fallback grid for machines, cables and anything unclassified — no inventory is known. */
    const val GENERIC_INCREMENT_KG = 2.5f

    private const val EPS = 0.002f

    /** Safety bound on the loadable-weight scan; real plate sets resolve within a few steps. */
    private const val MAX_SCAN_STEPS = 1000

    /** The size-scaled step the buttons aim for at [loadKg], before snapping to the gym's grid. */
    fun idealStepKg(loadKg: Float): Float =
        (loadKg.coerceAtLeast(0f) * STEP_FRACTION).coerceIn(MIN_STEP_KG, MAX_STEP_KG)

    /**
     * The weight the **+** ([up] = true) or **−** ([up] = false) button should put in the field.
     *
     * [profile] is the ACTIVE gym's equipment; `null` means it hasn't been resolved yet, in which
     * case the generic grids are used rather than guessing at another gym's plates. [dbEquipment]
     * is the resolved exercise-DB entry's equipment string, which is what lets dumbbell-by-nature
     * lifts ("Lateral Raise", "Zottman Curl") get dumbbell treatment — exactly as
     * [PlateMath.display] does, so the two can never disagree.
     *
     * Never returns a negative weight; **−** floors at 0 (a blank/bodyweight field is 0 kg).
     */
    fun next(
        currentKg: Float,
        up: Boolean,
        exerciseName: String,
        profile: PlateMath.PlateProfile?,
        dbEquipment: String? = null,
    ): Float {
        val cur = currentKg.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        val grid = gridFor(exerciseName, profile, dbEquipment)
        val ideal = idealStepKg(cur)
        val target = if (up) cur + ideal else cur - ideal

        // The very next loadable weight in the pressed direction. Nothing there (an empty bar
        // pressed down) means the only honest answer is the zero floor.
        val nextRung = (if (up) grid.firstStrictlyAbove(cur) else grid.lastStrictlyBelow(cur))
            ?: return (if (up) cur + ideal else 0f).coerceAtLeast(0f)

        // `inner` = the furthest loadable weight the aim does NOT pass; `outer` = the first one it
        // reaches or passes. `inner` is null when the aim doesn't even clear the next rung.
        val inner =
            if (up) grid.lastAtOrBelow(target)?.takeIf { it > cur + EPS }
            else grid.firstAtOrAbove(target)?.takeIf { it < cur - EPS }
        val outer = if (up) grid.firstAtOrAbove(target) else grid.lastAtOrBelow(target)

        // The aim clears no rung, or exactly one — that rung IS the step. (The MIN_STEP_KG guard
        // stops a gym with very fine increments from offering a heavy lift a token move; such a
        // press falls through to the nearest-to-aim choice below.)
        if (inner == null) return nextRung.coerceAtLeast(0f)
        if (abs(inner - nextRung) < EPS && abs(nextRung - cur) >= MIN_STEP_KG - EPS) {
            return nextRung.coerceAtLeast(0f)
        }

        // Several rungs are in reach: take the one nearest the aim, gentler first so an exact tie
        // resolves to the smaller step.
        val ordered = listOfNotNull(inner, outer)
            .filter { if (up) it > cur + EPS else (it < cur - EPS && it >= -EPS) }
        val chosen = ordered.minByOrNull { abs(it - target) } ?: nextRung
        return chosen.coerceAtLeast(0f)
    }

    /**
     * True when [weightKg] is genuinely achievable for this exercise at this gym — i.e. when the
     * per-side readout can show it exactly. Always true on the generic grids' own multiples;
     * exposed so callers (and tests) can assert the guarantee.
     */
    fun isLoadable(
        weightKg: Float,
        exerciseName: String,
        profile: PlateMath.PlateProfile?,
        dbEquipment: String? = null,
    ): Boolean = gridFor(exerciseName, profile, dbEquipment).isLoadable(weightKg)

    // ── grids ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Mirrors [PlateMath.display]'s equipment branching exactly (explicit dumbbell name → barbell
     * name → dumbbell-by-nature → everything else), so the buttons and the readout always agree
     * about which bar/handle a lift loads onto.
     */
    private fun gridFor(
        exerciseName: String,
        profile: PlateMath.PlateProfile?,
        dbEquipment: String?,
    ): Grid = when {
        PlateMath.isDumbbellExercise(exerciseName) -> dumbbellGrid(profile)
        PlateMath.isBarbellExercise(exerciseName) ->
            plateGrid(profile?.barKg, profile?.plates) ?: UniformGrid(GENERIC_INCREMENT_KG)
        PlateMath.isDumbbellByNature(exerciseName, dbEquipment) -> dumbbellGrid(profile)
        else -> UniformGrid(GENERIC_INCREMENT_KG)
    }

    private fun dumbbellGrid(profile: PlateMath.PlateProfile?): Grid {
        if (profile == null || !profile.loadableDumbbells) return UniformGrid(FIXED_DUMBBELL_INCREMENT_KG)
        return plateGrid(profile.dumbbellBarKg, profile.plates)
            ?: UniformGrid(FIXED_DUMBBELL_INCREMENT_KG)
    }

    private fun plateGrid(barKg: Float?, plates: List<Float>?): Grid? {
        if (barKg == null || plates == null) return null
        val plateUnits = plates.filter { it > 0f }.map { (it * 100f).roundToInt() }.filter { it > 0 }
        if (plateUnits.isEmpty()) return null
        // Every achievable total is bar + 2 × (a sum of plates), so the finest possible spacing is
        // twice the greatest common divisor of the plate sizes.
        val latticeUnits = 2 * plateUnits.reduce(::gcd)
        return PlateGrid((barKg * 100f).roundToInt(), latticeUnits, barKg, plates)
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private interface Grid {
        fun isLoadable(weightKg: Float): Boolean
        /** The smallest weight on this grid that is ≥ [weightKg], or null when there is none. */
        fun firstAtOrAbove(weightKg: Float): Float?
        /** The largest weight on this grid that is ≤ [weightKg], or null when there is none. */
        fun lastAtOrBelow(weightKg: Float): Float?
        /** The next weight on this grid strictly above [weightKg]. */
        fun firstStrictlyAbove(weightKg: Float): Float?
        /** The next weight on this grid strictly below [weightKg]. */
        fun lastStrictlyBelow(weightKg: Float): Float?
    }

    /** Plain multiples of [incrementKg] from 0 up — used where the app has no inventory. */
    private class UniformGrid(incrementKg: Float) : Grid {

        private val stepUnits = (incrementKg * 100f).roundToInt().coerceAtLeast(1)

        override fun isLoadable(weightKg: Float): Boolean =
            weightKg >= -EPS && units(weightKg) % stepUnits == 0

        override fun firstAtOrAbove(weightKg: Float): Float =
            at(ceilDiv(units(weightKg), stepUnits))

        override fun lastAtOrBelow(weightKg: Float): Float? =
            floorDiv(units(weightKg), stepUnits).takeIf { it >= 0 }?.let(::at)

        override fun firstStrictlyAbove(weightKg: Float): Float =
            at(floorDiv(units(weightKg), stepUnits) + 1)

        override fun lastStrictlyBelow(weightKg: Float): Float? =
            (ceilDiv(units(weightKg), stepUnits) - 1).takeIf { it >= 0 }?.let(::at)

        private fun at(k: Int): Float = (k.coerceAtLeast(0) * stepUnits) / 100f
    }

    /**
     * The ACTIVE gym's genuinely loadable weights for one bar/handle: the empty bar plus anything
     * [PlateMath.perSide] decomposes **exactly** from that gym's plate pairs. Candidates are walked
     * on the plate set's own lattice (twice the plates' gcd), so nothing achievable is skipped.
     */
    private class PlateGrid(
        private val barUnits: Int,
        private val latticeUnits: Int,
        private val barKg: Float,
        private val plates: List<Float>,
    ) : Grid {

        override fun isLoadable(weightKg: Float): Boolean =
            PlateMath.perSide(weightKg, barKg, plates)?.exact == true

        override fun firstAtOrAbove(weightKg: Float): Float? =
            scanUp(ceilDiv(units(weightKg) - barUnits, latticeUnits))

        override fun lastAtOrBelow(weightKg: Float): Float? =
            scanDown(floorDiv(units(weightKg) - barUnits, latticeUnits))

        override fun firstStrictlyAbove(weightKg: Float): Float? =
            scanUp(floorDiv(units(weightKg) - barUnits, latticeUnits) + 1)

        override fun lastStrictlyBelow(weightKg: Float): Float? =
            scanDown(ceilDiv(units(weightKg) - barUnits, latticeUnits) - 1)

        private fun scanUp(fromK: Int): Float? {
            var k = fromK.coerceAtLeast(0)
            repeat(MAX_SCAN_STEPS) {
                val w = weightAt(k)
                if (isLoadable(w)) return w
                k++
            }
            return null
        }

        private fun scanDown(fromK: Int): Float? {
            var k = fromK
            repeat(MAX_SCAN_STEPS) {
                if (k < 0) return null
                val w = weightAt(k)
                if (isLoadable(w)) return w
                k--
            }
            return null
        }

        private fun weightAt(k: Int): Float = (barUnits + k * latticeUnits) / 100f
    }

    private fun units(weightKg: Float): Int = (weightKg * 100f).roundToInt()

    private fun floorDiv(a: Int, b: Int): Int = Math.floorDiv(a, b)

    private fun ceilDiv(a: Int, b: Int): Int = -Math.floorDiv(-a, b)
}
