package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.BodyMetric
import java.time.ZoneId
import java.util.Locale

/**
 * CSV export of the logged body data (2026-08-04 follow-up to the v1.34.0 body-progress batch).
 *
 * Produces the two spreadsheet files the user asked for, from the two tables the body data actually
 * lives in:
 *
 * ```
 * weight.csv        date,weightKg
 * measurements.csv  date,waistCm,neckCm[,hipCm],bodyFatPct
 * ```
 *
 * Pure object with no Android and no I/O, so every rule below is JVM-unit-tested rather than trusted
 * to a fragment. The caller writes [CsvFile.content] to disk and shares it.
 *
 * ## The rules encoded here
 *
 * **Body fat is DERIVED, never read from storage.** The app has no body-fat column by design (see
 * [BodyMetric]); the percentage comes from [BodyComposition.estimate] against the profile's CURRENT
 * height/sex, exactly as the Body tab's chart does. An entry that cannot produce a number — no neck,
 * no waist, no height/sex on the profile, or a female profile with no hip — exports an EMPTY field
 * rather than a zero or a placeholder string, so a spreadsheet reads it as a blank cell and charts
 * skip it instead of plotting a fake 0 %.
 *
 * **Dates are the app's LOGICAL day.** [DayBoundary.logicalDate] is what the Body tab prints next to
 * each entry, so a late-night entry exports under the same date the user sees in the app rather than
 * the wall-clock one. The rendering is [java.time.LocalDate.toString] = ISO-8601 `YYYY-MM-DD`, which
 * every spreadsheet parses as a real date and which sorts correctly as plain text.
 *
 * **Numbers are always [Locale.US]-formatted.** This is load-bearing, not cosmetic: on a Norwegian
 * device the default locale renders a decimal as `79,2`, which in a comma-separated file silently
 * becomes two columns and corrupts every row. Logged values keep their full stored precision
 * (shortest round-trip form, so `79.2f` stays `79.2` and `79.25f` stays `79.25`); the derived body
 * fat is rounded to one decimal to match how the app displays it and to avoid inventing precision an
 * estimate does not have.
 *
 * No field can contain a comma, quote or newline — the schema is a fixed set of numbers plus an ISO
 * date — so no RFC-4180 quoting is emitted. Anything textual added here later would need it.
 */
object BodyDataCsv {

    /** One file to write and share. [fileName] already carries the date stamp. */
    data class CsvFile(val fileName: String, val content: String)

    const val WEIGHT_HEADER = "date,weightKg"
    private const val NEWLINE = "\n"

    /**
     * Filenames are prefixed and date-stamped like the JSON backup
     * (`treningsprogram-backup-YYYY-MM-DD.json`) rather than being bare `weight.csv` —
     * a bare name collides with every other export in a Downloads folder and carries no provenance.
     */
    fun fileName(base: String, dateStamp: String): String = "treningsprogram-$base-$dateStamp.csv"

    /**
     * Both files, oldest row first.
     *
     * A series with no rows is OMITTED entirely rather than exported as a lone header line: a user
     * who has only ever used the Home weight quick-add should get one useful file, not one useful
     * file plus an empty one. When BOTH series are empty the result is an empty list and the caller
     * should tell the user there is nothing to export instead of opening a share sheet.
     *
     * [heightCm] and [sex] come from the profile and are only used to derive body fat.
     */
    fun build(
        measurements: List<BodyMeasurement>,
        metrics: List<BodyMetric>,
        sex: String?,
        heightCm: Float?,
        nowMs: Long = System.currentTimeMillis(),
        cutoffHour: Int = DayBoundary.cutoffHour,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CsvFile> {
        val stamp = DayBoundary.logicalDate(nowMs, cutoffHour, zone).toString()
        val files = mutableListOf<CsvFile>()

        if (measurements.isNotEmpty()) {
            files.add(
                CsvFile(
                    fileName = fileName("weight", stamp),
                    content = weightCsv(measurements, cutoffHour, zone)
                )
            )
        }
        if (metrics.isNotEmpty()) {
            files.add(
                CsvFile(
                    fileName = fileName("measurements", stamp),
                    content = measurementsCsv(metrics, sex, heightCm, cutoffHour, zone)
                )
            )
        }
        return files
    }

    /** `date,weightKg` — the body-weight series, oldest first. */
    fun weightCsv(
        measurements: List<BodyMeasurement>,
        cutoffHour: Int = DayBoundary.cutoffHour,
        zone: ZoneId = ZoneId.systemDefault()
    ): String = buildString {
        append(WEIGHT_HEADER).append(NEWLINE)
        measurements.sortedBy { it.dateMs }.forEach { m ->
            append(date(m.dateMs, cutoffHour, zone)).append(',')
            append(num(m.weightKg)).append(NEWLINE)
        }
    }

    /**
     * `date,waistCm,neckCm[,hipCm],bodyFatPct` — the girth series plus derived body fat, oldest
     * first. See [includesHip] for when the hip column appears.
     */
    fun measurementsCsv(
        metrics: List<BodyMetric>,
        sex: String?,
        heightCm: Float?,
        cutoffHour: Int = DayBoundary.cutoffHour,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val withHip = includesHip(metrics, sex)
        return buildString {
            append("date,waistCm,neckCm")
            if (withHip) append(",hipCm")
            append(",bodyFatPct").append(NEWLINE)

            metrics.sortedBy { it.dateMs }.forEach { m ->
                append(date(m.dateMs, cutoffHour, zone)).append(',')
                append(num(m.waistCm)).append(',')
                append(num(m.neckCm)).append(',')
                if (withHip) append(num(m.hipCm)).append(',')
                // Body fat uses the RAW stored hip, not the display-filtered one: the export is a
                // record of what was logged, and the estimator itself decides whether hip matters.
                append(percent(BodyComposition.estimate(sex, heightCm, m.waistCm, m.neckCm, m.hipCm)))
                append(NEWLINE)
            }
        }
    }

    /**
     * Whether the `hipCm` column is emitted.
     *
     * Hip is a women-only field in the UI (decision 2 of the body-progress batch), so a male profile
     * would otherwise carry a permanently blank column. But the column must still appear whenever
     * hip data actually EXISTS — a user who logged hips and later changed their profile sex would
     * otherwise silently lose those measurements on export, and an export that drops logged data is
     * worse than one with a redundant column.
     */
    fun includesHip(metrics: List<BodyMetric>, sex: String?): Boolean =
        BodyComposition.needsHip(sex) || metrics.any { it.hipCm != null }

    // ── Field rendering ───────────────────────────────────────────────────────────────────────

    private fun date(ms: Long, cutoffHour: Int, zone: ZoneId): String =
        DayBoundary.logicalDate(ms, cutoffHour, zone).toString()

    /**
     * A stored measurement, at full precision. Kotlin's [Float.toString] is locale-independent and
     * emits the shortest form that round-trips, so `90f` -> "90.0" and `79.25f` -> "79.25" — no
     * rounding is applied to anything the user actually typed. Null -> empty field.
     */
    private fun num(v: Float?): String = v?.toString() ?: ""

    /** A derived percentage: one decimal, forced [Locale.US]. Null -> empty field. */
    private fun percent(v: Float?): String =
        if (v == null) "" else String.format(Locale.US, "%.1f", v)
}
