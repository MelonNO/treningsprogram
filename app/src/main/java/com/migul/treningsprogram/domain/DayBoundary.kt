package com.migul.treningsprogram.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single, app-wide source of truth for "which logical day does this instant belong to."
 *
 * By default a calendar day runs midnight→midnight, but a user who trains at 01:00 still thinks of
 * it as the *previous* day. This object shifts the day boundary by a configurable whole-hour
 * [cutoffHour] (default [DEFAULT_CUTOFF_HOUR] = 04:00): anything from 00:00 up to — but not
 * including — the cutoff is attributed to the previous calendar day. A "logical day" therefore runs
 * from the cutoff of one calendar day to just before the cutoff of the next (04:00 → 03:59).
 *
 * The math is a pure derivation over each timestamp (`ms − cutoffHour hours`, then take the local
 * date) — no stored data is rewritten, so historical sessions are simply re-interpreted. It is
 * DST-safe because it works in the instant timeline before resolving the local date.
 *
 * The pure functions take an explicit `cutoffHour`/`zone` so they are fully unit-testable off-device.
 * The convenience overloads (and every non-test caller) read the process-wide [cutoffHour] holder,
 * which [com.migul.treningsprogram.data.preferences.PreferencesManager] keeps in sync with the
 * user's Settings choice. This lets the ~80 date derivations across the app consult one shared
 * notion of "today" instead of each recomputing midnight.
 */
object DayBoundary {

    const val DEFAULT_CUTOFF_HOUR = 4
    const val MIN_CUTOFF_HOUR = 0
    const val MAX_CUTOFF_HOUR = 6

    private const val HOUR_MS = 60L * 60L * 1000L

    /**
     * Process-wide current cutoff hour, seeded from persisted prefs on app start and updated whenever
     * the user changes it in Settings. Defaults to [DEFAULT_CUTOFF_HOUR] so any derivation that runs
     * before prefs is constructed still behaves as the default-cutoff user would expect. Coerced into
     * the supported [MIN_CUTOFF_HOUR]..[MAX_CUTOFF_HOUR] range.
     */
    @Volatile
    var cutoffHour: Int = DEFAULT_CUTOFF_HOUR
        set(value) { field = value.coerceIn(MIN_CUTOFF_HOUR, MAX_CUTOFF_HOUR) }

    // ── Pure core (explicit cutoff/zone — used by tests and by callers that already have them) ──────

    /**
     * The instant [ms] shifted back by [cutoffHour] hours. Formatting the DATE of the result (day
     * granularity) yields the logical day; equivalent to [logicalDate] but convenient where an
     * existing SimpleDateFormat/Date pipeline only needs the millis shifted before formatting.
     */
    fun toLogicalMillis(ms: Long, cutoffHour: Int = this.cutoffHour): Long = ms - cutoffHour * HOUR_MS

    /** The logical calendar date that [ms] belongs to under the given cutoff. */
    fun logicalDate(ms: Long, cutoffHour: Int = this.cutoffHour, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).minusHours(cutoffHour.toLong()).toLocalDate()

    /** The logical local epoch-day (whole days since the Unix epoch) that [ms] belongs to. */
    fun logicalEpochDay(ms: Long, cutoffHour: Int = this.cutoffHour, zone: ZoneId = ZoneId.systemDefault()): Long =
        logicalDate(ms, cutoffHour, zone).toEpochDay()

    /** The current logical date ("today") for [nowMs] under the given cutoff. */
    fun today(cutoffHour: Int = this.cutoffHour, zone: ZoneId = ZoneId.systemDefault(), nowMs: Long = System.currentTimeMillis()): LocalDate =
        logicalDate(nowMs, cutoffHour, zone)

    /** The current logical local epoch-day ("today") for [nowMs] under the given cutoff. */
    fun todayEpochDay(cutoffHour: Int = this.cutoffHour, zone: ZoneId = ZoneId.systemDefault(), nowMs: Long = System.currentTimeMillis()): Long =
        today(cutoffHour, zone, nowMs).toEpochDay()
}
