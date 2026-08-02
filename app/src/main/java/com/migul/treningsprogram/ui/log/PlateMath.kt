package com.migul.treningsprogram.ui.log

import com.migul.treningsprogram.data.db.entity.GymPreset

/**
 * F4 — pure plate-loading math behind the keypad's live "per side" readout.
 *
 * Which bar/plates apply comes from the ACTIVE GYM PRESET via [PlateProfile]; the app-wide
 * default (no preset, or preset fields left blank) is the user's 50 mm home setup: a 7 kg bar,
 * a 20/15/10/5/2/1.45/1.25/1/0.5 kg plate set, and plate-loaded (not fixed) dumbbells — so the
 * readout also applies to dumbbell lifts there, using the dumbbell-handle weight.
 *
 * Greedy decomposition per side; when the target isn't reachable exactly with the available
 * plates the readout is prefixed with "≈" and shows the closest loadable weight below the
 * target. Pure so it is fully unit-testable off-device.
 */
object PlateMath {

    /** Resolved equipment profile the calculator works against. */
    data class PlateProfile(
        val barKg: Float,
        val dumbbellBarKg: Float,
        /** Available plate sizes per PAIR, any order; blank/invalid entries dropped. */
        val plates: List<Float>,
        /** True when dumbbells are plate-loaded handles (home style), not fixed gym dumbbells. */
        val loadableDumbbells: Boolean,
    ) {
        companion object {
            /** The user's 50 mm home setup — the app-wide default. */
            val DEFAULT = PlateProfile(
                barKg = 7f,
                dumbbellBarKg = 2f,
                plates = listOf(20f, 15f, 10f, 5f, 2f, 1.45f, 1.25f, 1f, 0.5f),
                loadableDumbbells = true,
            )

            /** Resolve a preset's profile; null preset or null fields fall back to [DEFAULT]. */
            fun from(preset: GymPreset?): PlateProfile = PlateProfile(
                barKg = preset?.barWeightKg ?: DEFAULT.barKg,
                dumbbellBarKg = preset?.dumbbellBarWeightKg ?: DEFAULT.dumbbellBarKg,
                plates = preset?.platesCsv?.let(::parsePlates)?.takeIf { it.isNotEmpty() }
                    ?: DEFAULT.plates,
                loadableDumbbells = preset?.loadableDumbbells ?: DEFAULT.loadableDumbbells,
            )

            /**
             * Parse a user-typed plate list. Separators: comma/semicolon/slash/whitespace.
             * When the string mixes commas WITH dots, commas are separators and dots decimals;
             * when only commas appear they are treated as separators (so "20,10,5" works) —
             * decimals should be typed with a dot ("1.25").
             */
            fun parsePlates(csv: String): List<Float> =
                csv.split(',', ';', '/', ' ', '\n')
                    .mapNotNull { it.trim().toFloatOrNull() }
                    .filter { it > 0f }
                    .sortedDescending()
        }
    }

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

    /** Explicitly-dumbbell lifts — plate math applies only when the profile has loadable handles. */
    fun isDumbbellExercise(name: String): Boolean {
        val n = name.lowercase()
        return "dumbbell" in n || n.startsWith("db ") || " db " in n
    }

    /**
     * QoL 2026-08 item 01: equipment words that veto the DB-equipment dumbbell rescue. When the
     * plan name itself names other equipment, a (possibly loose) dumbbell DB match must NOT force
     * the lift into dumbbell math.
     */
    private val DUMBBELL_RESCUE_VETO = listOf(
        "barbell", "cable", "machine", "smith", "kettlebell", "band", "bodyweight",
        "trap bar", "hex bar", "landmine", "ez bar", "ez-bar", "e-z",
    )

    /**
     * QoL 2026-08 item 01 — general dumbbell-by-nature recognition. A lift whose plan name lacks
     * "dumbbell"/"DB" (e.g. "Zottman Curl", "Hammer Curl", "Lateral Raise") still gets the
     * dumbbell readout when its resolved exercise-DB entry says its equipment IS a dumbbell.
     * The name-based checks keep precedence (no regressions), and the rescue is vetoed when the
     * name explicitly mentions other equipment (a loose DB match must not misclassify).
     */
    fun isDumbbellByNature(name: String, dbEquipment: String?): Boolean {
        if (isDumbbellExercise(name)) return true
        if (isBarbellExercise(name)) return false
        if (!"dumbbell".equals(dbEquipment?.trim(), ignoreCase = true)) return false
        val n = name.lowercase()
        return DUMBBELL_RESCUE_VETO.none { it in n }
    }

    data class Loadout(val perSide: List<Float>, val exact: Boolean, val achievableTotal: Float)

    /** Greedy per-side decomposition, or null when the total doesn't reach the bar. */
    fun perSide(totalKg: Float, barKg: Float, plates: List<Float>): Loadout? {
        if (totalKg < barKg - EPS) return null
        var side = (totalKg - barKg) / 2f
        val loaded = mutableListOf<Float>()
        for (p in plates.sortedDescending()) {
            while (side >= p - EPS) {
                loaded.add(p)
                side -= p
            }
        }
        return Loadout(
            perSide = loaded,
            exact = side < EPS,
            achievableTotal = barKg + loaded.sum() * 2f,
        )
    }

    /**
     * The keypad readout line, or null when it shouldn't be shown (exercise isn't plate-loaded
     * under [profile], or the weight doesn't reach the bar). For dumbbell lifts the entered
     * weight is ONE dumbbell's total, decomposed against the handle weight.
     *
     * [dbEquipment] is the resolved exercise-DB entry's equipment string (nullable; callers with
     * no resolved entry pass null and get the pre-existing name-only behaviour). It lets
     * dumbbell-by-nature lifts whose plan name lacks "dumbbell"/"DB" (item 01) get the readout.
     */
    fun display(
        totalKg: Float,
        exerciseName: String,
        profile: PlateProfile = PlateProfile.DEFAULT,
        dbEquipment: String? = null,
    ): String? {
        val (barKg, suffix) = when {
            isDumbbellExercise(exerciseName) ->
                if (profile.loadableDumbbells) profile.dumbbellBarKg to " (dumbbell)" else return null
            isBarbellExercise(exerciseName) -> profile.barKg to ""
            isDumbbellByNature(exerciseName, dbEquipment) ->
                if (profile.loadableDumbbells) profile.dumbbellBarKg to " (dumbbell)" else return null
            else -> return null
        }
        val l = perSide(totalKg, barKg, profile.plates) ?: return null
        if (l.perSide.isEmpty()) {
            return if (l.exact) "Empty bar (${fmt(barKg)} kg)$suffix" else null
        }
        val plates = l.perSide.joinToString(" + ") { fmt(it) }
        return if (l.exact) "$plates per side$suffix"
        else "≈ $plates per side (${fmt(l.achievableTotal)} kg)$suffix"
    }

    private fun fmt(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()
}
