package com.migul.treningsprogram

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.trimOverflowToWindow
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G2 Phase-2/3 — deterministic auto-trim SALVAGE of OVER-target days back into the strict window.
 *
 * [trimOverflowToWindow] is layered STRICTLY AFTER the gate's estimate. It never touches the estimator,
 * the ±10-min window, or any threshold. The trim LADDER is REST → SETS → REMOVAL (rest-first reclaims the
 * most minutes with zero loss of sets/exercises/coverage and validateProgram never penalises low rest):
 *   (1) lower recommendedRestSeconds toward a 60 s floor (never <60, never above original),
 *   (2) drop whole SETS from trailing non-primary exercises (sets>2),
 *   (3) remove whole trailing non-primary exercises (guarded).
 * reps / weight / notes are NEVER edited. These pure tests pin that contract (mirrors the H3/H4 pattern).
 *
 * Estimator (authoritative, same one the gate uses; P2 2026-07: per-rep work is 4 s):
 *   strength sec = sets*(maxReps*4) + (sets-1)*rest + 60 ;  day mins = (Σ + 30) / 60
 */
class G2TrimOverflowTest {

    private val target = 50          // window 40–60
    private val low = target - 10    // 40
    private val high = target + 10   // 60

    private fun ex(
        order: Int,
        name: String,
        sets: Int,
        reps: String,
        rest: Int,
        day: Int = 1
    ) = PlannedExercise(
        weekStart = 0L,
        dayOfWeek = day,
        orderInDay = order,
        exerciseName = name,
        sets = sets,
        targetReps = reps,
        targetWeightKg = 20f,
        recommendedRestSeconds = rest,
        notes = "RPE 8 (~2 RIR); double progression +reps then +load"
    )

    private fun dayMins(list: List<PlannedExercise>, day: Int) =
        WorkoutTimeEstimator.estimateDayMinutes(list.filter { it.dayOfWeek == day })

    /** Assert reps/weight/notes are immutable vs the source (matched by name); only sets/rest may differ. */
    private fun assertRepsWeightNotesUnchanged(src: List<PlannedExercise>, out: List<PlannedExercise>) {
        out.forEach { t ->
            val s = src.first { it.exerciseName == t.exerciseName }
            assertEquals("reps unchanged for ${t.exerciseName}", s.targetReps, t.targetReps)
            assertEquals("weight unchanged for ${t.exerciseName}", s.targetWeightKg, t.targetWeightKg)
            assertEquals("notes unchanged for ${t.exerciseName}", s.notes, t.notes)
        }
    }

    // ── precondition: the muscle classifications the fixtures rely on are real ──────────────────────

    @Test fun fixtureClassifications_areStable() {
        assertEquals("Chest", MuscleClassifier.displayName("Barbell Bench Press"))
        assertEquals("Back", MuscleClassifier.displayName("Dumbbell Row"))
        assertEquals("Back", MuscleClassifier.displayName("Lat Pulldown"))
        assertEquals("Shoulders", MuscleClassifier.displayName("Lateral Raise"))
        assertEquals("Arms", MuscleClassifier.displayName("Triceps Pushdown"))
        assertEquals("Arms", MuscleClassifier.displayName("Dumbbell Curl"))
        assertEquals("Legs", MuscleClassifier.displayName("Leg Curl"))
        assertEquals("Legs", MuscleClassifier.displayName("Barbell Squat"))
    }

    // ── 1. over PURELY due to long rest → salvaged by REST reduction ALONE (no set/exercise removed) ─

    @Test fun restOnly_overshoot_salvagedByRestReductionAlone_noSetOrExerciseRemoved() {
        // 6 exercises at the SAME sets but long 150 s rest ⇒ over the ceiling purely on rest.
        val daySrc = listOf(
            ex(0, "Barbell Bench Press", 4, "6-8", 150),   // primary
            ex(1, "Dumbbell Row", 4, "8-12", 150),
            ex(2, "Lateral Raise", 4, "12-15", 150),
            ex(3, "Triceps Pushdown", 4, "10-15", 150),
            ex(4, "Dumbbell Curl", 4, "10-12", 150),
            ex(5, "Leg Curl", 4, "12-15", 150)
        )
        assertEquals("fixture is genuinely OVER the ceiling", 72, dayMins(daySrc, 1))

        val trimmed = trimOverflowToWindow(daySrc, target, lockedDays = emptySet())
        assertNotNull("a rest-only overshoot must be salvageable", trimmed)
        trimmed!!

        assertTrue("day lands in [$low,$high]: ${dayMins(trimmed, 1)}", dayMins(trimmed, 1) in low..high)
        // REST reduction ALONE — nothing removed, no set dropped.
        assertEquals("no exercise removed", 6, trimmed.size)
        assertEquals("NO set dropped (rest-only salvage)", daySrc.sumOf { it.sets }, trimmed.sumOf { it.sets })
        // rest was actually reduced, and never below 60 / never above its original.
        assertTrue("rest was reduced somewhere",
            trimmed.sumOf { it.recommendedRestSeconds } < daySrc.sumOf { it.recommendedRestSeconds })
        trimmed.forEach { t ->
            assertTrue("${t.exerciseName} rest never below the 60 s floor: ${t.recommendedRestSeconds}",
                t.recommendedRestSeconds >= 60)
            assertTrue("${t.exerciseName} rest never above its original (150): ${t.recommendedRestSeconds}",
                t.recommendedRestSeconds <= 150)
        }
        assertRepsWeightNotesUnchanged(daySrc, trimmed)
    }

    // ── 2. rest already at the floor → SET-DROPS are the active lever; primary + rest untouched ─────

    @Test fun setDrops_whenRestAtFloor_trimsSetsNotRest_primaryUntouched() {
        // rest already 60 (rest lever exhausted) and heavy set volume ⇒ over; only set-drops can fix it.
        val daySrc = listOf(
            ex(0, "Barbell Bench Press", 7, "6-8", 60),   // primary
            ex(1, "Dumbbell Row", 7, "8-12", 60),
            ex(2, "Lateral Raise", 7, "12-15", 60),
            ex(3, "Triceps Pushdown", 7, "10-15", 60),
            ex(4, "Dumbbell Curl", 7, "10-12", 60),
            ex(5, "Leg Curl", 7, "12-15", 60)
        )
        assertTrue("fixture is over the ceiling", dayMins(daySrc, 1) > high)

        val trimmed = trimOverflowToWindow(daySrc, target, emptySet())
        assertNotNull(trimmed)
        trimmed!!

        assertTrue("day lands in window: ${dayMins(trimmed, 1)}", dayMins(trimmed, 1) in low..high)
        assertEquals("no exercise removed", 6, trimmed.size)
        assertEquals("primary sets untouched", 7, trimmed.first { it.exerciseName == "Barbell Bench Press" }.sets)
        // rest can't drop below 60 (already there) → unchanged; set-drops did the work.
        assertTrue("rest stays at the 60 s floor", trimmed.all { it.recommendedRestSeconds == 60 })
        assertTrue("at least one set dropped", trimmed.sumOf { it.sets } < daySrc.sumOf { it.sets })
        assertTrue("no exercise trimmed below 2 sets", trimmed.all { it.sets >= 2 })
        assertRepsWeightNotesUnchanged(daySrc, trimmed)
    }

    // ── 3. long rest AND heavy volume → rest fully reduces, then set-drops; never undershoots floor ─

    @Test fun restThenSets_combined_neverUndershootsFloor() {
        val daySrc = listOf(
            ex(0, "Barbell Bench Press", 7, "6-8", 150),   // primary
            ex(1, "Dumbbell Row", 7, "8-12", 150),
            ex(2, "Lateral Raise", 7, "12-15", 150),
            ex(3, "Triceps Pushdown", 7, "10-15", 150),
            ex(4, "Dumbbell Curl", 7, "10-12", 150),
            ex(5, "Leg Curl", 7, "12-15", 150)
        )
        assertTrue("fixture is far over the ceiling", dayMins(daySrc, 1) > high)

        val trimmed = trimOverflowToWindow(daySrc, target, emptySet())
        assertNotNull(trimmed)
        trimmed!!

        assertTrue("never knocked below the floor", dayMins(trimmed, 1) >= low)
        assertTrue("landed in window", dayMins(trimmed, 1) in low..high)
        // rest fully reduced to the floor, then sets trimmed.
        assertTrue("rest fully reduced to the 60 s floor", trimmed.all { it.recommendedRestSeconds == 60 })
        assertTrue("at least one set dropped after rest bottomed out",
            trimmed.sumOf { it.sets } < daySrc.sumOf { it.sets })
        assertEquals("primary sets untouched", 7, trimmed.first { it.exerciseName == "Barbell Bench Press" }.sets)
        assertRepsWeightNotesUnchanged(daySrc, trimmed)
    }

    // ── 4. removal never orphans a muscle group; a duplicate-muscle exercise is removed instead ─────

    @Test fun removal_skipsOrphanMuscle_removesDuplicateInstead() {
        // target 30 (window 20–40). rest already 60 + accessories at the 2-set floor ⇒ only REMOVAL helps.
        // Trailing two (Arms, Shoulders) are the ONLY member of their group → removing them would orphan
        // the group → skipped. Leg Curl (Legs, duplicated by the squat primary) is the deepest removable.
        // Sized for the 4-s/rep estimator: day ≈ 42 min (over the 40 ceiling); removing Leg Curl ⇒ ≈ 38 min.
        val t30 = 30
        val daySrc = listOf(
            ex(0, "Barbell Squat", 13, "12", 60),    // primary, Legs — big enough that levers 1&2 are exhausted
            ex(1, "Dumbbell Row", 2, "12", 60),      // Back
            ex(2, "Lat Pulldown", 2, "12", 60),      // Back (dup)
            ex(3, "Leg Curl", 2, "12", 60),          // Legs (dup of squat) → safe to remove
            ex(4, "Lateral Raise", 2, "15", 60),     // Shoulders (unique) → orphan if removed
            ex(5, "Dumbbell Curl", 2, "12", 60)      // Arms (unique) → orphan if removed
        )
        assertTrue("fixture is over the t30 ceiling", dayMins(daySrc, 1) > t30 + 10)

        val trimmed = trimOverflowToWindow(daySrc, t30, emptySet())
        assertNotNull(trimmed)
        trimmed!!

        assertTrue("day lands in [20,40]: ${dayMins(trimmed, 1)}", dayMins(trimmed, 1) in (t30 - 10)..(t30 + 10))
        assertTrue("unique Arms exercise retained", trimmed.any { it.exerciseName == "Dumbbell Curl" })
        assertTrue("unique Shoulders exercise retained", trimmed.any { it.exerciseName == "Lateral Raise" })
        assertFalse("duplicate-muscle Leg Curl removed", trimmed.any { it.exerciseName == "Leg Curl" })
        assertEquals("primary sets untouched", 13, trimmed.first { it.exerciseName == "Barbell Squat" }.sets)
        assertTrue("day keeps ≥4 exercises", trimmed.size >= 4)
        assertTrue("rest untouched at floor", trimmed.all { it.recommendedRestSeconds == 60 })
        // re-numbered contiguously after the removal
        assertEquals(trimmed.indices.toList(), trimmed.sortedBy { it.orderInDay }.map { it.orderInDay })
    }

    // ── 5. extreme overshoot that cannot reach the window without breaking a guard → null ───────────

    @Test fun extremeOvershoot_unsalvageable_returnsNull() {
        // target 30. rest at the 60 s floor (rest lever exhausted) + a huge UN-trimmable primary; the 3
        // accessories are already at the 2-set floor (no safe set-drop), and only 4 exercises means any
        // removal breaks the ≥4 guard ⇒ the day stays over ⇒ null (never trims under the floor / relaxes).
        val t30 = 30
        val daySrc = listOf(
            ex(0, "Barbell Squat", 16, "20", 60),    // primary, never trimmed; oversized to force over-at-floor
            ex(1, "Dumbbell Row", 2, "20", 60),
            ex(2, "Lateral Raise", 2, "20", 60),
            ex(3, "Dumbbell Curl", 2, "20", 60)
        )
        assertTrue("fixture is over the t30 ceiling even at the rest floor", dayMins(daySrc, 1) > t30 + 10)
        assertNull("un-salvageable overshoot must return null", trimOverflowToWindow(daySrc, t30, emptySet()))
    }

    // ── 6. under-time day is left alone (no auto-add) → un-salvageable → null ────────────────────────

    @Test fun underTimeDay_neverPadded_returnsNull() {
        val daySrc = listOf(
            ex(0, "Barbell Bench Press", 2, "8", 60),
            ex(1, "Dumbbell Row", 2, "8", 60),
            ex(2, "Lateral Raise", 2, "10", 60)
        )
        assertTrue("fixture is UNDER the floor", dayMins(daySrc, 1) < low)
        assertNull("under-time day must not be auto-filled", trimOverflowToWindow(daySrc, target, emptySet()))
    }

    // ── 7. locked day is skipped entirely (not trimmed, not counted against the window) ─────────────

    @Test fun lockedOverDay_skipped_andDoesNotBlockSalvage() {
        val plan = listOf(
            ex(0, "Barbell Squat", 6, "12", 180, day = 1),
            ex(1, "Dumbbell Row", 6, "12", 180, day = 1),
            ex(2, "Lateral Raise", 6, "15", 180, day = 1),
            ex(3, "Dumbbell Curl", 6, "12", 180, day = 1),
            ex(0, "Barbell Bench Press", 4, "8", 150, day = 2),
            ex(1, "Dumbbell Row", 4, "10", 150, day = 2),
            ex(2, "Lateral Raise", 4, "15", 150, day = 2),
            ex(3, "Triceps Pushdown", 4, "15", 150, day = 2)
        )
        assertTrue("locked day 1 is over the window", dayMins(plan, 1) > high)
        assertTrue("day 2 is in-window", dayMins(plan, 2) in low..high)

        val trimmed = trimOverflowToWindow(plan, target, lockedDays = setOf(1))
        assertNotNull("locked over-day must not block salvage", trimmed)
        trimmed!!

        // Locked day 1 returned verbatim (still over, still 4 exercises, sets + rest untouched).
        val day1 = trimmed.filter { it.dayOfWeek == 1 }
        assertEquals("locked day 1 untouched (count)", 4, day1.size)
        assertTrue("locked day 1 NOT trimmed (still over)", dayMins(trimmed, 1) > high)
        assertTrue("locked day 1 sets untouched", day1.all { it.sets == 6 })
        assertTrue("locked day 1 rest untouched (not reduced)", day1.all { it.recommendedRestSeconds == 180 })
        // Day 2 returned unchanged and in-window (in-window days are not rest-trimmed).
        val day2 = trimmed.filter { it.dayOfWeek == 2 }
        assertEquals("day 2 untouched (count)", 4, day2.size)
        assertTrue("day 2 rest untouched", day2.all { it.recommendedRestSeconds == 150 })
        assertTrue("day 2 still in-window", dayMins(trimmed, 2) in low..high)
    }

    // ── 8. an already-all-in-window plan is returned unchanged ──────────────────────────────────────

    @Test fun allInWindow_returnedUnchanged() {
        val daySrc = listOf(
            ex(0, "Barbell Bench Press", 4, "8", 150),
            ex(1, "Dumbbell Row", 4, "10", 150),
            ex(2, "Lateral Raise", 4, "15", 150),
            ex(3, "Triceps Pushdown", 4, "15", 150)
        )
        assertTrue("fixture already in-window", dayMins(daySrc, 1) in low..high)
        val trimmed = trimOverflowToWindow(daySrc, target, emptySet())
        assertNotNull(trimmed)
        assertEquals("in-window plan returned byte-for-byte (no rest reduction)", daySrc, trimmed)
    }
}
