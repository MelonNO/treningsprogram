package com.migul.treningsprogram

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.dao.ExerciseSessionRow
import com.migul.treningsprogram.domain.MuscleRecovery
import com.migul.treningsprogram.domain.MuscleRecovery.RecoveryState
import com.migul.treningsprogram.ui.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the U1 weighted recovery rework:
 *   1. Fine taxonomy mapping (MuscleClassifier.finerMusclesFor)
 *   2. Broad-group reverse-mapping (MuscleClassifier.broadGroupFor)
 *   3. Recovering-only filter + remaining recovery
 *   4. Per-exercise weighting via multi-muscle records
 *
 * No Android dependencies -- pure JVM, no Hilt/Room.
 * The ViewModel's buildWeightedRecoveryItems logic is extracted into a local helper
 * (RecoveryLogic) that replicates the identical algorithm, keeping tests decoupled
 * from Hilt construction.
 */
class U1WeightedRecoveryTest {

    private val now = 1_000_000_000_000L
    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour
    private fun ago(ms: Long) = now - ms

    // ── 1. Fine taxonomy: finerMusclesFor ────────────────────────────────────────

    @Test fun benchPress_primaryIsChest_synergistsFrontDeltsAndTriceps() {
        val map = MuscleClassifier.finerMusclesFor("Bench Press").toMap()
        assertEquals(1.0f, map["Chest"]!!, 0.001f)
        assertEquals(0.6f, map["Front Delts"]!!, 0.001f)
        assertEquals(0.6f, map["Triceps"]!!, 0.001f)
    }

    @Test fun overheadPress_primaryIsFrontDelts() {
        val map = MuscleClassifier.finerMusclesFor("Overhead Press").toMap()
        assertEquals(1.0f, map["Front Delts"]!!, 0.001f)
        assertEquals(0.6f, map["Side Delts"]!!, 0.001f)
        assertEquals(0.6f, map["Triceps"]!!, 0.001f)
    }

    @Test fun squat_primaryIsQuads_synergistGlutes() {
        val map = MuscleClassifier.finerMusclesFor("Barbell Squat").toMap()
        assertEquals(1.0f, map["Quads"]!!, 0.001f)
        assertEquals(0.6f, map["Glutes"]!!, 0.001f)
    }

    @Test fun romanianDeadlift_primaryIsHamstrings() {
        val map = MuscleClassifier.finerMusclesFor("Romanian Deadlift").toMap()
        assertEquals(1.0f, map["Hamstrings"]!!, 0.001f)
        assertEquals(0.6f, map["Glutes"]!!, 0.001f)
        assertEquals(0.6f, map["Lower Back"]!!, 0.001f)
    }

    @Test fun conventionalDeadlift_primaryIsLowerBack() {
        val map = MuscleClassifier.finerMusclesFor("Deadlift").toMap()
        assertEquals(1.0f, map["Lower Back"]!!, 0.001f)
        assertEquals(0.6f, map["Glutes"]!!, 0.001f)
        assertEquals(0.6f, map["Hamstrings"]!!, 0.001f)
        assertEquals(0.6f, map["Upper Back"]!!, 0.001f)
    }

    @Test fun lateralRaise_primaryIsSideDelts() {
        val map = MuscleClassifier.finerMusclesFor("Lateral Raise").toMap()
        assertEquals(1.0f, map["Side Delts"]!!, 0.001f)
    }

    @Test fun pullUp_primaryIsUpperBack_synergistBiceps() {
        val map = MuscleClassifier.finerMusclesFor("Pull-Up").toMap()
        assertEquals(1.0f, map["Upper Back"]!!, 0.001f)
        assertEquals(0.6f, map["Biceps"]!!, 0.001f)
    }

    @Test fun bicepCurl_primaryIsBiceps() {
        val map = MuscleClassifier.finerMusclesFor("Bicep Curl").toMap()
        assertEquals(1.0f, map["Biceps"]!!, 0.001f)
    }

    @Test fun tricepPushdown_primaryIsTriceps() {
        val map = MuscleClassifier.finerMusclesFor("Tricep Pushdown").toMap()
        assertEquals(1.0f, map["Triceps"]!!, 0.001f)
    }

    @Test fun calfRaise_primaryIsCalves() {
        val map = MuscleClassifier.finerMusclesFor("Calf Raise").toMap()
        assertEquals(1.0f, map["Calves"]!!, 0.001f)
    }

    @Test fun hipThrust_primaryIsGlutes() {
        val map = MuscleClassifier.finerMusclesFor("Hip Thrust").toMap()
        assertEquals(1.0f, map["Glutes"]!!, 0.001f)
    }

    @Test fun plank_primaryIsCore() {
        val map = MuscleClassifier.finerMusclesFor("Plank").toMap()
        assertEquals(1.0f, map["Core"]!!, 0.001f)
    }

    @Test fun unknownExercise_returnsEmptyList() {
        assertTrue(MuscleClassifier.finerMusclesFor("Underwater Basket Weaving").isEmpty())
    }

    @Test fun allWeightsAreInValidRange() {
        val exercises = listOf(
            "Bench Press", "Overhead Press", "Deadlift", "Squat",
            "Pull-Up", "Lateral Raise", "Hip Thrust", "Romanian Deadlift",
            "Bicep Curl", "Tricep Pushdown", "Calf Raise", "Plank", "Seated Row"
        )
        for (ex in exercises) {
            for ((_, w) in MuscleClassifier.finerMusclesFor(ex)) {
                assertTrue("Weight for $ex must be in (0, 1]", w > 0f && w <= 1.0f)
            }
        }
    }

    // ── 2. broadGroupFor reverse mapping ─────────────────────────────────────────

    @Test fun frontDelts_broadGroupIsShoulders() {
        assertEquals("Shoulders", MuscleClassifier.broadGroupFor("Front Delts"))
    }

    @Test fun sideDelts_broadGroupIsShoulders() {
        assertEquals("Shoulders", MuscleClassifier.broadGroupFor("Side Delts"))
    }

    @Test fun quads_broadGroupIsLegs() {
        assertEquals("Legs", MuscleClassifier.broadGroupFor("Quads"))
    }

    @Test fun hamstrings_broadGroupIsLegs() {
        assertEquals("Legs", MuscleClassifier.broadGroupFor("Hamstrings"))
    }

    @Test fun glutes_broadGroupIsLegs() {
        assertEquals("Legs", MuscleClassifier.broadGroupFor("Glutes"))
    }

    @Test fun calves_broadGroupIsLegs() {
        assertEquals("Legs", MuscleClassifier.broadGroupFor("Calves"))
    }

    @Test fun upperBack_broadGroupIsBack() {
        assertEquals("Back", MuscleClassifier.broadGroupFor("Upper Back"))
    }

    @Test fun lowerBack_broadGroupIsBack() {
        assertEquals("Back", MuscleClassifier.broadGroupFor("Lower Back"))
    }

    @Test fun biceps_broadGroupIsArms() {
        assertEquals("Arms", MuscleClassifier.broadGroupFor("Biceps"))
    }

    @Test fun triceps_broadGroupIsArms() {
        assertEquals("Arms", MuscleClassifier.broadGroupFor("Triceps"))
    }

    @Test fun chest_broadGroupIsChest() {
        assertEquals("Chest", MuscleClassifier.broadGroupFor("Chest"))
    }

    @Test fun core_broadGroupIsCore() {
        assertEquals("Core", MuscleClassifier.broadGroupFor("Core"))
    }

    // ── 3. Recovering-only filter ─────────────────────────────────────────────────

    /** Replicates HomeViewModel.buildWeightedRecoveryItems logic in pure JVM for testing. */
    private fun buildItems(rows: List<ExerciseSessionRow>, nowMs: Long): List<HomeViewModel.MuscleRecoveryItem> {
        val stimuliByMuscle = mutableMapOf<String, MutableList<MuscleRecovery.ExerciseStimulusRecord>>()
        for (row in rows) {
            for ((fineLabel, weight) in MuscleClassifier.finerMusclesFor(row.exerciseName)) {
                stimuliByMuscle.getOrPut(fineLabel) { mutableListOf() }.add(
                    MuscleRecovery.ExerciseStimulusRecord(
                        sessionId = row.sessionId,
                        sessionDateMs = row.sessionDateMs,
                        exerciseName = row.exerciseName,
                        weight = weight,
                        effortLevel = row.effortLevel
                    )
                )
            }
        }
        val orderIndex = MuscleClassifier.ALL_FINE_MUSCLES.withIndex()
            .associate { (idx, label) -> label to idx }
        return stimuliByMuscle.entries.mapNotNull { (label, stimuli) ->
            val result = MuscleRecovery.computeRecovery(
                stimuli, nowMs, MuscleRecovery.baseRecoveryMsFor(label)
            ) ?: return@mapNotNull null
            if (result.state != RecoveryState.RECOVERING) return@mapNotNull null
            HomeViewModel.MuscleRecoveryItem(
                muscleLabel = label,
                state = result.state,
                lastTrainedMs = result.lastTrainedMs,
                lastSessionId = result.lastSessionId,
                recoveryFraction = result.recoveryFraction,
                remainingMs = result.remainingMs
            )
        }.sortedBy { orderIndex[it.muscleLabel] ?: Int.MAX_VALUE }
    }

    @Test fun emptyRows_returnsEmptyList() {
        assertTrue(buildItems(emptyList(), now).isEmpty())
    }

    @Test fun onlyRecoveringMusclesAppear_readyAndOverdueAreFiltered() {
        val rows = listOf(ExerciseSessionRow("Bench Press", 1L, ago(10L * hour)))
        val labels = buildItems(rows, now).map { it.muscleLabel }
        assertTrue("Chest should be recovering", "Chest" in labels)
        assertTrue("Front Delts should be recovering", "Front Delts" in labels)
        assertTrue("Triceps should be recovering", "Triceps" in labels)
        assertFalse("Quads should not appear (not trained)", "Quads" in labels)
    }

    @Test fun muscleReadyAfter48h_isNotShown() {
        // 50h -> Chest at weight 1.0 -> effectiveElapsed 50h -> READY -> hidden
        val rows = listOf(ExerciseSessionRow("Bench Press", 1L, ago(50L * hour)))
        val labels = buildItems(rows, now).map { it.muscleLabel }
        assertFalse("Chest should be READY at 50h, not shown", "Chest" in labels)
    }

    @Test fun synergistMuscleReady_whilePrimaryIsRecovering() {
        // Bench 30h ago: Chest (1.0) -> 30h -> RECOVERING; Triceps (0.6) -> 30/0.6=50h -> READY
        val rows = listOf(ExerciseSessionRow("Bench Press", 1L, ago(30L * hour)))
        val labels = buildItems(rows, now).map { it.muscleLabel }
        assertTrue("Chest should be recovering", "Chest" in labels)
        assertFalse("Triceps should be READY (effective 50h), not shown", "Triceps" in labels)
    }

    @Test fun recoveryItemHasCorrectRemainingMs() {
        // Bench 12h ago: Chest weight=1.0 -> remaining = 48 - 12 = 36h
        val rows = listOf(ExerciseSessionRow("Bench Press", 1L, ago(12L * hour)))
        val chestItem = buildItems(rows, now).find { it.muscleLabel == "Chest" }
        assertNotNull(chestItem)
        val expectedRemaining = 36L * hour
        assertEquals(expectedRemaining.toDouble(), chestItem!!.remainingMs.toDouble(), (hour / 10).toDouble())
    }

    @Test fun recoveryItemHasCorrectFraction_halfAt24h() {
        val rows = listOf(ExerciseSessionRow("Bench Press", 1L, ago(24L * hour)))
        val chestItem = buildItems(rows, now).find { it.muscleLabel == "Chest" }
        assertNotNull(chestItem)
        assertEquals(0.5f, chestItem!!.recoveryFraction, 0.01f)
    }

    @Test fun recoveryItemSessionId_pointsToLastTrainedSession() {
        val rows = listOf(ExerciseSessionRow("Bench Press", 42L, ago(10L * hour)))
        val chestItem = buildItems(rows, now).find { it.muscleLabel == "Chest" }
        assertNotNull(chestItem)
        assertEquals(42L, chestItem!!.lastSessionId)
    }

    @Test fun outputOrderFollowsAllFineMusclesDisplayOrder() {
        val rows = listOf(
            ExerciseSessionRow("Bench Press", 1L, ago(10L * hour)),
            ExerciseSessionRow("Squat", 2L, ago(10L * hour))
        )
        val labels = buildItems(rows, now).map { it.muscleLabel }
        val chestIdx = labels.indexOf("Chest")
        val quadsIdx = labels.indexOf("Quads")
        assertTrue("Chest before Quads in display order",
            chestIdx >= 0 && quadsIdx >= 0 && chestIdx < quadsIdx)
    }

    // ── 4. Per-exercise weighting ─────────────────────────────────────────────────

    @Test fun benchPress_andRow_trainDifferentPrimaryMuscles() {
        val benchPrimaries = MuscleClassifier.finerMusclesFor("Bench Press")
            .filter { it.second == 1.0f }.map { it.first }.toSet()
        val rowPrimaries = MuscleClassifier.finerMusclesFor("Seated Row")
            .filter { it.second == 1.0f }.map { it.first }.toSet()
        assertTrue("Chest is primary for bench", "Chest" in benchPrimaries)
        assertTrue("Upper Back is primary for row", "Upper Back" in rowPrimaries)
        assertTrue("Bench and row have no common primary muscle",
            (benchPrimaries intersect rowPrimaries).isEmpty())
    }

    @Test fun squat_doesNotTrainCardio() {
        val labels = buildItems(
            listOf(ExerciseSessionRow("Squat", 1L, ago(10L * hour))),
            now
        ).map { it.muscleLabel }
        assertFalse("Cardio should not appear from Squat", "Cardio" in labels)
    }

    @Test fun multipleSessionsSameExercise_latestHigherWeightSessionWins() {
        // Session 1: Squat (Quads 1.0) 30h ago
        // Session 2: Leg Press (Quads 1.0) 10h ago (same weight, more recent)
        val rows = listOf(
            ExerciseSessionRow("Squat", 1L, ago(30L * hour)),
            ExerciseSessionRow("Leg Press", 2L, ago(10L * hour))
        )
        val quadsItem = buildItems(rows, now).find { it.muscleLabel == "Quads" }
        assertNotNull(quadsItem)
        // Both have weight 1.0, so the more recent session (2) wins
        assertEquals(2L, quadsItem!!.lastSessionId)
    }
}
