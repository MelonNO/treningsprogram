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
        assertEquals("", MuscleClassifier.fromName("Zercher Carry"))
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
        assertEquals("Training", MuscleClassifier.displayName("Zercher Carry"))
        assertEquals("", MuscleClassifier.fromName("Zercher Carry"))
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
