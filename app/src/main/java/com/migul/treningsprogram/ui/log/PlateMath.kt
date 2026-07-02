package com.migul.treningsprogram.ui.log

/**
 * F4 — pure plate-loading math behind the keypad's live "per side" readout.
 *
 * Assumes a standard 20 kg bar and metric plate pairs (25/20/15/10/5/2.5/1.25).
 * Greedy decomposition per side; when the target isn't reachable exactly with
 * standard plates the readout is prefixed with "≈" and shows the closest
 * loadable weight below the target. Pure so it is fully unit-testable off-device.
 */
object PlateMath {

    const val BAR_KG = 20f
    private val PLATES_KG = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f)
    private const val EPS = 0.001f

    /** Words that mean "not a barbell" even when the lift name matches a barbell hint. */
    private val NON_BARBELL = listOf(
        "dumbbell", "db ", "machine", "cable", "smith", "kettlebell", "goblet",
        "band", "bodyweight", "bulgarian", "pistol", "trap bar", "hex bar", "landmine",
    )

    /** Lifts that are barbell-loaded by default when nothing above vetoes it. */
    private val BARBELL_HINTS = listOf(
        "barbell", "deadlift", "squat", "bench press", "overhead press", "ohp",
        "military press", "bent-over row", "bent over row", "pendlay", "hip thrust",
        "power clean", "clean and", "snatch", "romanian", "rdl", "good morning",
    )

    fun isBarbellExercise(name: String): Boolean {
        val n = name.lowercase()
        if (n.isBlank()) return false
        if (NON_BARBELL.any { it in n }) return false
        return BARBELL_HINTS.any { it in n }
    }

    data class Loadout(val perSide: List<Float>, val exact: Boolean, val achievableTotal: Float)

    /** Greedy per-side decomposition, or null when the total doesn't reach the bar. */
    fun perSide(totalKg: Float, barKg: Float = BAR_KG): Loadout? {
        if (totalKg < barKg - EPS) return null
        var side = (totalKg - barKg) / 2f
        val plates = mutableListOf<Float>()
        for (p in PLATES_KG) {
            while (side >= p - EPS) {
                plates.add(p)
                side -= p
            }
        }
        return Loadout(
            perSide = plates,
            exact = side < EPS,
            achievableTotal = barKg + plates.sum() * 2f,
        )
    }

    /**
     * The keypad readout line, or null when it shouldn't be shown (non-barbell
     * exercise, or weight below the bar).
     */
    fun display(totalKg: Float, exerciseName: String): String? {
        if (!isBarbellExercise(exerciseName)) return null
        val l = perSide(totalKg) ?: return null
        if (l.perSide.isEmpty()) {
            return if (l.exact) "Empty bar (${fmt(BAR_KG)} kg)" else null
        }
        val plates = l.perSide.joinToString(" + ") { fmt(it) }
        return if (l.exact) "$plates per side"
        else "≈ $plates per side (${fmt(l.achievableTotal)} kg)"
    }

    private fun fmt(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()
}
