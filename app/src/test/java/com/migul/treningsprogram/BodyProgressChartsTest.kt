package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.BodyMetric
import com.migul.treningsprogram.domain.BodyComposition
import com.migul.treningsprogram.domain.BodyProgressCharts
import com.migul.treningsprogram.domain.BodyProgressRange
import com.migul.treningsprogram.domain.DateRangeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Body-progress batch 2026-08-04 (brief 02) — the chart series builder and the time-scale presets.
 */
class BodyProgressChartsTest {

    private val day = 86_400_000L
    private fun ms(epochDay: Long) = epochDay * day + 12 * 3_600_000L  // midday, away from the boundary

    // ── Series ────────────────────────────────────────────────────────────────────────────────

    @Test fun `each series is independent and sparse - nothing is interpolated`() {
        val weights = listOf(
            BodyMeasurement(id = 1, dateMs = ms(10), weightKg = 80f),
            BodyMeasurement(id = 2, dateMs = ms(11), weightKg = 79.5f),
            BodyMeasurement(id = 3, dateMs = ms(12), weightKg = 79f)
        )
        val metrics = listOf(BodyMetric(id = 1, dateMs = ms(11), waistCm = 88f))

        val s = BodyProgressCharts.build(weights, metrics, null, "Male", 180f)

        assertEquals(3, s.weight.size)
        assertEquals(1, s.waist.size)          // NOT padded out to match the weight series
        assertTrue(s.neck.isEmpty())
        assertTrue(s.hip.isEmpty())
    }

    @Test fun `weight series comes from body_measurements - the Home quick-add path`() {
        val weights = listOf(BodyMeasurement(id = 1, dateMs = ms(5), weightKg = 82.5f))
        val s = BodyProgressCharts.build(weights, emptyList(), null, "", 0f)
        assertEquals(listOf(BodyProgressCharts.Point(ms(5), 82.5f)), s.weight)
    }

    @Test fun `body fat only for entries with waist and neck (decision 4)`() {
        val metrics = listOf(
            BodyMetric(id = 1, dateMs = ms(1), waistCm = 90f),                 // no neck -> no point
            BodyMetric(id = 2, dateMs = ms(2), neckCm = 38f),                  // no waist -> no point
            BodyMetric(id = 3, dateMs = ms(3), waistCm = 90f, neckCm = 38f)    // qualifies
        )
        val s = BodyProgressCharts.build(emptyList(), metrics, null, "Male", 180f)
        assertEquals(1, s.bodyFat.size)
        assertEquals(ms(3), s.bodyFat.single().dateMs)
        assertEquals(
            BodyComposition.estimate("Male", 180f, 90f, 38f)!!,
            s.bodyFat.single().value,
            0.0001f
        )
    }

    @Test fun `no profile means no body fat, but girths still chart (A5)`() {
        val metrics = listOf(BodyMetric(id = 1, dateMs = ms(3), waistCm = 90f, neckCm = 38f))
        val s = BodyProgressCharts.build(emptyList(), metrics, null, "", 0f)
        assertTrue("must never fabricate a percentage", s.bodyFat.isEmpty())
        assertEquals(1, s.waist.size)
        assertEquals(1, s.neck.size)
    }

    @Test fun `setting the profile later lights up historical entries (A5)`() {
        val metrics = listOf(BodyMetric(id = 1, dateMs = ms(3), waistCm = 90f, neckCm = 38f))
        assertTrue(BodyProgressCharts.build(emptyList(), metrics, null, "", 0f).bodyFat.isEmpty())
        // Same stored rows, profile now filled in — the derived series appears retroactively.
        assertEquals(1, BodyProgressCharts.build(emptyList(), metrics, null, "Male", 180f).bodyFat.size)
    }

    @Test fun `a woman needs a hip for body fat, and gets a hip series (A4 + decision 2)`() {
        val withoutHip = listOf(BodyMetric(id = 1, dateMs = ms(3), waistCm = 75f, neckCm = 32f))
        val withHip = listOf(BodyMetric(id = 1, dateMs = ms(3), waistCm = 75f, neckCm = 32f, hipCm = 98f))

        assertTrue(BodyProgressCharts.build(emptyList(), withoutHip, null, "Female", 165f).bodyFat.isEmpty())

        val s = BodyProgressCharts.build(emptyList(), withHip, null, "Female", 165f)
        assertEquals(1, s.bodyFat.size)
        assertEquals(1, s.hip.size)
    }

    @Test fun `a male profile never gets a hip series even if a row somehow has one`() {
        val metrics = listOf(BodyMetric(id = 1, dateMs = ms(3), waistCm = 90f, neckCm = 38f, hipCm = 100f))
        val s = BodyProgressCharts.build(emptyList(), metrics, null, "Male", 180f)
        assertTrue(s.hip.isEmpty())
    }

    @Test fun `the range windows every series`() {
        val weights = (1L..30L).map { BodyMeasurement(id = it, dateMs = ms(it), weightKg = 80f) }
        val metrics = (1L..30L).map { BodyMetric(id = it, dateMs = ms(it), waistCm = 90f) }
        val range = DateRangeFilter.Range(20, 25)

        val s = BodyProgressCharts.build(weights, metrics, range, "Male", 180f)
        assertEquals(6, s.weight.size)
        assertEquals(6, s.waist.size)
    }

    @Test fun `series are sorted oldest-first so charts draw left to right`() {
        val weights = listOf(
            BodyMeasurement(id = 1, dateMs = ms(9), weightKg = 81f),
            BodyMeasurement(id = 2, dateMs = ms(3), weightKg = 83f),
            BodyMeasurement(id = 3, dateMs = ms(6), weightKg = 82f)
        )
        val s = BodyProgressCharts.build(weights, emptyList(), null, "", 0f)
        assertEquals(listOf(ms(3), ms(6), ms(9)), s.weight.map { it.dateMs })
    }

    @Test fun `isEmpty reports a genuinely empty tab`() {
        assertTrue(BodyProgressCharts.build(emptyList(), emptyList(), null, "Male", 180f).isEmpty)
        assertTrue(
            !BodyProgressCharts.build(
                listOf(BodyMeasurement(id = 1, dateMs = ms(1), weightKg = 80f)),
                emptyList(), null, "Male", 180f
            ).isEmpty
        )
    }

    // ── Recent entries (the delete list) ──────────────────────────────────────────────────────

    @Test fun `a combined save shows as ONE row, not two`() {
        val t = ms(4)
        val rows = BodyProgressCharts.recentEntries(
            measurements = listOf(BodyMeasurement(id = 7, dateMs = t, weightKg = 80f)),
            metrics = listOf(BodyMetric(id = 9, dateMs = t, waistCm = 90f, neckCm = 38f)),
            sex = "Male", heightCm = 180f
        )
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(80f, row.weightKg!!, 0.0001f)
        assertEquals(90f, row.waistCm!!, 0.0001f)
        assertEquals(7L, row.measurementId)
        assertEquals(9L, row.metricId)
        assertTrue("body fat should be derived for a qualifying row", row.bodyFatPercent != null)
    }

    @Test fun `a Home quick-add weight stands alone and is still deletable`() {
        val rows = BodyProgressCharts.recentEntries(
            measurements = listOf(BodyMeasurement(id = 7, dateMs = ms(4), weightKg = 80f)),
            metrics = emptyList(), sex = "Male", heightCm = 180f
        )
        assertEquals(1, rows.size)
        assertEquals(7L, rows.single().measurementId)
        assertNull(rows.single().metricId)
        assertNull(rows.single().waistCm)
    }

    @Test fun `a girth-only entry stands alone`() {
        val rows = BodyProgressCharts.recentEntries(
            measurements = emptyList(),
            metrics = listOf(BodyMetric(id = 3, dateMs = ms(4), waistCm = 90f)),
            sex = "Male", heightCm = 180f
        )
        assertEquals(1, rows.size)
        assertNull(rows.single().measurementId)
        assertEquals(3L, rows.single().metricId)
    }

    @Test fun `recent entries are newest-first and capped`() {
        val ms20 = (1L..20L).map { BodyMeasurement(id = it, dateMs = ms(it), weightKg = 80f) }
        val rows = BodyProgressCharts.recentEntries(ms20, emptyList(), "Male", 180f, limit = 5)
        assertEquals(5, rows.size)
        assertEquals(ms(20), rows.first().dateMs)
        assertEquals(ms(16), rows.last().dateMs)
    }

    @Test fun `recent entries hide hip from a male profile`() {
        val rows = BodyProgressCharts.recentEntries(
            measurements = emptyList(),
            metrics = listOf(BodyMetric(id = 1, dateMs = ms(4), waistCm = 90f, hipCm = 100f)),
            sex = "Male", heightCm = 180f
        )
        assertNull(rows.single().hipCm)
    }

    // ── Deleting earlier entries of BOTH kinds (user request 2026-08-04) ──────────────────────

    @Test fun `earlier weight entries are reachable, not cut off at ten`() {
        // 40 weigh-ins — the kind of history a daily logger builds in six weeks. The oldest must be
        // reachable, or "delete an earlier entry" is impossible.
        val weights = (1L..40L).map { BodyMeasurement(id = it, dateMs = ms(it), weightKg = 80f) }

        val collapsed = BodyProgressCharts.recentEntries(weights, emptyList(), "Male", 180f)
        assertEquals(BodyProgressCharts.DEFAULT_ENTRY_LIMIT, collapsed.size)
        assertTrue("the oldest entry is NOT reachable in the collapsed list",
            collapsed.none { it.measurementId == 1L })

        val all = BodyProgressCharts.recentEntries(
            weights, emptyList(), "Male", 180f, BodyProgressCharts.NO_LIMIT
        )
        assertEquals(40, all.size)
        assertTrue("the oldest weigh-in must be deletable", all.any { it.measurementId == 1L })
    }

    @Test fun `earlier girth entries are reachable too`() {
        val metrics = (1L..40L).map { BodyMetric(id = it, dateMs = ms(it), waistCm = 90f, neckCm = 38f) }
        val all = BodyProgressCharts.recentEntries(
            emptyList(), metrics, "Male", 180f, BodyProgressCharts.NO_LIMIT
        )
        assertEquals(40, all.size)
        assertTrue(all.any { it.metricId == 1L })
    }

    @Test fun `both kinds appear in one list, newest first, however they were logged`() {
        val weights = listOf(
            BodyMeasurement(id = 1, dateMs = ms(1), weightKg = 80f),   // Home quick-add, weight only
            BodyMeasurement(id = 2, dateMs = ms(3), weightKg = 79f)    // half of a combined save
        )
        val metrics = listOf(
            BodyMetric(id = 5, dateMs = ms(2), waistCm = 90f),          // girths only
            BodyMetric(id = 6, dateMs = ms(3), waistCm = 89f, neckCm = 38f)  // the other half
        )
        val rows = BodyProgressCharts.recentEntries(
            weights, metrics, "Male", 180f, BodyProgressCharts.NO_LIMIT
        )

        assertEquals("the combined save must collapse to ONE line", 3, rows.size)
        assertEquals(listOf(ms(3), ms(2), ms(1)), rows.map { it.dateMs })
        // Every kind is represented and every row carries the id(s) a delete needs.
        assertTrue(rows.any { it.hasWeight && !it.hasMetrics })   // weight only
        assertTrue(rows.any { !it.hasWeight && it.hasMetrics })   // girths only
        assertTrue(rows.any { it.isCombined })
        assertTrue(rows.all { it.measurementId != null || it.metricId != null })
    }

    @Test fun `only a combined entry is flagged as needing a delete choice`() {
        val weightOnly = BodyProgressCharts.recentEntries(
            listOf(BodyMeasurement(id = 1, dateMs = ms(1), weightKg = 80f)), emptyList(), "Male", 180f
        ).single()
        val girthOnly = BodyProgressCharts.recentEntries(
            emptyList(), listOf(BodyMetric(id = 1, dateMs = ms(1), waistCm = 90f)), "Male", 180f
        ).single()
        val combined = BodyProgressCharts.recentEntries(
            listOf(BodyMeasurement(id = 1, dateMs = ms(1), weightKg = 80f)),
            listOf(BodyMetric(id = 2, dateMs = ms(1), waistCm = 90f)), "Male", 180f
        ).single()

        assertTrue(weightOnly.hasWeight && !weightOnly.isCombined)
        assertTrue(girthOnly.hasMetrics && !girthOnly.isCombined)
        assertTrue(combined.isCombined)
    }

    @Test fun `the three delete parts map to the right underlying rows`() {
        val row = BodyProgressCharts.recentEntries(
            listOf(BodyMeasurement(id = 7, dateMs = ms(1), weightKg = 80f)),
            listOf(BodyMetric(id = 9, dateMs = ms(1), waistCm = 90f, neckCm = 38f)),
            "Male", 180f
        ).single()

        // These are the EXACT properties BodyProgressViewModel.deleteEntry branches on — asserting
        // the enum itself rather than a copy of the rule. WEIGHT_ONLY must leave the girth row
        // alone and METRICS_ONLY must leave the weigh-in alone, so mistyping one half of a combined
        // save never costs the other half.
        val all = BodyProgressCharts.DeletePart.ALL
        val weightOnly = BodyProgressCharts.DeletePart.WEIGHT_ONLY
        val metricsOnly = BodyProgressCharts.DeletePart.METRICS_ONLY

        assertTrue(all.removesWeight && all.removesMetrics)
        assertTrue(weightOnly.removesWeight)
        assertTrue("deleting the weight must NOT drop the girths", !weightOnly.removesMetrics)
        assertTrue(metricsOnly.removesMetrics)
        assertTrue("deleting the girths must NOT drop the weigh-in", !metricsOnly.removesWeight)

        // …and the row carries both ids, so either half can actually be targeted.
        assertEquals(7L, row.measurementId)
        assertEquals(9L, row.metricId)
    }

    @Test fun `a single-kind entry is unaffected by the part it is deleted with`() {
        // A weight-only row has no metricId, so METRICS_ONLY would be a no-op on it — the UI never
        // offers the choice for these, and the data model makes the wrong choice harmless anyway.
        val weightOnly = BodyProgressCharts.recentEntries(
            listOf(BodyMeasurement(id = 7, dateMs = ms(1), weightKg = 80f)), emptyList(), "Male", 180f
        ).single()
        assertNull(weightOnly.metricId)
        assertEquals(7L, weightOnly.measurementId)
    }

    // ── Time scale presets ────────────────────────────────────────────────────────────────────

    @Test fun `the default window is 3 months (decision 7)`() {
        assertEquals(BodyProgressRange.Preset.M3, BodyProgressRange.DEFAULT)
    }

    @Test fun `all five presets are offered in order`() {
        assertEquals(
            listOf("1M", "3M", "6M", "1Y", "All"),
            BodyProgressRange.PRESETS.map { it.label }
        )
    }

    @Test fun `All means no bound`() {
        assertNull(BodyProgressRange.rangeFor(BodyProgressRange.Preset.ALL, 20_000L))
    }

    @Test fun `presets end today and start the right number of months back`() {
        val today = LocalDate.of(2026, 8, 4).toEpochDay()
        val m3 = BodyProgressRange.rangeFor(BodyProgressRange.Preset.M3, today)!!
        assertEquals(today, m3.endEpochDay)
        assertEquals(LocalDate.of(2026, 5, 4).toEpochDay(), m3.startEpochDay)

        val y1 = BodyProgressRange.rangeFor(BodyProgressRange.Preset.Y1, today)!!
        assertEquals(LocalDate.of(2025, 8, 4).toEpochDay(), y1.startEpochDay)
    }

    @Test fun `month arithmetic clamps at short months instead of drifting`() {
        val today = LocalDate.of(2026, 3, 31).toEpochDay()
        val m1 = BodyProgressRange.rangeFor(BodyProgressRange.Preset.M1, today)!!
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), m1.startEpochDay)
    }

    @Test fun `label prefers the custom range over the preset`() {
        val custom = DateRangeFilter.Range(
            LocalDate.of(2026, 6, 5).toEpochDay(), LocalDate.of(2026, 7, 2).toEpochDay()
        )
        assertEquals("3M", BodyProgressRange.labelFor(BodyProgressRange.Preset.M3, null))
        assertEquals(DateRangeFilter.label(custom), BodyProgressRange.labelFor(null, custom))
        assertEquals(DateRangeFilter.label(custom), BodyProgressRange.labelFor(BodyProgressRange.Preset.M3, custom))
    }
}
