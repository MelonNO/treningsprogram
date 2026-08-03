package com.migul.treningsprogram.ui.log

/**
 * Flagged-info batch 2026-08 item 01 — which sections of [ExerciseInfoBottomSheet] are visible.
 *
 * Once the user flags "this database info doesn't match this exercise" (v1.30.0's
 * ExerciseInfoCorrections flags), the mismatched content must stop showing — from EVERY entry
 * point that opens the sheet (active workout, Program, History). Hidden = everything sourced
 * from the matched entry: pictures, instructions, and the metadata lines (muscles, equipment,
 * level/category, "From database: …").
 *
 * Deliberately NOT governed here (always shown, they are not sourced from the flagged match):
 * the exercise name (title), the AI "Coach's note", the History performed-sets section, and the
 * flag/flagged row itself.
 *
 * D2 (intake decision): the name-keyed static-catalog fallback is hidden for a flagged exercise
 * too — the user flagged "wrong pictures/instructions", and a wrong static entry would repeat
 * the offense. The generic-tips fallback likewise: a flagged sheet shows only the hidden-notice
 * in the content area.
 *
 * Pure and stateless so the JVM unit tests exercise exactly what the fragment renders.
 */
object FlaggedExerciseInfoPolicy {

    data class Sections(
        /** The image block (DB frame cycler or static URL image). */
        val images: Boolean,
        /** The matched DB entry's details: "From database" label, metadata lines, instructions. */
        val dbDetails: Boolean,
        /** The name-keyed static-catalog fallback details. */
        val staticDetails: Boolean,
        /** The generic "control the eccentric…" tips shown when nothing matched. */
        val genericFallback: Boolean,
        /** The "info hidden because you flagged this match" explanation line. */
        val hiddenNotice: Boolean
    )

    fun sectionsFor(
        flagged: Boolean,
        hasDbEntry: Boolean,
        hasStaticEntry: Boolean,
        hasImageSource: Boolean
    ): Sections {
        if (flagged) {
            return Sections(
                images = false,
                dbDetails = false,
                staticDetails = false,
                genericFallback = false,
                hiddenNotice = true
            )
        }
        return Sections(
            images = hasImageSource,
            dbDetails = hasDbEntry,
            staticDetails = !hasDbEntry && hasStaticEntry,
            genericFallback = !hasDbEntry && !hasStaticEntry,
            hiddenNotice = false
        )
    }
}
