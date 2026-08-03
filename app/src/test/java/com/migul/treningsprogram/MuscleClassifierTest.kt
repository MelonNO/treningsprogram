package com.migul.treningsprogram

import com.migul.treningsprogram.data.MuscleClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the write-time muscle-group fallback (fix F1). Before this, sets logged for a
 * swapped calisthenics progression variant or a custom "Add anyway" exercise stored
 * muscleGroup = "" and vanished from muscle-volume stats, the recap "muscles hit"
 * section, and muscle-based daily challenges, because resolution was DEFAULT_EXERCISES
 * exact-match only.
 */
class MuscleClassifierTest {

    @Test fun calisthenicsSwapTargets_resolveToARealGroup_notBlank() {
        // These are CalisthenicsProgressionMap targets that are NOT in DEFAULT_EXERCISES —
        // exactly the names that previously stored a blank muscle group.
        assertEquals("Chest", MuscleClassifier.fromName("Archer Push-Up"))
        assertEquals("Chest", MuscleClassifier.fromName("Diamond Push-Up"))
        assertEquals("Chest", MuscleClassifier.fromName("Ring Dip"))
        assertEquals("Legs", MuscleClassifier.fromName("Pistol Squat"))
        assertEquals("Legs", MuscleClassifier.fromName("Bulgarian Split Squat"))
        assertEquals("Back", MuscleClassifier.fromName("Inverted Row"))
        assertEquals("Back", MuscleClassifier.fromName("Australian Pull-Up"))
        assertEquals("Core", MuscleClassifier.fromName("Dragon Flag"))
        assertEquals("Core", MuscleClassifier.fromName("L-Sit"))
    }

    @Test fun legsCheckedBeforeBack_soRomanianDeadliftIsLegs() {
        // The "romanian" keyword is matched (Legs is checked before Back) so a Romanian
        // Deadlift is a hamstring move, not Back. Mirrors ProgramFragment.getMuscleGroup.
        assertEquals("Legs", MuscleClassifier.fromName("Romanian Deadlift"))
        assertEquals("Legs", MuscleClassifier.fromName("RDL"))
        // A plain deadlift still classifies as Back (consistent with the rest of the app).
        assertEquals("Back", MuscleClassifier.fromName("Deadlift"))
    }

    @Test fun cardioNames_resolveToCardio() {
        assertEquals("Cardio", MuscleClassifier.fromName("Easy Jog"))
        assertEquals("Cardio", MuscleClassifier.fromName("Interval Run"))
        assertEquals("Cardio", MuscleClassifier.fromName("Burpees"))
    }

    @Test fun commonStrengthNames_resolveSensibly() {
        assertEquals("Chest", MuscleClassifier.fromName("Bench Press"))
        assertEquals("Shoulders", MuscleClassifier.fromName("Overhead Press"))
        assertEquals("Arms", MuscleClassifier.fromName("Hammer Curl"))
        assertEquals("Back", MuscleClassifier.fromName("Lat Pulldown"))
    }

    @Test fun trulyUnknownName_returnsBlank_notASyntheticBucket() {
        // A blank result keeps the `WHERE muscleGroup != ''` stat filtering meaningful.
        // (NOTE: loaded carries — "Farmer's Carry", "Suitcase Carry", "Zercher Carry" — are now
        // classified as Core, so the unknown-name example uses a genuinely unclassifiable move.)
        assertEquals("", MuscleClassifier.fromName("Turkish Get-Up"))
        assertEquals("", MuscleClassifier.fromName("Foobar"))
    }

    // ── F3: badge classifiers (Log + Program screens) now delegate here ──────────

    @Test fun displayName_agreesWithStoredGroup_onTheExactSwapTargetsThatWereTheBug() {
        // Both the Log banner and Program badge now use displayName(); the muscle group
        // STORED on each set uses fromName(). For the swap/add targets that previously
        // mismatched (badge said one thing, stored value was blank/other), the badge label
        // must now equal the stored group — no cosmetic mismatch.
        val swapTargets = listOf(
            "Archer Push-Up", "Diamond Push-Up", "Ring Dip", "Pistol Squat",
            "Bulgarian Split Squat", "Inverted Row", "Australian Pull-Up",
            "Dragon Flag", "L-Sit", "Romanian Deadlift"
        )
        swapTargets.forEach { name ->
            assertEquals(
                "badge label must equal the stored muscle group for '$name'",
                MuscleClassifier.fromName(name),
                MuscleClassifier.displayName(name)
            )
        }
    }

    @Test fun displayName_showsTrainingForUnclassifiable_butStorageStaysBlank() {
        assertEquals("Training", MuscleClassifier.displayName("Turkish Get-Up"))
        assertEquals("", MuscleClassifier.fromName("Turkish Get-Up"))
    }

    // ── 2026-08 gen-science fixes: names AI plans generate that returned "" or misclassified ──

    @Test fun inclineDeclinePresses_resolveToChest() {
        // These contain neither "bench" nor "chest", so they fell through every rule and
        // stored "" — 12 of 14 live AI-generated plans contained at least one such name.
        assertEquals("Chest", MuscleClassifier.fromName("Incline Barbell Press"))
        assertEquals("Chest", MuscleClassifier.fromName("Incline Dumbbell Press"))
        assertEquals("Chest", MuscleClassifier.fromName("Decline Dumbbell Press"))
        // Already worked via "bench" — must keep doing so.
        assertEquals("Chest", MuscleClassifier.fromName("Incline Bench Press"))
        // Earlier specific rules still win over the new incline/decline+press rule.
        assertEquals("Shoulders", MuscleClassifier.fromName("Incline Shoulder Press"))
    }

    @Test fun landminePress_resolvesToShoulders() {
        // The app's overhead-press substitute for shoulder injuries.
        assertEquals("Shoulders", MuscleClassifier.fromName("Landmine Press"))
        assertEquals("Shoulders", MuscleClassifier.fromName("Half-Kneeling Landmine Press"))
    }

    @Test fun pullThrough_resolvesToLegs() {
        assertEquals("Legs", MuscleClassifier.fromName("Cable Pull-Through"))
        assertEquals("Legs", MuscleClassifier.fromName("Cable Pull Through"))
    }

    @Test fun pullover_resolvesToBack() {
        assertEquals("Back", MuscleClassifier.fromName("Straight-Arm Cable Pullover"))
        assertEquals("Back", MuscleClassifier.fromName("Dumbbell Pullover"))
    }

    @Test fun pullApart_resolvesToShoulders() {
        assertEquals("Shoulders", MuscleClassifier.fromName("Band Pull-Apart"))
        assertEquals("Shoulders", MuscleClassifier.fromName("Band Pull Apart"))
    }

    @Test fun crunchVariants_areCore_notCardio() {
        // Regression: the Cardio keyword "run" used to match INSIDE "crunch" ("c-run-ch"),
        // so every crunch classified as Cardio and the time estimator counted it as a
        // 30-minute cardio entry (live-demonstrated). "run" is now word-start-anchored.
        assertEquals("Core", MuscleClassifier.fromName("Cable Crunch"))
        assertEquals("Core", MuscleClassifier.fromName("Bicycle Crunch"))
        assertEquals("Core", MuscleClassifier.fromName("Crunch"))
    }

    @Test fun genuineRunNames_stillCardio_afterWordBoundaryFix() {
        assertEquals("Cardio", MuscleClassifier.fromName("Run"))
        assertEquals("Cardio", MuscleClassifier.fromName("Outdoor Run"))
        assertEquals("Cardio", MuscleClassifier.fromName("Interval Run"))
        assertEquals("Cardio", MuscleClassifier.fromName("Running"))
        assertEquals("Cardio", MuscleClassifier.fromName("Trail Run"))
    }

    @Test fun walkingLunge_staysLegs_andAnkleRehabStaysUngrouped() {
        assertEquals("Legs", MuscleClassifier.fromName("Walking Lunge"))
        // The intentional ""-classifications for ankle/foot rehab are untouched.
        assertEquals("", MuscleClassifier.fromName("Ankle Circles"))
        assertEquals("", MuscleClassifier.fromName("Toe Scrunch"))
        assertEquals("", MuscleClassifier.fromName("Ankle Alphabet"))
        assertEquals("", MuscleClassifier.fromName("Single-Leg Balance Hold"))
    }

    @Test fun finerMuscles_forTheNewNames_areNonEmptyAndTiered() {
        assertEquals(
            listOf("Chest" to 1.0f, "Front Delts" to 0.6f, "Triceps" to 0.6f),
            MuscleClassifier.finerMusclesFor("Incline Barbell Press")
        )
        assertEquals(
            listOf("Chest" to 1.0f, "Front Delts" to 0.6f, "Triceps" to 0.6f),
            MuscleClassifier.finerMusclesFor("Incline Dumbbell Press")
        )
        assertEquals(
            listOf("Chest" to 1.0f, "Triceps" to 0.6f, "Front Delts" to 0.3f),
            MuscleClassifier.finerMusclesFor("Decline Dumbbell Press")
        )
        assertEquals(
            listOf("Front Delts" to 1.0f, "Chest" to 0.6f, "Triceps" to 0.6f),
            MuscleClassifier.finerMusclesFor("Landmine Press")
        )
        assertEquals(
            listOf("Glutes" to 1.0f, "Hamstrings" to 0.6f, "Lower Back" to 0.6f),
            MuscleClassifier.finerMusclesFor("Cable Pull-Through")
        )
        assertEquals(
            listOf("Upper Back" to 1.0f, "Chest" to 0.3f, "Triceps" to 0.3f),
            MuscleClassifier.finerMusclesFor("Dumbbell Pullover")
        )
        // Pull-apart: Rear Delts primary — the finer primary's broad group (Shoulders)
        // matches fromName, keeping broad/finer consistent via broadGroupFor.
        assertEquals(
            listOf("Rear Delts" to 1.0f, "Upper Back" to 0.6f),
            MuscleClassifier.finerMusclesFor("Band Pull-Apart")
        )
        assertEquals(
            MuscleClassifier.fromName("Band Pull-Apart"),
            MuscleClassifier.broadGroupFor(MuscleClassifier.finerMusclesFor("Band Pull-Apart").first().first)
        )
    }

    @Test fun finerMuscles_crunchIsCore_runIsCardio() {
        assertEquals(listOf("Core" to 1.0f), MuscleClassifier.finerMusclesFor("Cable Crunch"))
        assertEquals(listOf("Cardio" to 1.0f), MuscleClassifier.finerMusclesFor("Outdoor Run"))
    }

    @Test fun colorFor_returnsCanonicalColors_andRespectsPerScreenFallback() {
        assertEquals("#E91E63", MuscleClassifier.colorFor("Chest", fallbackColor = "#000000"))
        assertEquals("#4CAF50", MuscleClassifier.colorFor("Legs", fallbackColor = "#000000"))
        // Unknown / "Training" group keeps the caller's own neutral colour
        // (Program uses #607D8B, Log uses #7C67F5).
        assertEquals("#607D8B", MuscleClassifier.colorFor("Training", fallbackColor = "#607D8B"))
        assertEquals("#7C67F5", MuscleClassifier.colorFor("", fallbackColor = "#7C67F5"))
    }
}
