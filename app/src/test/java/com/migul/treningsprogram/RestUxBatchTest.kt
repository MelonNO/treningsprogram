package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.dayDurationFeedback
import com.migul.treningsprogram.data.repository.trimOverflowToWindow
import com.migul.treningsprogram.domain.ManualRestTimes
import com.migul.treningsprogram.domain.RestTimePolicy
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import com.migul.treningsprogram.ui.log.LogWorkoutViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rest-UX batch 2026-07 — items 4 (manual rest times), 2 (session rest-adjustment memory) and
 * 5 (per-exercise timer surviving backgrounding/process death).
 *
 * All logic under test here is pure (policy/parse, estimator override, trim ladder in manual
 * mode, rest-start resolution, exercise-timer start resolution), mirroring the house pattern of
 * unit-testing the seams off-device; on-device behaviour is the user's check after shipping.
 */
class RestUxBatchTest {

    // ── Item 4: heavy-compound classification (assumption A3 — automatic, name-derived) ────────

    @Test fun heavyCompounds_classicLifts() {
        listOf(
            "Barbell Squat", "Front Squat", "Goblet Squat",
            "Deadlift", "Romanian Deadlift", "Sumo Deadlift", "Dumbbell RDL",
            "Bench Press", "Incline Bench Press", "Dumbbell Bench Press",
            "Overhead Press", "Shoulder Press", "Leg Press", "Push Press",
            "Barbell Row", "Seated Cable Row", "Dumbbell Row",
            "Walking Lunge", "Bulgarian Split Squat", "Hip Thrust",
            "Power Clean", "Snatch", "Good Morning",
            "Pull-Up", "Weighted Pull Up", "Chin-Up", "Dips"
        ).forEach {
            assertTrue("$it should be a heavy compound", RestTimePolicy.isHeavyCompound(it))
        }
    }

    @Test fun accessories_isolationAndCore() {
        listOf(
            "Bicep Curl", "Hammer Curl", "Leg Curl", "Leg Extension",
            "Lateral Raise", "Rear Delt Fly", "Cable Fly", "Face Pull",
            "Triceps Pushdown", "Plank", "Crunch", "Russian Twist",
            "Calf Raise", "Wrist Curl", "Back Extension", "Glute Bridge"
        ).forEach {
            assertFalse("$it should be an accessory", RestTimePolicy.isHeavyCompound(it))
        }
    }

    @Test fun accessories_heavyPatternFalsePositivesExcluded() {
        // These CONTAIN a heavy pattern substring but are not heavy compound work.
        listOf(
            "Pallof Press",                       // "press" but anti-rotation core
            "Calf Press on Leg Press Machine",    // "press" but calf isolation
            "Triceps Pressdown",                  // "press" but cable isolation
            "Rowing Machine", "Rower", "Rowing"   // "row" but the cardio machine
        ).forEach {
            assertFalse("$it should NOT be a heavy compound", RestTimePolicy.isHeavyCompound(it))
        }
    }

    @Test fun cardio_isNeverHeavy_A2() {
        listOf("Easy Jog", "Stationary Bike", "Treadmill Walk", "HIIT Circuit").forEach {
            assertFalse("$it (cardio) rests as an accessory", RestTimePolicy.isHeavyCompound(it))
        }
    }

    @Test fun manualRestTimes_restForCategory() {
        val m = ManualRestTimes(heavyCompoundSeconds = 180, accessorySeconds = 90)
        assertEquals(180, m.restSecondsFor("Barbell Squat"))
        assertEquals(90, m.restSecondsFor("Bicep Curl"))
        assertEquals(90, m.restSecondsFor("Easy Jog"))
    }

    // ── Item 4: forgiving m:ss parse + format ───────────────────────────────────────────────────

    @Test fun parseMinSec_acceptedShapes() {
        assertEquals(180, ManualRestTimes.parseMinSec("3:00"))
        assertEquals(90, ManualRestTimes.parseMinSec("1:30"))
        assertEquals(90, ManualRestTimes.parseMinSec(" 1:30 "))
        assertEquals(150, ManualRestTimes.parseMinSec("2.30"))   // keyboard slip for 2:30
        assertEquals(90, ManualRestTimes.parseMinSec("90"))      // plain number = seconds
        assertEquals(45, ManualRestTimes.parseMinSec("0:45"))
    }

    @Test fun parseMinSec_garbageAndBoundsAreSafe() {
        assertNull(ManualRestTimes.parseMinSec(null))
        assertNull(ManualRestTimes.parseMinSec(""))
        assertNull(ManualRestTimes.parseMinSec("abc"))
        assertNull(ManualRestTimes.parseMinSec("3:99"))          // invalid seconds part
        assertNull(ManualRestTimes.parseMinSec("-30"))
        assertNull(ManualRestTimes.parseMinSec("0"))
        assertEquals(ManualRestTimes.MIN_SECONDS, ManualRestTimes.parseMinSec("5"))      // floor 15
        assertEquals(ManualRestTimes.MAX_SECONDS, ManualRestTimes.parseMinSec("15:00"))  // cap 600
    }

    @Test fun formatMinSec_roundTrips() {
        assertEquals("3:00", ManualRestTimes.formatMinSec(180))
        assertEquals("1:30", ManualRestTimes.formatMinSec(90))
        assertEquals("0:45", ManualRestTimes.formatMinSec(45))
        assertEquals(180, ManualRestTimes.parseMinSec(ManualRestTimes.formatMinSec(180)))
    }

    // ── Item 4: estimator counts manual category times ──────────────────────────────────────────

    private fun ex(
        name: String,
        sets: Int,
        reps: String,
        rest: Int,
        order: Int = 0,
        day: Int = 1
    ) = PlannedExercise(
        weekStart = 0L, dayOfWeek = day, orderInDay = order, exerciseName = name,
        sets = sets, targetReps = reps, targetWeightKg = 20f, recommendedRestSeconds = rest,
        notes = "RPE 8 (~2 RIR); double progression +reps then +load"
    )

    @Test fun estimator_manualOverridesAiRest() {
        val manual = ManualRestTimes(heavyCompoundSeconds = 180, accessorySeconds = 90)
        // Squat (heavy): AI said 120 s but the user rests 180 s.
        // 4*(10*4) + 3*180 + 60 = 160 + 540 + 60 = 760 (vs 580 with the AI's 120 s)
        val squat = ex("Barbell Squat", sets = 4, reps = "8-10", rest = 120)
        assertEquals(580, WorkoutTimeEstimator.estimateExerciseSeconds(squat))
        assertEquals(760, WorkoutTimeEstimator.estimateExerciseSeconds(squat, manual))

        // Curl (accessory): AI said 150 s but the user rests 90 s.
        // 3*(12*4) + 2*90 + 60 = 144 + 180 + 60 = 384 (vs 504 with the AI's 150 s)
        val curl = ex("Bicep Curl", sets = 3, reps = "10-12", rest = 150)
        assertEquals(504, WorkoutTimeEstimator.estimateExerciseSeconds(curl))
        assertEquals(384, WorkoutTimeEstimator.estimateExerciseSeconds(curl, manual))

        // Day minutes recompute with the same override: (760 + 384 + 30) / 60 = 19
        assertEquals(19, WorkoutTimeEstimator.estimateDayMinutes(listOf(squat, curl), manual))
    }

    @Test fun estimator_cardioUnaffectedByManualMode() {
        val manual = ManualRestTimes(300, 200)
        val jog = ex("Easy Jog", sets = 1, reps = "30 min", rest = 60)
        assertEquals(
            WorkoutTimeEstimator.estimateExerciseSeconds(jog),
            WorkoutTimeEstimator.estimateExerciseSeconds(jog, manual)
        )
    }

    @Test fun estimator_nullManualIsExistingBehaviour() {
        val bench = ex("Bench Press", sets = 4, reps = "8-10", rest = 120)
        assertEquals(
            WorkoutTimeEstimator.estimateExerciseSeconds(bench),
            WorkoutTimeEstimator.estimateExerciseSeconds(bench, null)
        )
    }

    // ── Item 4: auto-trim in manual mode skips the (dead) rest lever ─────────────────────────────

    @Test fun trim_manualMode_neverTouchesRecommendedRest_andLandsInWindow() {
        val manual = ManualRestTimes(heavyCompoundSeconds = 180, accessorySeconds = 90)
        val target = 50
        // Build a day that is OVER 60 min under MANUAL math:
        // Squat 5x8 @180: 5*32*... => 5*(8*4)=160 + 4*180=720 + 60 = 940 s
        // Bench 4x10 @180: 4*40=160... reps 10 → 4*(10*4)=160 + 3*180=540 + 60 = 760 s
        // Row 4x10 @180: 760 s ; Curl 4x12 @90: 4*48=192 + 3*90=270 + 60 = 522 s
        // Extension 4x12 @90: 522 s → total 3504 s → (3504+30)/60 = 58 min... need more.
        val day = listOf(
            ex("Barbell Squat", 5, "8", 120, order = 0),
            ex("Bench Press", 4, "10", 120, order = 1),
            ex("Barbell Row", 4, "10", 120, order = 2),
            ex("Bicep Curl", 4, "12", 60, order = 3),
            ex("Leg Extension", 4, "12", 60, order = 4),
            ex("Lateral Raise", 4, "15", 60, order = 5)
        )
        val before = WorkoutTimeEstimator.estimateDayMinutes(day, manual)
        assertTrue("precondition: day must be OVER the ceiling under manual math", before > target + 10)

        val trimmed = trimOverflowToWindow(day, target, emptySet(), manual)
        assertNotNull("manual-mode trim should salvage an over day via sets/removal", trimmed)
        trimmed!!

        // The rest lever is DEAD in manual mode: no exercise's stored AI suggestion may change.
        trimmed.forEach { t ->
            val src = day.first { it.exerciseName == t.exerciseName }
            assertEquals(
                "recommendedRestSeconds untouched for ${t.exerciseName}",
                src.recommendedRestSeconds, t.recommendedRestSeconds
            )
        }
        // And the result is in-window under the SAME manual math the gate uses.
        val after = WorkoutTimeEstimator.estimateDayMinutes(trimmed, manual)
        assertTrue("trimmed day in window (got $after)", after in (target - 10)..(target + 10))
    }

    // ── Item 4: duration feedback stops naming the dead rest lever ──────────────────────────────

    @Test fun feedback_manualMode_underDay_steersAwayFromRest() {
        val msg = dayDurationFeedback(1, 30, 50, restIsLever = false)
        assertNotNull(msg)
        assertTrue(msg!!.contains("REST IS NOT A LEVER"))
        assertFalse(msg.contains("raise inter-set REST"))
    }

    @Test fun feedback_aiMode_unchangedWording() {
        val msg = dayDurationFeedback(1, 30, 50)
        assertNotNull(msg)
        assertTrue(msg!!.contains("raise inter-set REST"))
    }

    @Test fun feedback_manualMode_overDay_noShortenRest() {
        val msg = dayDurationFeedback(1, 70, 50, restIsLever = false)
        assertNotNull(msg)
        assertTrue(msg!!.contains("rest times are FIXED"))
        assertFalse(msg.contains("or shorten rest."))
    }

    // ── Items 4 + 2: rest-start resolution (base source + session adjustment + floor) ───────────

    @Test fun restStart_aiMode_usesPlannedRest_withFallback() {
        val r = LogWorkoutViewModel.resolveRestStart("Bench Press", 120, null, 0)
        assertEquals(120, r.seconds)
        assertEquals(120, r.baseSeconds)
        assertFalse(r.isManualSource)
        // Freestyle / no planned exercise → 90 s fallback.
        val f = LogWorkoutViewModel.resolveRestStart("Whatever", null, null, 0)
        assertEquals(90, f.seconds)
    }

    @Test fun restStart_manualMode_usesCategoryTime_regardlessOfAiSuggestion() {
        val manual = ManualRestTimes(180, 90)
        val heavy = LogWorkoutViewModel.resolveRestStart("Barbell Squat", 45, manual, 0)
        assertEquals(180, heavy.seconds)
        assertTrue(heavy.isManualSource)
        val acc = LogWorkoutViewModel.resolveRestStart("Bicep Curl", 240, manual, 0)
        assertEquals(90, acc.seconds)
    }

    @Test fun restStart_adjustmentLayersOnTop_ofEitherBase() {
        // AC (item 2): AI says 90, one +30 press → next sets start at 120.
        val ai = LogWorkoutViewModel.resolveRestStart("Bench Press", 90, null, +30)
        assertEquals(120, ai.seconds)
        assertEquals(90, ai.baseSeconds)
        // Accumulation: +30 +30 → base+60 ; +30 −30 → back to base.
        assertEquals(150, LogWorkoutViewModel.resolveRestStart("Bench Press", 90, null, +60).seconds)
        assertEquals(90, LogWorkoutViewModel.resolveRestStart("Bench Press", 90, null, 0).seconds)
        // AC (item 4d): manual Heavy 3:00, −30 → 2:30 for remaining sets of that exercise.
        val manual = ManualRestTimes(180, 90)
        assertEquals(150, LogWorkoutViewModel.resolveRestStart("Barbell Squat", null, manual, -30).seconds)
    }

    @Test fun restStart_floorPreventsNonsenseTimer() {
        val r = LogWorkoutViewModel.resolveRestStart("Bench Press", 90, null, -300)
        assertEquals(LogWorkoutViewModel.MIN_REST_START_SECONDS, r.seconds)
    }

    // ── Item 5: exercise-timer start survives backgrounding + process death ─────────────────────

    @Test fun exerciseTimer_freshExercise_capturesNowAndPersists() {
        val (start, state) = LogWorkoutViewModel.resolveExerciseTimerStart("", 7L, 2, 1_000_000L)
        assertEquals(1_000_000L, start)
        assertEquals("7|2|1000000", state)
    }

    @Test fun exerciseTimer_matchingPersistedState_isReused() {
        // Same session + same exercise index (i.e. backgrounded or process-killed and resumed)
        // → the ORIGINAL start is kept, so elapsed reflects true wall-clock time.
        val (start, state) = LogWorkoutViewModel.resolveExerciseTimerStart("7|2|1000000", 7L, 2, 1_090_000L)
        assertEquals(1_000_000L, start)
        assertEquals("7|2|1000000", state)
    }

    @Test fun exerciseTimer_indexChange_resets() {
        // Moving to the next/previous exercise resets the timer (unchanged design).
        val (start, state) = LogWorkoutViewModel.resolveExerciseTimerStart("7|2|1000000", 7L, 3, 1_090_000L)
        assertEquals(1_090_000L, start)
        assertEquals("7|3|1090000", state)
    }

    @Test fun exerciseTimer_differentSession_resets() {
        val (start, _) = LogWorkoutViewModel.resolveExerciseTimerStart("7|2|1000000", 8L, 2, 1_090_000L)
        assertEquals(1_090_000L, start)
    }

    @Test fun exerciseTimer_garbageOrFutureState_resetsSafely() {
        assertEquals(500L, LogWorkoutViewModel.resolveExerciseTimerStart("junk", 1L, 0, 500L).first)
        assertEquals(500L, LogWorkoutViewModel.resolveExerciseTimerStart("1|0|notanumber", 1L, 0, 500L).first)
        // A persisted start in the future (clock change) must not produce negative elapsed.
        assertEquals(500L, LogWorkoutViewModel.resolveExerciseTimerStart("1|0|999999", 1L, 0, 500L).first)
    }

    // ── Item 4: backup v5 (manual rest prefs ride through export/import/merge) ──────────────────

    private val gson = com.google.gson.Gson()

    @Test fun backup_v4WithoutManualRestKeys_migratesToDefaults() {
        val v4 = """
            {
              "schema_version": 4,
              "exported_at": "2026-07-01T00:00:00Z",
              "sessions": [], "sets": [], "achievements": [],
              "body_measurements": [], "planned_exercises": [],
              "exercises": [], "gym_presets": [], "programs": [],
              "preferences": { "daysPerWeek": 5 }
            }
        """.trimIndent()
        val envelope = com.migul.treningsprogram.data.backup.BackupMigrations.parseAndMigrate(gson, v4)
        assertFalse(envelope.preferences.manualRestEnabled)
        assertEquals(180, envelope.preferences.manualRestHeavySeconds)
        assertEquals(90, envelope.preferences.manualRestAccessorySeconds)
    }

    @Test fun backup_v5RoundTripsManualRestPrefs() {
        val out = gson.toJson(
            com.migul.treningsprogram.data.backup.BackupEnvelope(
                preferences = com.migul.treningsprogram.data.backup.BackupPreferences(
                    manualRestEnabled = true,
                    manualRestHeavySeconds = 240,
                    manualRestAccessorySeconds = 60
                )
            )
        )
        val back = com.migul.treningsprogram.data.backup.BackupMigrations.parseAndMigrate(gson, out)
        assertTrue(back.preferences.manualRestEnabled)
        assertEquals(240, back.preferences.manualRestHeavySeconds)
        assertEquals(60, back.preferences.manualRestAccessorySeconds)
    }

    @Test fun backup_merge_phoneWinsIfSet_forManualRest() {
        val phone = com.migul.treningsprogram.data.backup.BackupPreferences(
            manualRestEnabled = true, manualRestHeavySeconds = 200
        )
        val backup = com.migul.treningsprogram.data.backup.BackupPreferences(
            manualRestEnabled = false, manualRestHeavySeconds = 240, manualRestAccessorySeconds = 60
        )
        val merged = com.migul.treningsprogram.data.backup.PreferencesMerger.merge(phone, backup)
        assertTrue(merged.manualRestEnabled)                    // phone set it → phone wins
        assertEquals(200, merged.manualRestHeavySeconds)        // phone set it → phone wins
        assertEquals(60, merged.manualRestAccessorySeconds)     // phone default → backup adopted
    }
}
