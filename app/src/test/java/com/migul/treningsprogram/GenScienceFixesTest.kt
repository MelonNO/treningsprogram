package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.buildCrossWeekRecoveryBlock
import com.migul.treningsprogram.data.repository.cardioEntriesViolation
import com.migul.treningsprogram.data.repository.pullHingeAdjacencyWarning
import com.migul.treningsprogram.data.repository.salvageRestFloorSeconds
import com.migul.treningsprogram.data.repository.sanitizeUnanchoredWeights
import com.migul.treningsprogram.data.repository.trimOverflowToWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Generation science-review fixes (2026-08-03) — the new deterministic seams:
 *
 *  - [cardioEntriesViolation] — the NO-CARDIO product guarantee: generated plans are
 *    resistance-only; a Cardio-classified entry deterministically rejects the attempt.
 *  - [sanitizeUnanchoredWeights] — P2 (fixes S3): fabricated absolute loads are coerced to 0 kg
 *    when the user has no loaded history (at all, or in the lift's broad muscle group). The
 *    live-proven injury vector: a 130 kg deadlift prescribed to a user with no logged history.
 *  - [buildCrossWeekRecoveryBlock] — P1 (fixes S1, the user-hit case): the prompt gets a real
 *    date anchor + recent-session muscles + an explicit cross-boundary recovery rule.
 *  - [pullHingeAdjacencyWarning] — P5 (fixes S4): warn-only detection of a heavy-hinge day
 *    directly adjacent to a dedicated pull day.
 *  - [salvageRestFloorSeconds] + the [trimOverflowToWindow] floor parameter — P6 (fixes S7):
 *    a salvaged Strength day may no longer end up at 60 s rests.
 */
class GenScienceFixesTest {

    private fun ex(
        name: String, day: Int = 1, order: Int = 0, sets: Int = 3, reps: String = "8-10",
        weight: Float = 0f, rest: Int = 120, notes: String = ""
    ) = PlannedExercise(
        weekStart = 0L, dayOfWeek = day, orderInDay = order, exerciseName = name,
        sets = sets, targetReps = reps, targetWeightKg = weight, notes = notes,
        recommendedRestSeconds = rest
    )

    // ── NO-CARDIO gate ──────────────────────────────────────────────────────────────────────────

    @Test fun cardioEntry_rejectsWithTargetedFeedback() {
        val plan = listOf(
            ex("Barbell Bench Press", day = 1, order = 0),
            ex("Outdoor Run", day = 1, order = 1, sets = 1, reps = "30 min")
        )
        val reason = cardioEntriesViolation(plan)
        assertNotNull(reason); reason!!
        assertTrue("names the offender: $reason", reason.contains("Outdoor Run"))
        assertTrue("states the rule: $reason", reason.contains("RESISTANCE-ONLY"))
    }

    @Test fun pureStrengthPlan_passesCardioGate() {
        val plan = listOf(
            ex("Barbell Bench Press", day = 1), ex("Goblet Squat", day = 2),
            ex("Walking Lunge", day = 2, order = 1)  // "walk" inside a strength name stays Legs
        )
        assertNull(cardioEntriesViolation(plan))
    }

    @Test fun cardioOnLockedDay_isExempt() {
        // A locked day echoes real logged history, which may legitimately contain a run.
        val plan = listOf(
            ex("Outdoor Run", day = 1, sets = 1, reps = "30 min"),
            ex("Barbell Bench Press", day = 3)
        )
        assertNull(cardioEntriesViolation(plan, lockedDays = setOf(1)))
        assertNotNull(cardioEntriesViolation(plan, lockedDays = emptySet()))
    }

    // ── P2 weight-sanity gate ───────────────────────────────────────────────────────────────────

    @Test fun noLoadedHistory_zeroesEveryFabricatedLoad_keepsNotes() {
        // The live-proven S3 case: 130 kg deadlift + 110 kg squat for a user with NO history.
        val plan = listOf(
            ex("Barbell Deadlift", weight = 130f, notes = "RPE 7; +2.5 kg when clean"),
            ex("Barbell Squat", weight = 110f, order = 1),
            ex("Push-Up", weight = 0f, order = 2)
        )
        val out = sanitizeUnanchoredWeights(plan, emptySet(), hasAnyLoadedHistory = false)
        assertEquals(0f, out[0].targetWeightKg)
        assertEquals(0f, out[1].targetWeightKg)
        assertEquals(0f, out[2].targetWeightKg)
        assertEquals("notes are preserved", "RPE 7; +2.5 kg when clean", out[0].notes)
    }

    @Test fun loadedHistoryInGroup_keepsWeight_relatedLiftEstimationStaysLegal() {
        // Logged squats (Legs) anchor a goblet-squat estimate; the weight survives.
        val plan = listOf(ex("Goblet Squat", weight = 24f))
        val out = sanitizeUnanchoredWeights(plan, setOf("Legs"), hasAnyLoadedHistory = true)
        assertEquals(24f, out[0].targetWeightKg)
    }

    @Test fun loadedHistoryOnlyElsewhere_zeroesCrossGroupFabrication() {
        // Bench-only history (Chest) cannot anchor a fabricated 130 kg deadlift (Back).
        val plan = listOf(
            ex("Barbell Deadlift", weight = 130f),
            ex("Barbell Bench Press", weight = 62.5f, order = 1)
        )
        val out = sanitizeUnanchoredWeights(plan, setOf("Chest"), hasAnyLoadedHistory = true)
        assertEquals("deadlift fabrication zeroed", 0f, out[0].targetWeightKg)
        assertEquals("bench anchored by its own group", 62.5f, out[1].targetWeightKg)
    }

    @Test fun unclassifiableName_isLeftAlone() {
        // "" classification: never risk zeroing a legitimate related-lift estimate.
        val plan = listOf(ex("Mystery Machine Movement", weight = 40f))
        val out = sanitizeUnanchoredWeights(plan, setOf("Chest"), hasAnyLoadedHistory = true)
        assertEquals(40f, out[0].targetWeightKg)
    }

    @Test fun lockedDays_areExemptFromWeightCoercion() {
        val plan = listOf(ex("Barbell Deadlift", day = 2, weight = 130f))
        val out = sanitizeUnanchoredWeights(plan, emptySet(), false, lockedDays = setOf(2))
        assertEquals(130f, out[0].targetWeightKg)
    }

    // ── P1 cross-week recovery block ────────────────────────────────────────────────────────────

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            clear(); set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    @Test fun recoveryBlock_anchorsDates_andSpansTheBoundary() {
        // The user-hit case: chest pressed Sunday, the new week generated on Monday.
        val monday = millisOf(2026, 8, 3, 0)     // Mon 2026-08-03 (weekStart)
        val today = millisOf(2026, 8, 3, 9)      // generated Monday morning
        val sunday = millisOf(2026, 8, 2, 18)    // heavy chest the evening before
        val block = buildCrossWeekRecoveryBlock(
            today, monday, listOf(sunday to listOf("Chest", "Arms"))
        )
        assertTrue("has the header", block.contains("CROSS-WEEK RECOVERY"))
        assertTrue("anchors the plan window", block.contains("Monday 2026-08-03"))
        assertTrue("names the boundary distance: $block", block.contains("1 day before this plan's Monday"))
        assertTrue("lists the session muscles", block.contains("Chest, Arms"))
        assertTrue("states the cross-boundary rule", block.contains("ACROSS this boundary"))
        assertTrue("names the user-hit example", block.contains("chest pressed on Sunday"))
    }

    @Test fun recoveryBlock_emptyForNewUser_andForInWeekSessionsOnly() {
        val monday = millisOf(2026, 8, 3, 0)
        assertEquals("", buildCrossWeekRecoveryBlock(millisOf(2026, 8, 3, 9), monday, emptyList()))
        // A session ON/AFTER the plan's Monday is the locked-days block's job, not this one's.
        val wednesday = millisOf(2026, 8, 5, 18)
        assertEquals(
            "", buildCrossWeekRecoveryBlock(
                millisOf(2026, 8, 6, 9), monday, listOf(wednesday to listOf("Legs"))
            )
        )
    }

    @Test fun recoveryBlock_listsAtMostThreePreBoundarySessions() {
        val monday = millisOf(2026, 8, 3, 0)
        val sessions = listOf(
            millisOf(2026, 8, 2) to listOf("Chest"),
            millisOf(2026, 8, 1) to listOf("Back"),
            millisOf(2026, 7, 31) to listOf("Legs"),
            millisOf(2026, 7, 30) to listOf("Shoulders")
        )
        val block = buildCrossWeekRecoveryBlock(millisOf(2026, 8, 3, 9), monday, sessions)
        assertTrue(block.contains("Chest") && block.contains("Back") && block.contains("Legs"))
        assertFalse("only the 3 most recent are listed", block.contains("Shoulders"))
    }

    // ── P5 pull→hinge adjacency warning ─────────────────────────────────────────────────────────

    private fun pullDay(day: Int) = listOf(
        ex("Weighted Pull-Up", day = day, order = 0),
        ex("Barbell Row", day = day, order = 1),
        ex("Bicep Curl", day = day, order = 2)
    )

    @Test fun deadliftDayAfterPullDay_warns() {
        val plan = pullDay(5) + listOf(ex("Barbell Deadlift", day = 6), ex("Leg Press", day = 6, order = 1))
        val warning = pullHingeAdjacencyWarning(plan)
        assertNotNull(warning); warning!!
        assertTrue("names both days: $warning", warning.contains("5") && warning.contains("6"))
    }

    @Test fun spacedHingeDay_doesNotWarn() {
        val plan = pullDay(1) + listOf(ex("Barbell Deadlift", day = 4))
        assertNull(pullHingeAdjacencyWarning(plan))
    }

    @Test fun hingeOnThePullDayItself_doesNotWarn() {
        // Same-day pairing is a session-design question, not the adjacency case S4 flagged.
        val plan = pullDay(3) + listOf(ex("Barbell Deadlift", day = 3, order = 3))
        assertNull(pullHingeAdjacencyWarning(plan))
    }

    @Test fun singlePullExerciseAdjacent_doesNotWarn() {
        // One row on the adjacent day is not a dedicated pull day (needs >= 2 Back pulls).
        val plan = listOf(ex("Barbell Row", day = 5), ex("Bench Press", day = 5, order = 1)) +
            listOf(ex("Barbell Deadlift", day = 6))
        assertNull(pullHingeAdjacencyWarning(plan))
    }

    // ── P6 goal-aware salvage rest floor ────────────────────────────────────────────────────────

    @Test fun salvageRestFloor_perGoal() {
        assertEquals(180, salvageRestFloorSeconds("Strength"))
        assertEquals(90, salvageRestFloorSeconds("Hypertrophy"))
        assertEquals(60, salvageRestFloorSeconds("Endurance"))
        assertEquals(60, salvageRestFloorSeconds("Weight Loss"))
        assertEquals(60, salvageRestFloorSeconds("General"))
    }

    @Test fun trimFloor_changesOutcome_strengthDayIsNotButcheredTo60s() {
        // 4 exercises, sets=2 (no set-drops possible), removal blocked (would leave < 4), rest 180.
        // Corrected estimator: per ex = 2*(20*4+35) + 2*180 + 90 = 680 s; day = 2720 s ≈ 45 min.
        // Target 20 → window 10–30 (at the flat 60 s floor the day is 4×440+30 ≈ 29 min = in-window).
        val day = (0..3).map { i -> ex("Lift $i", order = i, sets = 2, reps = "20", rest = 180) }
        // Strength floor (180): rest is untouchable, no other lever → correctly unsalvageable.
        assertNull(trimOverflowToWindow(day, 20, emptySet(), null, 180))
        // Historical flat 60 s floor: the same day WOULD have been salvaged by cutting rest to
        // 60 s — exactly the S7 quality failure P6 removes for strength.
        val flat = trimOverflowToWindow(day, 20, emptySet(), null, 60)
        assertNotNull(flat)
        assertTrue(flat!!.any { it.recommendedRestSeconds < 180 })
    }

    @Test fun trimWithFloor_neverCutsRestBelowFloor_whileStillTrimmingIntoWindow() {
        // Over-target day with rest headroom above the floor: per ex = 4*8*4 + 3*300 + 60 = 1088 s;
        // 5 exercises = 5440 s ≈ 91 min vs target 60 (window 50–70) → over.
        val day = (0..4).map { i -> ex("Lift $i", order = i, sets = 4, reps = "8", rest = 300) }
        val out = trimOverflowToWindow(day, 60, emptySet(), null, 180)
        assertNotNull(out); out!!
        assertTrue("every rest stays at/above the strength floor",
            out.all { it.recommendedRestSeconds >= 180 })
    }
}
