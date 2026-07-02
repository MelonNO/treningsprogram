package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.domain.ChallengeProgress
import com.migul.treningsprogram.domain.WeekCompletion
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.model.DailyChallenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4 — shared challenge-progress semantics + the WeekCompletion (Perfect Week) rule. */
class ChallengeProgressTest {

    private fun set(name: String, muscle: String, reps: Int = 8, kg: Float = 50f) =
        WorkoutSet(id = 0, sessionId = 1, exerciseName = name, muscleGroup = muscle,
            setNumber = 1, reps = reps, weightKg = kg, isWarmup = false)

    private fun ch(
        metric: String?, target: Int = 0, progress: Int = 0, param: String = "",
        completed: Boolean = false
    ) = DailyChallenge("id", "Name", "desc", 100, isCompleted = completed,
        metric = metric, target = target, progress = progress, param = param)

    // ── sessionValue per metric ────────────────────────────────────────────────────────────────

    @Test fun sessionValues() {
        val sets = listOf(set("Bench", "Chest"), set("Bench", "Chest"), set("Row", "Back"))
        assertEquals(3, ChallengeProgress.sessionValue(ChallengeProgress.METRIC_SESSION_SETS, "", sets))
        assertEquals(2, ChallengeProgress.sessionValue(ChallengeProgress.METRIC_SESSION_EXERCISES, "", sets))
        assertEquals(3 * 8 * 50, ChallengeProgress.sessionValue(ChallengeProgress.METRIC_SESSION_VOLUME, "", sets))
        assertEquals(2, ChallengeProgress.sessionValue(ChallengeProgress.METRIC_MUSCLE, "Chest", sets))
        assertEquals(0, ChallengeProgress.sessionValue(ChallengeProgress.METRIC_MUSCLE, "Legs", sets))
        assertEquals(1, ChallengeProgress.sessionValue(ChallengeProgress.METRIC_COMPLETE, "", sets))
        assertEquals(1, ChallengeProgress.sessionValue(ChallengeProgress.METRIC_WEEK_WORKOUTS, "", sets))
        assertEquals(1, ChallengeProgress.sessionValue(ChallengeProgress.METRIC_PR, "", sets, listOf("Bench")))
        assertEquals(1, ChallengeProgress.sessionValue(ChallengeProgress.METRIC_PR_EXERCISE, "Bench", sets, listOf("Bench")))
        assertEquals(0, ChallengeProgress.sessionValue(ChallengeProgress.METRIC_PR_EXERCISE, "Squat", sets, listOf("Bench")))
    }

    // ── week accumulation across sessions ─────────────────────────────────────────────────────

    @Test fun weekMetricAccumulatesAcrossSessions() {
        val start = ch(ChallengeProgress.METRIC_WEEK_SETS, target = 10)
        val afterFirst = ChallengeProgress.afterSession(start, List(4) { set("A", "Chest") }, emptyList())
        assertEquals(4, afterFirst.progress)
        assertFalse(afterFirst.isCompleted)
        val afterSecond = ChallengeProgress.afterSession(afterFirst, List(6) { set("B", "Back") }, emptyList())
        assertEquals(10, afterSecond.progress)
        assertTrue(afterSecond.isCompleted)
    }

    @Test fun sessionMetricDoesNotAccumulate() {
        val chal = ch(ChallengeProgress.METRIC_SESSION_SETS, target = 10)
        val after = ChallengeProgress.afterSession(chal, List(6) { set("A", "Chest") }, emptyList())
        assertFalse(after.isCompleted)
        assertEquals(0, after.progress) // session metrics never carry progress between sessions
        val after2 = ChallengeProgress.afterSession(after, List(6) { set("A", "Chest") }, emptyList())
        assertFalse(after2.isCompleted)  // 6 again, not 12
    }

    @Test fun completedChallengeIsNeverReprocessed() {
        val done = ch(ChallengeProgress.METRIC_WEEK_SETS, target = 5, progress = 5, completed = true)
        assertEquals(done, ChallengeProgress.afterSession(done, List(9) { set("A", "Chest") }, emptyList()))
    }

    // ── displays ──────────────────────────────────────────────────────────────────────────────

    @Test fun liveLineShowsCountsForCountables() {
        val line = ChallengeProgress.liveLine(
            listOf(ch(ChallengeProgress.METRIC_SESSION_SETS, target = 10).copy(name = "Volume Day")),
            List(4) { set("A", "Chest") }
        )
        assertEquals("⬜ Volume Day 4/10", line)
    }

    @Test fun liveLineIncludesWeekAccumulation() {
        val line = ChallengeProgress.liveLine(
            listOf(ch(ChallengeProgress.METRIC_WEEK_SETS, target = 40, progress = 31).copy(name = "Outdo")),
            List(4) { set("A", "Chest") }
        )
        assertEquals("⬜ Outdo 35/40", line)
    }

    @Test fun liveLineMarksMetChallenges() {
        val line = ChallengeProgress.liveLine(
            listOf(ch(ChallengeProgress.METRIC_MUSCLE, param = "Chest").copy(name = "Push Day")),
            listOf(set("Bench", "Chest"))
        )
        assertEquals("✅ Push Day", line)
    }

    @Test fun homeSuffixOnlyForWeekCountablesWithProgress() {
        assertEquals("31/40", ChallengeProgress.homeSuffix(ch(ChallengeProgress.METRIC_WEEK_SETS, 40, 31)))
        assertNull(ChallengeProgress.homeSuffix(ch(ChallengeProgress.METRIC_WEEK_SETS, 40, 0)))
        assertNull(ChallengeProgress.homeSuffix(ch(ChallengeProgress.METRIC_SESSION_SETS, 10, 0)))
        assertNull(ChallengeProgress.homeSuffix(ch(ChallengeProgress.METRIC_WEEK_SETS, 40, 31, completed = true)))
    }

    // ── WeekCompletion / Perfect Week rule ────────────────────────────────────────────────────

    private fun planned(day: Int, logged: Boolean) = PlannedExercise(
        weekStart = 0L, dayOfWeek = day, orderInDay = 0, exerciseName = "X",
        sets = 3, targetReps = "8", targetWeightKg = 0f, isLogged = logged
    )

    @Test fun weekCompletionMatchesProgramBarRule() {
        val plan = listOf(planned(1, true), planned(1, false), planned(3, true), planned(5, false))
        // Day counts: day1 has a logged row -> done; day3 done; day5 not. 2/3.
        assertEquals(2 to 3, WeekCompletion.dayCounts(plan))
        assertFalse(WeekCompletion.isPerfect(plan))
    }

    @Test fun perfectWeekRequiresEveryPlannedDayLogged() {
        val perfect = listOf(planned(1, true), planned(3, true), planned(5, true))
        assertTrue(WeekCompletion.isPerfect(perfect))
    }

    @Test fun emptyWeekIsNeverPerfect() {
        assertFalse(WeekCompletion.isPerfect(emptyList())) // A-C2: no plan -> no Perfect Week
    }
}
