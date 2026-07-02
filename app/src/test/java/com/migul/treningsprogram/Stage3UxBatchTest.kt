package com.migul.treningsprogram

import com.migul.treningsprogram.domain.DateRangeFilter
import com.migul.treningsprogram.domain.FineMuscleVolume
import com.migul.treningsprogram.domain.HeatmapDrill
import com.migul.treningsprogram.domain.RecentPrs
import com.migul.treningsprogram.domain.RepsProgress
import com.migul.treningsprogram.domain.SessionEarned
import com.migul.treningsprogram.domain.VolumeHeatmap
import com.migul.treningsprogram.ui.history.HistoryRecapFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Stage-3 UX batch (2026-07) — pure-logic coverage for the briefs' unit-testable ACs:
 *  - item 1  (RepsProgress): bodyweight detection for the Progress reps chart.
 *  - item 4  (RecentPrs): Profile shows only PRs earned in the rolling 7-logical-day window.
 *  - item 9  (FineMuscleVolume): recap muscle card at fine granularity, weighted whole sets.
 *  - item 11 (HeatmapDrill): heatmap cell → most recent session that week for that muscle;
 *              broad-group highlight matching in exercisesHittingMuscle.
 *  - items 12/13 (DateRangeFilter): inclusive calendar range on the logical day boundary.
 *  - item 14 (SessionEarned): achievement→session attribution, omit-when-unsure.
 */
class Stage3UxBatchTest {

    private val zone: ZoneId = ZoneId.of("Europe/Oslo")
    private val cutoff = 4

    /** Millis for local date/time in the test zone. */
    private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDate.of(y, m, d).atTime(h, min).atZone(zone).toInstant().toEpochMilli()

    private fun epochDay(y: Int, m: Int, d: Int): Long = LocalDate.of(y, m, d).toEpochDay()

    // ── Items 12/13 — DateRangeFilter ────────────────────────────────────────────────────────

    @Test fun range_nullMeansAll() {
        assertTrue(DateRangeFilter.contains(null, ms(2026, 6, 1), cutoff, zone))
        assertEquals("All time", DateRangeFilter.label(null))
    }

    @Test fun range_inclusiveOnBothEnds() {
        val r = DateRangeFilter.Range(epochDay(2026, 6, 10), epochDay(2026, 6, 12))
        assertTrue(DateRangeFilter.contains(r, ms(2026, 6, 10, 8), cutoff, zone))   // start day
        assertTrue(DateRangeFilter.contains(r, ms(2026, 6, 12, 22), cutoff, zone))  // end day
        assertFalse(DateRangeFilter.contains(r, ms(2026, 6, 9, 23), cutoff, zone))
        assertFalse(DateRangeFilter.contains(r, ms(2026, 6, 13, 8), cutoff, zone))
    }

    @Test fun range_logicalDayBoundary_01amBelongsToPreviousDay() {
        // 01:00 on the 13th is LOGICALLY the 12th (cutoff 04:00) → inside a range ending the 12th.
        val r = DateRangeFilter.Range(epochDay(2026, 6, 10), epochDay(2026, 6, 12))
        assertTrue(DateRangeFilter.contains(r, ms(2026, 6, 13, 1), cutoff, zone))
        // …and 01:00 on the 10th is logically the 9th → OUTSIDE a range starting the 10th.
        assertFalse(DateRangeFilter.contains(r, ms(2026, 6, 10, 1), cutoff, zone))
    }

    @Test fun range_fromPickerUtc_convertsAndOrders() {
        val d10 = epochDay(2026, 6, 10)
        val d12 = epochDay(2026, 6, 12)
        val r = DateRangeFilter.fromPickerUtc(d10 * 86_400_000L, d12 * 86_400_000L)
        assertEquals(DateRangeFilter.Range(d10, d12), r)
        // Reversed input still yields an ordered range.
        assertEquals(r, DateRangeFilter.fromPickerUtc(d12 * 86_400_000L, d10 * 86_400_000L))
    }

    @Test fun range_labelShowsBothEnds_singleDayCollapses() {
        // Locale-robust: assert shape, not month spellings (device locale formats the dates).
        val r = DateRangeFilter.Range(epochDay(2026, 6, 10), epochDay(2026, 7, 2))
        val label = DateRangeFilter.label(r)
        assertTrue(label.contains(" – "))
        assertTrue(label.contains("2026"))
        val single = DateRangeFilter.Range(epochDay(2026, 6, 10), epochDay(2026, 6, 10))
        val singleLabel = DateRangeFilter.label(single)
        assertFalse(singleLabel.contains("–"))
        assertEquals(singleLabel, label.substringBefore(" – "))
    }

    // ── Item 1 — RepsProgress ────────────────────────────────────────────────────────────────

    @Test fun repsChart_shownForAllBodyweightHistory() {
        val history = listOf(
            RepsProgress.Point(1L, 0f, 8),
            RepsProgress.Point(2L, 0f, 10),
        )
        assertTrue(RepsProgress.showRepsChart(history))
    }

    @Test fun repsChart_shownForMixedHistory_weightAddedLater() {
        val history = listOf(
            RepsProgress.Point(1L, 0f, 8),    // bodyweight pull-ups
            RepsProgress.Point(2L, 5f, 6),    // added 5 kg
        )
        assertTrue(RepsProgress.showRepsChart(history))
    }

    @Test fun repsChart_hiddenForOrdinaryLoadedLift() {
        val history = listOf(
            RepsProgress.Point(1L, 60f, 5),
            RepsProgress.Point(2L, 62.5f, 5),
        )
        assertFalse(RepsProgress.showRepsChart(history))
        assertFalse(RepsProgress.showRepsChart(emptyList()))
    }

    // ── Item 4 — RecentPrs ───────────────────────────────────────────────────────────────────

    @Test fun recentPrs_prInsideWindowAppears_beatsBaseline() {
        val today = LocalDate.of(2026, 7, 2).toEpochDay()
        val prs = RecentPrs.compute(
            sets = listOf(RecentPrs.SetSample("Bench Press", 82.5f, ms(2026, 6, 29))),
            baselineMaxByExercise = mapOf("Bench Press" to 80f),
            todayEpochDay = today, cutoffHour = cutoff, zone = zone,
        )
        assertEquals(1, prs.size)
        assertEquals("Bench Press", prs[0].exerciseName)
        assertEquals(82.5f, prs[0].weightKg, 0.001f)
    }

    @Test fun recentPrs_firstEverPerformanceIsBaselineNotPr() {
        val today = LocalDate.of(2026, 7, 2).toEpochDay()
        val prs = RecentPrs.compute(
            sets = listOf(RecentPrs.SetSample("New Lift", 40f, ms(2026, 7, 1))),
            baselineMaxByExercise = emptyMap(),
            todayEpochDay = today, cutoffHour = cutoff, zone = zone,
        )
        assertTrue(prs.isEmpty())
    }

    @Test fun recentPrs_equalOrLowerThanBaselineIsNotPr() {
        val today = LocalDate.of(2026, 7, 2).toEpochDay()
        val prs = RecentPrs.compute(
            sets = listOf(
                RecentPrs.SetSample("Squat", 100f, ms(2026, 7, 1)),   // ties the record
                RecentPrs.SetSample("Deadlift", 120f, ms(2026, 7, 1)) // below the record
            ),
            baselineMaxByExercise = mapOf("Squat" to 100f, "Deadlift" to 140f),
            todayEpochDay = today, cutoffHour = cutoff, zone = zone,
        )
        assertTrue(prs.isEmpty())
    }

    @Test fun recentPrs_olderThan7LogicalDaysRollsOut() {
        val today = LocalDate.of(2026, 7, 2).toEpochDay()
        // PR set on 25 Jun: today(2 Jul) - 25 Jun = 7 days back → OUTSIDE today+6-back window.
        val prs = RecentPrs.compute(
            sets = listOf(RecentPrs.SetSample("Bench Press", 82.5f, ms(2026, 6, 25))),
            baselineMaxByExercise = mapOf("Bench Press" to 80f),
            todayEpochDay = today, cutoffHour = cutoff, zone = zone,
        )
        assertTrue(prs.isEmpty())
        // 26 Jun (exactly 6 days back) is still in.
        val kept = RecentPrs.compute(
            sets = listOf(RecentPrs.SetSample("Bench Press", 82.5f, ms(2026, 6, 26))),
            baselineMaxByExercise = mapOf("Bench Press" to 80f),
            todayEpochDay = today, cutoffHour = cutoff, zone = zone,
        )
        assertEquals(1, kept.size)
    }

    @Test fun recentPrs_multiplePrsSameExercise_latestShown() {
        val today = LocalDate.of(2026, 7, 2).toEpochDay()
        val prs = RecentPrs.compute(
            sets = listOf(
                RecentPrs.SetSample("Bench Press", 82.5f, ms(2026, 6, 28)),
                RecentPrs.SetSample("Bench Press", 85f, ms(2026, 7, 1)),
            ),
            baselineMaxByExercise = mapOf("Bench Press" to 80f),
            todayEpochDay = today, cutoffHour = cutoff, zone = zone,
        )
        assertEquals(1, prs.size)
        assertEquals(85f, prs[0].weightKg, 0.001f)
    }

    @Test fun recentPrs_preWindowSampleOnlyExtendsBaseline() {
        val today = LocalDate.of(2026, 7, 2).toEpochDay()
        // A 90 kg set 8 days ago (outside window, inside fetch) raises the bar; the 85 kg set
        // inside the window is NOT a PR against it.
        val prs = RecentPrs.compute(
            sets = listOf(
                RecentPrs.SetSample("Squat", 90f, ms(2026, 6, 24)),
                RecentPrs.SetSample("Squat", 85f, ms(2026, 7, 1)),
            ),
            baselineMaxByExercise = mapOf("Squat" to 80f),
            todayEpochDay = today, cutoffHour = cutoff, zone = zone,
        )
        assertTrue(prs.isEmpty())
    }

    @Test fun recentPrs_sortedNewestFirst() {
        val today = LocalDate.of(2026, 7, 2).toEpochDay()
        val prs = RecentPrs.compute(
            sets = listOf(
                RecentPrs.SetSample("Squat", 105f, ms(2026, 6, 28)),
                RecentPrs.SetSample("Bench Press", 85f, ms(2026, 7, 1)),
            ),
            baselineMaxByExercise = mapOf("Squat" to 100f, "Bench Press" to 80f),
            todayEpochDay = today, cutoffHour = cutoff, zone = zone,
        )
        assertEquals(listOf("Bench Press", "Squat"), prs.map { it.exerciseName })
    }

    // ── Item 9 — FineMuscleVolume ────────────────────────────────────────────────────────────

    private val fakeResolver: (String) -> List<Pair<String, Float>> = { name ->
        when (name) {
            "Bench Press" -> listOf("Chest" to 1.0f, "Front Delts" to 0.6f, "Triceps" to 0.6f)
            "Tricep Pushdown" -> listOf("Triceps" to 1.0f)
            "Mystery Move" -> emptyList()
            else -> emptyList()
        }
    }

    @Test fun fineMuscles_weightedWholeSetRounding() {
        val rows = FineMuscleVolume.rows(
            listOf("Bench Press" to 9, "Tricep Pushdown" to 6),
            fakeResolver
        )
        // Chest 9·1.0 = 9; Triceps 9·0.6 + 6·1.0 = 11.4 → 11; Front Delts 9·0.6 = 5.4 → 5.
        assertEquals(listOf("Triceps" to 11, "Chest" to 9, "Front Delts" to 5), rows)
    }

    @Test fun fineMuscles_zeroRoundedLabelsDropped_unresolvedIgnored() {
        val rows = FineMuscleVolume.rows(
            listOf("Bench Press" to 0, "Mystery Move" to 4),
            fakeResolver
        )
        assertTrue(rows.isEmpty())
    }

    @Test fun fineMuscles_realClassifierAgreesWithRecoveryPanelTaxonomy() {
        // One taxonomy, no contradictions: the recap card resolves through the same
        // MuscleClassifier.finerMusclesFor the recovery panel uses.
        val rows = FineMuscleVolume.rows(
            listOf("Bench Press" to 6, "Tricep Pushdown" to 3),
            com.migul.treningsprogram.data.MuscleClassifier::finerMusclesFor
        )
        val labels = rows.map { it.first }
        assertTrue("Chest" in labels)
        assertTrue("Triceps" in labels)
        assertFalse("Arms" in labels)   // coarse labels never appear
    }

    // ── Item 11 — HeatmapDrill + broad-group highlight ───────────────────────────────────────

    @Test fun heatmapDrill_mostRecentSessionInWeekForMuscle() {
        val monday = VolumeHeatmap.mondayOf(ms(2026, 6, 24))  // week of 22 Jun
        val rows = listOf(
            HeatmapDrill.Row(1L, ms(2026, 6, 22), "Chest"),
            HeatmapDrill.Row(2L, ms(2026, 6, 25), "Chest"),   // most recent Chest that week
            HeatmapDrill.Row(3L, ms(2026, 6, 26), "Legs"),    // other muscle
            HeatmapDrill.Row(4L, ms(2026, 6, 29), "Chest"),   // next week
        )
        assertEquals(2L, HeatmapDrill.resolve(rows, "Chest", monday))
    }

    @Test fun heatmapDrill_emptyCellResolvesNull() {
        val monday = VolumeHeatmap.mondayOf(ms(2026, 6, 24))
        val rows = listOf(HeatmapDrill.Row(1L, ms(2026, 6, 22), "Chest"))
        assertNull(HeatmapDrill.resolve(rows, "Back", monday))
        assertNull(HeatmapDrill.resolve(emptyList(), "Chest", monday))
    }

    @Test fun highlight_broadGroupLabelMatchesFineExercises() {
        // The heatmap passes BROAD rows ("Arms"); exercises resolve to fine labels that must
        // roll up and still match.
        val session = listOf("Bicep Curl", "Tricep Pushdown", "Back Squat")
        val hit = HistoryRecapFragment.exercisesHittingMuscle(session, "Arms")
        assertEquals(setOf("Bicep Curl", "Tricep Pushdown"), hit)
        // Fine labels keep working exactly as before (B06 recovery-panel path).
        val fine = HistoryRecapFragment.exercisesHittingMuscle(session, "Triceps")
        assertTrue("Tricep Pushdown" in fine)
        assertFalse("Back Squat" in fine)
    }

    // ── Item 14 — SessionEarned ──────────────────────────────────────────────────────────────

    private val w1 = SessionEarned.Window(sessionId = 10L, startMs = ms(2026, 7, 1, 18), durationMinutes = 60)
    private val w2 = SessionEarned.Window(sessionId = 11L, startMs = ms(2026, 7, 2, 18), durationMinutes = 45)

    @Test fun earned_unlockAtCompletionAttributesToThatSession() {
        // Unlock stamped moments after completion: start + 60 min + a few seconds.
        val unlock = ms(2026, 7, 1, 19, 0) + 5_000L
        assertEquals(10L, SessionEarned.attributeTo(unlock, listOf(w1, w2)))
    }

    @Test fun earned_neighboringSessionNeverClaimsIt() {
        val unlock = ms(2026, 7, 1, 19, 0)
        assertEquals(10L, SessionEarned.attributeTo(unlock, listOf(w1, w2)))
        // The next day's session window does not contain it.
        assertNull(SessionEarned.attributeTo(unlock, listOf(w2)))
    }

    @Test fun earned_outsideAnyWindowOmitted() {
        val unlock = ms(2026, 7, 1, 23)   // hours after w1's window closed
        assertNull(SessionEarned.attributeTo(unlock, listOf(w1, w2)))
    }

    @Test fun earned_ambiguousOverlapOmitted_neverWrongPositive() {
        val overlapping = SessionEarned.Window(12L, ms(2026, 7, 1, 18, 30), 60)
        val unlock = ms(2026, 7, 1, 19, 0)
        assertNull(SessionEarned.attributeTo(unlock, listOf(w1, overlapping)))
    }

    @Test fun earned_unstampedLegacyUnlockOmitted() {
        assertNull(SessionEarned.attributeTo(0L, listOf(w1, w2)))
    }

    @Test fun earned_graceCoversDurationRounding() {
        // Duration stored as 59 min but processing ran at +59m40s: still inside the grace.
        val w = SessionEarned.Window(20L, ms(2026, 7, 1, 18), 59)
        val unlock = ms(2026, 7, 1, 18) + 59L * 60_000L + 40_000L
        assertEquals(20L, SessionEarned.attributeTo(unlock, listOf(w)))
    }
}
