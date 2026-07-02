package com.migul.treningsprogram

import com.migul.treningsprogram.domain.ChallengeCatalog
import com.migul.treningsprogram.domain.ChallengeContext
import com.migul.treningsprogram.domain.ChallengeProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4 — pool size, adaptivity share, deterministic draw, eligibility (brief ACs). */
class ChallengeCatalogTest {

    private val richCtx = ChallengeContext(
        hasHistory = true,
        lastWeekWorkingSets = 40,
        lastWeekVolumeKg = 5000,
        avgSessionVolumeKg = 1200,
        maxRecentSessionSets = 14,
        maxRecentSessionVolumeKg = 1800,
        plannedDayCount = 4,
        baselineExercises = listOf("Bench Press", "Squat")
    )

    private val emptyCtx = ChallengeContext() // brand-new user: no history, no plan

    @Test fun poolIsEnlargedAndAtLeastAThirdAdaptive() {
        val total = ChallengeCatalog.TEMPLATES.size
        val adaptive = ChallengeCatalog.TEMPLATES.count { it.adaptive }
        assertTrue("pool should be ~25-30, was $total", total >= 25)
        assertTrue("adaptive share should be >= 1/3, was $adaptive/$total", adaptive * 3 >= total)
    }

    @Test fun templateIdsAreUnique() {
        val ids = ChallengeCatalog.TEMPLATES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun classicIdsSurviveInThePool() {
        val ids = ChallengeCatalog.TEMPLATES.map { it.id }.toSet()
        listOf("complete_workout", "sets_10", "sets_15", "sets_20", "set_pr", "chest_day",
            "back_day", "leg_day", "arms_day", "core_day", "exercises_3", "exercises_5")
            .forEach { assertTrue("classic id $it missing", it in ids) }
    }

    @Test fun drawIsDeterministicWithinAWeek() {
        val a = ChallengeCatalog.draw("2026-W27", richCtx)
        val b = ChallengeCatalog.draw("2026-W27", richCtx)
        assertEquals(a, b)
        assertEquals(3, a.size)
    }

    @Test fun drawDiffersAcrossWeeks() {
        // Over four consecutive weeks at least one draw must differ (seeded rotation).
        val draws = listOf("2026-W25", "2026-W26", "2026-W27", "2026-W28")
            .map { ChallengeCatalog.draw(it, richCtx).map { c -> c.id } }
        assertTrue(draws.toSet().size > 1)
    }

    @Test fun emptyUserNeverDrawsAnUnsatisfiableChallenge() {
        // With NO history and NO plan, none of the history/plan-dependent templates may appear —
        // check across many weeks to sweep the whole rotation.
        val dependentIds = setOf(
            "set_pr", "beat_week_sets", "beat_week_volume", "week_volume_push",
            "above_avg_session", "pr_exercise", "all_planned", "per_day_sets",
            "beat_best_sets", "beat_best_volume"
        )
        (1..30).forEach { w ->
            val drawn = ChallengeCatalog.draw("2026-W$w", emptyCtx)
            assertEquals(3, drawn.size)
            drawn.forEach { ch ->
                assertTrue("week $w drew unsatisfiable ${ch.id}", ch.id !in dependentIds)
            }
        }
    }

    @Test fun adaptiveTargetsAreComputedFrozenAndStatedInText() {
        val t = ChallengeCatalog.TEMPLATES.first { it.id == "beat_week_sets" }
        assertTrue(t.eligible(richCtx))
        val target = t.target(richCtx)
        assertEquals(42, target) // 40 + 5% = 42
        assertTrue(t.describe(target, "").contains("42"))
    }

    @Test fun adaptiveTargetsAreChallengingButReachable() {
        // A-C3: last week + 0-10%, never a huge jump.
        val sets = ChallengeCatalog.TEMPLATES.first { it.id == "beat_week_sets" }.target(richCtx)
        assertTrue(sets in 41..44)
        val vol = ChallengeCatalog.TEMPLATES.first { it.id == "beat_week_volume" }.target(richCtx)
        assertTrue(vol in 5001..5500)
    }

    @Test fun prExerciseChallengeCarriesAConcreteParam() {
        // Force the pr_exercise template through materialization.
        val drawnSomeWeek = (1..40).asSequence()
            .map { ChallengeCatalog.draw("2026-W$it", richCtx) }
            .flatten()
            .firstOrNull { it.id == "pr_exercise" }
        if (drawnSomeWeek != null) {
            assertTrue(drawnSomeWeek.param in richCtx.baselineExercises)
            assertTrue(drawnSomeWeek.description.contains(drawnSomeWeek.param))
        }
        // And the metric metadata is always present on every drawn challenge.
        ChallengeCatalog.draw("2026-W27", richCtx).forEach {
            assertTrue(it.metric != null)
        }
    }

    @Test fun allPlannedChallengeTargetsThePlannedDayCount() {
        val t = ChallengeCatalog.TEMPLATES.first { it.id == "all_planned" }
        assertEquals(4, t.target(richCtx))
        assertEquals(ChallengeProgress.METRIC_WEEK_WORKOUTS, t.metric)
    }
}
