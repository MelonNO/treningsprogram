package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.BodyMetric
import com.migul.treningsprogram.domain.BodyDataCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

/**
 * CSV export of the logged body data (2026-08-04 follow-up to the v1.34.0 body-progress batch).
 *
 * Everything asserted here is a decision that would be invisible in a build but wrong in a
 * spreadsheet: the column order the user chose, the ISO date, the decimal separator, whether a
 * derived body fat is blank or fabricated, and whether a logged hip can be dropped on export.
 */
class BodyDataCsvTest {

    private val day = 86_400_000L
    private val utc = ZoneOffset.UTC

    /** Midday on [epochDay] — well away from any day-boundary cutoff. */
    private fun ms(epochDay: Long) = epochDay * day + 12 * 3_600_000L

    /** Epoch day 20000 = 2024-10-04; used wherever a literal date string is asserted. */
    private val d0 = 20_000L

    private fun build(
        measurements: List<BodyMeasurement> = emptyList(),
        metrics: List<BodyMetric> = emptyList(),
        sex: String? = "Male",
        heightCm: Float? = 180f
    ) = BodyDataCsv.build(
        measurements = measurements,
        metrics = metrics,
        sex = sex,
        heightCm = heightCm,
        nowMs = ms(d0),
        cutoffHour = 0,
        zone = utc
    )

    private fun weightCsv(vararg m: BodyMeasurement) =
        BodyDataCsv.weightCsv(m.toList(), cutoffHour = 0, zone = utc)

    private fun measurementsCsv(
        vararg m: BodyMetric,
        sex: String? = "Male",
        heightCm: Float? = 180f
    ) = BodyDataCsv.measurementsCsv(m.toList(), sex, heightCm, cutoffHour = 0, zone = utc)

    // ── The exact shape the user chose ────────────────────────────────────────────────────────

    @Test fun `weight file is date and weightKg, one row per weigh-in`() {
        val csv = weightCsv(BodyMeasurement(id = 1, dateMs = ms(d0), weightKg = 79.2f))
        assertEquals("date,weightKg\n2024-10-04,79.2\n", csv)
    }

    @Test fun `measurements file is date, girths, then derived body fat`() {
        val csv = measurementsCsv(
            BodyMetric(id = 1, dateMs = ms(d0), waistCm = 90f, neckCm = 38f)
        )
        // 21.9 is the equal-weight Navy/RFM average for 180 cm / 90 / 38, computed independently.
        assertEquals("date,waistCm,neckCm,bodyFatPct\n2024-10-04,90.0,38.0,21.9\n", csv)
    }

    @Test fun `rows are oldest first so a spreadsheet charts left to right`() {
        val csv = weightCsv(
            BodyMeasurement(id = 1, dateMs = ms(d0 + 2), weightKg = 78f),
            BodyMeasurement(id = 2, dateMs = ms(d0), weightKg = 80f),
            BodyMeasurement(id = 3, dateMs = ms(d0 + 1), weightKg = 79f)
        )
        assertEquals(
            listOf("date,weightKg", "2024-10-04,80.0", "2024-10-05,79.0", "2024-10-06,78.0"),
            csv.trim().lines()
        )
    }

    // ── Derived body fat: never stored, never fabricated ──────────────────────────────────────

    @Test fun `body fat is blank - not zero - when the entry cannot produce one`() {
        val csv = measurementsCsv(
            BodyMetric(id = 1, dateMs = ms(d0), waistCm = 90f),                 // no neck
            BodyMetric(id = 2, dateMs = ms(d0 + 1), neckCm = 38f),              // no waist
            BodyMetric(id = 3, dateMs = ms(d0 + 2), waistCm = 90f, neckCm = 38f)
        )
        val rows = csv.trim().lines().drop(1)
        assertEquals("2024-10-04,90.0,,", rows[0])
        assertEquals("2024-10-05,,38.0,", rows[1])
        assertEquals("2024-10-06,90.0,38.0,21.9", rows[2])
    }

    @Test fun `no height or sex on the profile means blank body fat, girths still export`() {
        val entry = BodyMetric(id = 1, dateMs = ms(d0), waistCm = 90f, neckCm = 38f)

        val noHeight = measurementsCsv(entry, heightCm = null).trim().lines()[1]
        assertEquals("2024-10-04,90.0,38.0,", noHeight)

        val noSex = measurementsCsv(entry, sex = "").trim().lines()[1]
        assertEquals("2024-10-04,90.0,38.0,", noSex)
    }

    @Test fun `female profile without a hip has no body fat, but the entry still exports`() {
        val csv = measurementsCsv(
            BodyMetric(id = 1, dateMs = ms(d0), waistCm = 75f, neckCm = 32f),
            sex = "Female",
            heightCm = 165f
        )
        assertEquals("date,waistCm,neckCm,hipCm,bodyFatPct\n2024-10-04,75.0,32.0,,\n", csv)
    }

    @Test fun `female profile with a hip gets the full Navy-RFM average`() {
        val csv = measurementsCsv(
            BodyMetric(id = 1, dateMs = ms(d0), waistCm = 75f, neckCm = 32f, hipCm = 95f),
            sex = "Female",
            heightCm = 165f
        )
        // 29.7 for 165 cm / 75 / 32 / 95, computed independently.
        assertEquals("date,waistCm,neckCm,hipCm,bodyFatPct\n2024-10-04,75.0,32.0,95.0,29.7\n", csv)
    }

    @Test fun `derived body fat is rounded to one decimal, not raw estimator output`() {
        val row = measurementsCsv(
            BodyMetric(id = 1, dateMs = ms(d0), waistCm = 95f, neckCm = 38f)
        ).trim().lines()[1]
        // Raw average is 24.6668…; the file must carry the same single decimal the app displays.
        assertEquals("2024-10-04,95.0,38.0,24.7", row)
    }

    // ── The hip column ────────────────────────────────────────────────────────────────────────

    @Test fun `male profile with no hip data gets no hip column`() {
        val metrics = listOf(BodyMetric(id = 1, dateMs = ms(d0), waistCm = 90f, neckCm = 38f))
        assertFalse(BodyDataCsv.includesHip(metrics, "Male"))
        assertFalse(measurementsCsv(metrics.first()).contains("hipCm"))
    }

    @Test fun `female profile always gets a hip column, even before any hip is logged`() {
        assertTrue(BodyDataCsv.includesHip(emptyList(), "Female"))
    }

    @Test fun `logged hip data is never dropped, even on a male or unset profile`() {
        // A user who logged hips and later changed their profile sex must not lose them on export.
        val metrics = listOf(BodyMetric(id = 1, dateMs = ms(d0), waistCm = 90f, hipCm = 100f))
        assertTrue(BodyDataCsv.includesHip(metrics, "Male"))
        assertTrue(BodyDataCsv.includesHip(metrics, ""))

        val csv = measurementsCsv(metrics.first())
        assertEquals("date,waistCm,neckCm,hipCm,bodyFatPct\n2024-10-04,90.0,,100.0,\n", csv)
    }

    // ── Locale: the trap that silently corrupts every row ─────────────────────────────────────

    @Test fun `decimals are always dots, even under a comma-decimal default locale`() {
        val original = Locale.getDefault()
        try {
            // Norway (the app's own locale) formats 21.9 as "21,9" — in a comma-separated file that
            // silently becomes an extra column and shifts every field after it.
            Locale.setDefault(Locale.forLanguageTag("nb-NO"))

            val weight = weightCsv(BodyMeasurement(id = 1, dateMs = ms(d0), weightKg = 79.2f))
            assertEquals("date,weightKg\n2024-10-04,79.2\n", weight)

            val measurements = measurementsCsv(
                BodyMetric(id = 1, dateMs = ms(d0), waistCm = 90f, neckCm = 38f)
            )
            assertEquals("date,waistCm,neckCm,bodyFatPct\n2024-10-04,90.0,38.0,21.9\n", measurements)

            // Every data row must have exactly as many fields as the header.
            measurements.trim().lines().forEach { assertEquals(4, it.split(',').size) }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `logged precision is preserved - nothing the user typed is rounded`() {
        val csv = weightCsv(BodyMeasurement(id = 1, dateMs = ms(d0), weightKg = 79.25f))
        assertEquals("date,weightKg\n2024-10-04,79.25\n", csv)
    }

    // ── Dates ─────────────────────────────────────────────────────────────────────────────────

    @Test fun `dates are the app's logical day, matching what the Body tab prints`() {
        // 01:00 on epoch day 20001 with a 04:00 cutoff still belongs to the previous logical day,
        // so the export must not disagree with the date shown next to the entry in the app.
        val lateNight = 20_001L * day + 1 * 3_600_000L
        val csv = BodyDataCsv.weightCsv(
            listOf(BodyMeasurement(id = 1, dateMs = lateNight, weightKg = 80f)),
            cutoffHour = 4,
            zone = utc
        )
        assertEquals("date,weightKg\n2024-10-04,80.0\n", csv)
    }

    // ── File set and naming ───────────────────────────────────────────────────────────────────

    @Test fun `no logged data at all produces no files`() {
        assertTrue(build().isEmpty())
    }

    @Test fun `weight-only user gets one file, not an empty measurements file`() {
        val files = build(measurements = listOf(BodyMeasurement(id = 1, dateMs = ms(d0), weightKg = 80f)))
        assertEquals(1, files.size)
        assertEquals("treningsprogram-weight-2024-10-04.csv", files.single().fileName)
    }

    @Test fun `measurements-only user gets one file, not an empty weight file`() {
        val files = build(metrics = listOf(BodyMetric(id = 1, dateMs = ms(d0), waistCm = 90f)))
        assertEquals(1, files.size)
        assertEquals("treningsprogram-measurements-2024-10-04.csv", files.single().fileName)
    }

    @Test fun `both series present produces both files, weight first`() {
        val files = build(
            measurements = listOf(BodyMeasurement(id = 1, dateMs = ms(d0), weightKg = 80f)),
            metrics = listOf(BodyMetric(id = 1, dateMs = ms(d0), waistCm = 90f, neckCm = 38f))
        )
        assertEquals(
            listOf("treningsprogram-weight-2024-10-04.csv", "treningsprogram-measurements-2024-10-04.csv"),
            files.map { it.fileName }
        )
        assertEquals("date,weightKg\n2024-10-04,80.0\n", files[0].content)
        assertEquals("date,waistCm,neckCm,bodyFatPct\n2024-10-04,90.0,38.0,21.9\n", files[1].content)
    }

    @Test fun `filenames are date-stamped and prefixed like the JSON backup`() {
        assertEquals("treningsprogram-weight-2024-10-04.csv", BodyDataCsv.fileName("weight", "2024-10-04"))
    }
}
