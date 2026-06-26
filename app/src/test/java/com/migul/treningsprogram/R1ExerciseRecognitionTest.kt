package com.migul.treningsprogram

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.MuscleGroupResolver
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1 — exercise-name → muscle-group recognition. Guards the corrected [MuscleClassifier.fromName]
 * ordering/keywords, the shared [MuscleGroupResolver] used by both the write path and the
 * historical backfill, and the finer-taxonomy consistency. Also pins the regression where
 * "tempo"/"interval" strength modifiers were wrongly read as Cardio (inflating time estimates).
 */
class R1ExerciseRecognitionTest {

    private fun planned(
        name: String,
        sets: Int,
        targetReps: String,
        rest: Int = 90
    ) = PlannedExercise(
        weekStart = 0L,
        dayOfWeek = 1,
        orderInDay = 0,
        exerciseName = name,
        sets = sets,
        targetReps = targetReps,
        targetWeightKg = 0f,
        recommendedRestSeconds = rest
    )

    @Test fun realWorldNames_classifyToExpectedBroadGroup() {
        val cases = mapOf(
            "Incline Walk" to "Cardio",
            "Chest-Supported Dumbbell Row (Incline Bench)" to "Back",
            "Dumbbell Seated Arnold Press" to "Shoulders",
            "Dumbbell Bent-Over Rear Delt Fly" to "Shoulders",
            "Dumbbell Flat Neutral-Grip Press (Chest Focus)" to "Chest",
            "Dumbbell Chest-Supported Face Pull Alternative — Prone DB Rear Delt Fly" to "Shoulders",
            "Hand-Supported Single-Leg Balance Hold (Wall Touch)" to "",
            "Dumbbell Overhead Press (Seated, Pronated Grip)" to "Shoulders",
            "Dumbbell Face Pull Substitute — Dumbbell Prone Y-Raise (on Incline Bench)" to "Shoulders",
            "Dumbbell Squeeze Press (Flat Bench)" to "Chest",
            "Ab Roller Roll-Out" to "Core",
            "Standing Ankle Balance Hold (Ankle Prehab)" to "",
            "Barbell Drag Curl" to "Arms",
            "Dumbbell Renegade Row" to "Back",
            "High Knees" to "Cardio",
            "Tibialis Raise (Seated, Heels on Floor, Toes Lift)" to "Legs",
            "Dumbbell Bent-Over Lateral Raise (Rear Delt)" to "Shoulders",
            "Dumbbell Reverse Fly (Lying Face-Down on Flat Bench)" to "Shoulders",
            "Wall-Supported Single-Leg Calf Raise (Ankle Rehab)" to "Legs",
            "Dumbbell Seated Neutral-Grip Overhead Press" to "Shoulders",
            "Calf Raise on Step — Hand-Supported Single-Leg (Ankle Rehab)" to "Legs",
            "Dumbbell Chest-Supported Rear Delt Row (Wide Elbows)" to "Shoulders",
            "Standing Single-Leg Ankle Balance Hold (Hand on Wall)" to "",
            "Ankle Alphabet / Foot Circles (Seated)" to ""
        )
        cases.forEach { (name, expected) ->
            assertEquals("fromName(\"$name\") should be \"$expected\"", expected, MuscleClassifier.fromName(name))
        }
    }

    @Test fun patternRegressionGuards() {
        val cases = mapOf(
            "Chest Fly" to "Chest",
            "Incline Dumbbell Fly" to "Chest",
            "Cable Fly" to "Chest",
            "Rear Delt Fly" to "Shoulders",
            "Reverse Fly" to "Shoulders",
            "Bench Press" to "Chest",
            "Incline Bench Press" to "Chest",
            "Chest-Supported T-Bar Row" to "Back",
            "Seated Cable Row" to "Back",
            "Outdoor Run" to "Cardio",
            "Easy Jog" to "Cardio",
            "Stationary Bike" to "Cardio",
            "Treadmill Run" to "Cardio",
            "High Knees" to "Cardio",
            "Jump Rope" to "Cardio",
            "Tempo Squat" to "Legs",
            "Interval Lunge" to "Legs",
            "Tempo Bench Press" to "Chest",
            // walk must NOT override the leg movement
            "Walking Lunge" to "Legs"
        )
        cases.forEach { (name, expected) ->
            assertEquals("fromName(\"$name\") should be \"$expected\"", expected, MuscleClassifier.fromName(name))
        }
    }

    @Test fun tempoMove_isNotCardio_andTimeEstimateIsNotInflated() {
        val name = "Standing Calf Raise (Slow Tempo)"
        assertNotEquals("tempo move must not be Cardio", "Cardio", MuscleClassifier.displayName(name))

        // strength sec = sets*(maxReps*3) + (sets-1)*rest + 60 = 3*(25*3) + 2*45 + 60 = 375
        val ex = planned(name, sets = 3, targetReps = "20-25", rest = 45)
        assertEquals(375, WorkoutTimeEstimator.estimateExerciseSeconds(ex))
        assertTrue("must be far under the 30-min cardio fallback",
            WorkoutTimeEstimator.estimateExerciseSeconds(ex) < 1800)

        assertEquals("Legs", MuscleClassifier.displayName("Tempo Squat"))
        assertNotEquals("Cardio", MuscleClassifier.displayName("Interval Lunge"))
    }

    @Test fun resolver_isSharedBetweenWriteAndBackfill_andDefaultsStayAuthoritative() {
        // DEFAULT_EXERCISE whose name alone would NOT match fromName, but the library is authoritative.
        assertEquals("Chest", MuscleGroupResolver.resolve("Incline Dumbbell Press"))
        assertEquals("Legs", MuscleGroupResolver.resolve("Calf Raise"))

        // A custom (non-library) name falls back to fromName — identical result both ways.
        val custom = "Chest-Supported Dumbbell Row (Incline Bench)"
        assertEquals(MuscleClassifier.fromName(custom), MuscleGroupResolver.resolve(custom))
        assertEquals("Back", MuscleGroupResolver.resolve(custom))

        // Idempotent: resolving twice yields the same group.
        listOf("Incline Dumbbell Press", custom, "Dumbbell Bent-Over Rear Delt Fly", "Tempo Squat").forEach {
            assertEquals("resolve must be idempotent for \"$it\"",
                MuscleGroupResolver.resolve(it), MuscleGroupResolver.resolve(it))
        }
    }

    @Test fun finerTaxonomy_primaryBroadGroup_isConsistent() {
        val cases = mapOf(
            "Dumbbell Reverse Fly (Lying Face-Down on Flat Bench)" to "Shoulders",
            "Chest-Supported Dumbbell Row (Incline Bench)" to "Back",
            "Dumbbell Face Pull Substitute — Dumbbell Prone Y-Raise (on Incline Bench)" to "Shoulders",
            "Dumbbell Chest-Supported Rear Delt Row (Wide Elbows)" to "Shoulders",
            "Incline Walk" to "Cardio"
        )
        cases.forEach { (name, expectedBroad) ->
            val primaryBroad = MuscleClassifier.broadGroupFor(MuscleClassifier.finerMusclesFor(name).first().first)
            assertEquals("finer primary broad for \"$name\"", expectedBroad, primaryBroad)
        }

        listOf(
            "Hand-Supported Single-Leg Balance Hold (Wall Touch)",
            "Standing Ankle Balance Hold (Ankle Prehab)",
            "Standing Single-Leg Ankle Balance Hold (Hand on Wall)",
            "Ankle Alphabet / Foot Circles (Seated)"
        ).forEach { name ->
            assertTrue("balance move \"$name\" must have no fine muscles",
                MuscleClassifier.finerMusclesFor(name).isEmpty())
        }
    }
}
