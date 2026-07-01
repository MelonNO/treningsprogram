package com.migul.treningsprogram

import com.migul.treningsprogram.data.ExerciseCatalog
import com.migul.treningsprogram.data.ExerciseDbResolver
import com.migul.treningsprogram.data.ExerciseResolutionLog
import com.migul.treningsprogram.data.MuscleClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode

/**
 * R2 — recognition of verbose, AI-generated exercise names (v1.16.0).
 *
 * A user harvested ~130 names that the app FAILED to recognise (they piled up in the Settings
 * "unrecognized exercises" list). "Recognised" in this codebase = [ExerciseDbResolver.resolve]
 * returns a non-null library match; a resolver miss is exactly what feeds that Settings list. This
 * test locks in that:
 *   1. EVERY harvested name now resolves to a real library entry (nothing left "unrecognized").
 *   2. Each name also carries a SENSIBLE muscle group — either a canonical group, or intentionally
 *      "" for pure ankle/foot mobility & balance drills (excluded from volume, but still resolved).
 *   3. The fix is PATTERN-level, not 130 hard-coded strings: brand-new verbose names in the same
 *      movement families also resolve + classify correctly.
 *
 * Robolectric loads the real exercise DB asset (Conscrypt OFF — its native lacks an aarch64 build
 * on this Pi host; the resolver needs no TLS). See [R1ExerciseRecognitionTest] for the classifier
 * unit-level guards and [R1BackfillMigrationTest] for the data-safe re-derivation of stored groups.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class R2VerboseExerciseRecognitionTest {

    private val ctx = RuntimeEnvironment.getApplication()
    private val resolver: ExerciseDbResolver by lazy {
        ExerciseCatalog.initialize(ctx)
        ExerciseDbResolver(ExerciseResolutionLog(ctx))
    }

    private fun harvestedNames(): List<String> =
        javaClass.classLoader!!.getResourceAsStream("unrecognized-exercises.txt")!!
            .bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    @Test fun everyHarvestedName_nowResolvesToARealLibraryEntry() {
        val names = harvestedNames()
        assertEquals("expected the full harvested list", 128, names.size)

        val stillUnrecognized = names.filter { resolver.resolve(it) == null }
        assertTrue(
            "these names still fail to resolve (would still show as 'unrecognized'):\n" +
                stillUnrecognized.joinToString("\n"),
            stillUnrecognized.isEmpty()
        )

        // Every match points at a REAL catalog entry (not a dangling id).
        names.forEach { name ->
            val r = resolver.resolve(name)!!
            assertTrue(
                "resolve('$name') → ${r.dbId} which is not a real catalog entry",
                ExerciseCatalog.byId.containsKey(r.dbId)
            )
        }
    }

    @Test fun everyHarvestedName_isRecognized_matchedAndOrClassified() {
        harvestedNames().forEach { name ->
            val resolved = resolver.resolve(name) != null
            val classified = MuscleClassifier.fromName(name).isNotEmpty()
            assertTrue(
                "'$name' is neither resolved nor classified — would be unrecognized",
                resolved || classified
            )
        }
    }

    /** Representative names → the muscle group they MUST classify to (correctness, not just non-blank). */
    @Test fun representativeNames_classifyToTheCorrectGroup() {
        val cases = mapOf(
            // verbose lifts whose group used to be hijacked by an incidental "on Bench"/"at Chest"
            "Dumbbell Seated Shoulder Press (Seated on Bench, Neutral Grip)" to "Shoulders",
            "Dumbbell Overhead Press (Seated on Bench, Dumbbells — Low Ceiling Safe)" to "Shoulders",
            "Dumbbell Seated Arnold Press (Seated on Bench, Full ROM)" to "Shoulders",
            "Dumbbell Z-Press (Seated Floor)" to "Shoulders",
            "Single-Leg Calf Raise with Hand Support on Bench (Ankle Rehab — Bodyweight)" to "Legs",
            "Dumbbell Split Squat (Front-Foot Elevated, Both Hands Fixed on Bench for Support — Bilateral Stance Base)" to "Legs",
            "Bodyweight Glute Bridge (Barbell Hip Thrust with Upper Back on Bench)" to "Legs",
            "Dumbbell Goblet Reverse Lunge (Held at Chest, Alternate Legs)" to "Legs",
            "Pallof Press — Dumbbell Hold (Standing, DB Held at Chest, Anti-Rotation Brace)" to "Core",
            "Decline Plank Hold (Feet on Bench, Forearms on Floor — Core Isometric)" to "Core",
            "Dumbbell Supinated Curl (Seated on Bench, Alternating)" to "Arms",
            "Dumbbell Tricep Overhead Extension (Lying on Bench, Both Arms, EZ-Style — Skull Crusher Variation)" to "Arms",
            "Dumbbell Chest-Supported Shrug (Prone on Incline Bench)" to "Back",
            "Bodyweight Back Extension (Prone on Bench, Isometric Hold at Top)" to "Back",
            "Dumbbell Side-Lying External Rotation (Ankle Rehab Companion — Shoulder Light)" to "Shoulders",
            // families that previously returned blank / wrong
            "Barbell Good Morning" to "Legs",
            "Barbell Sumo Deadlift" to "Legs",
            "Barbell Drag Curl" to "Arms",
            "Barbell Floor Skull Crusher (Lying on Floor, EZ Grip with Barbell)" to "Arms",
            "Dumbbell Wide-Grip Upright Row (Elbows Flared, Lateral-Delt Bias)" to "Shoulders",
            "Dumbbell Bent-Over Rear Delt Flye" to "Shoulders",
            "Dumbbell Flat Chest Fly" to "Chest",
            "Dumbbell Squeeze Press (Flat Bench, Palms Facing, Press and Squeeze DBs Together)" to "Chest",
            "Farmer's Carry (Bilateral Dumbbells, Upright Posture — Core Stability)" to "Core",
            "Chest-Supported Dumbbell Row (Prone on Incline Bench, Bilateral)" to "Back",
            // loaded lower-leg rehab is Legs; pure mobility/balance is intentionally blank
            "Tibialis Raise (Seated, Heel on Floor, Toes Lift)" to "Legs",
            "Standing Heel Raise — Bilateral (Bodyweight, Off Step Edge)" to "Legs",
            "Ankle Alphabet / Controlled Ankle Circles (Seated — Ankle Rehab)" to "",
            "Single-Leg Balance Hold (Hand on Wall, 3×20 s each foot — Ankle Rehab)" to "",
            "Seated Ankle Dorsiflexion Hold (Seated, Heel on Floor, Lift Toes and Hold 5 s)" to "",
            "Incline Walk (Treadmill or Outdoor Hill — Ankle Rehab Cardio)" to "Cardio"
        )
        cases.forEach { (name, expected) ->
            assertEquals("fromName(\"$name\")", expected, MuscleClassifier.fromName(name))
        }
    }

    /** The catch-net is keyed on movement FAMILIES, so unseen verbose names resolve + classify too. */
    @Test fun brandNewVerboseNames_generalizeByFamily() {
        val cases = mapOf(
            "Dumbbell Seated Arnold Press (Low Ceiling Safe, Neutral-to-Pronated, 3×12 — Deload Week)" to "Shoulders",
            "Barbell Sumo-Stance Romanian Deadlift (Straps, Slow Eccentric — Posterior Chain Focus)" to "Legs",
            "Dumbbell Chest-Supported Meadows Row (Landmine Feel, Elbow Tucked — Bench Set to 30°)" to "Back",
            "Banded Ankle Alphabet & Single-Leg Balance Reach (Barefoot — Ankle Rehab Superset)" to "",
            "Cable Rope Overhead Triceps Extension (Kneeling, Both Arms — Long-Head Bias)" to "Arms",
            "Single-Leg Standing Calf Raise off a Step (Deep Stretch, Hand on Rack — Prehab)" to "Legs"
        )
        cases.forEach { (name, expectedGroup) ->
            assertNotNull("resolve('$name') should find a library entry", resolver.resolve(name))
            assertEquals("fromName(\"$name\")", expectedGroup, MuscleClassifier.fromName(name))
        }
    }
}
