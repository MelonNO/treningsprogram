package com.migul.treningsprogram.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * Item 06: computes WHEN the unattended weekly generation should run.
 *
 * The plan is keyed on `thisMonday()`, which resolves the week through [DayBoundary] (a logical day
 * runs cutoff→cutoff, default 04:00). Running before the boundary on Monday would generate for the
 * OLD week and no-op — so the scheduled run targets the first instant that is safely inside the new
 * logical week: calendar Monday at cutoffHour:[RUN_MINUTE_MARGIN] local time. With the max cutoff
 * (06:00) that is 06:20 — still "ready by Monday morning" (assumption A5 allows late Sunday/early
 * Monday; the exact slot is the builder's choice).
 *
 * Pure functions over explicit now/cutoff/zone so the computation is unit-testable off-device.
 */
object AutoGenSchedule {

    /** Minutes past the day-boundary hour, small safety margin against clock/zone skew. */
    const val RUN_MINUTE_MARGIN = 20

    /** Re-try delay after a transient unattended failure (the failure cap bounds total attempts). */
    const val RETRY_DELAY_MS: Long = 2L * 60L * 60L * 1000L

    /**
     * The next instant (epoch ms, strictly after [nowMs]) at calendar-Monday
     * cutoffHour:[RUN_MINUTE_MARGIN] local — i.e. the first safe moment of the next logical week.
     * If "now" is Monday before that time, it is later TODAY (this also self-corrects a run that
     * fired while the previous logical week was still active: the next slot lands hours, not a
     * week, away).
     */
    fun nextRunAtMs(
        nowMs: Long = System.currentTimeMillis(),
        cutoffHour: Int = DayBoundary.cutoffHour,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        var date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        repeat(9) {
            if (date.dayOfWeek == DayOfWeek.MONDAY) {
                val at = date.atTime(cutoffHour, RUN_MINUTE_MARGIN).atZone(zone).toInstant().toEpochMilli()
                if (at > nowMs) return at
            }
            date = date.plusDays(1)
        }
        // Unreachable: a Monday always occurs within the scanned window.
        return nowMs + 7L * 24L * 60L * 60L * 1000L
    }

    /** Delay from [nowMs] until [nextRunAtMs] — what the work scheduler actually needs. */
    fun nextRunDelayMs(
        nowMs: Long = System.currentTimeMillis(),
        cutoffHour: Int = DayBoundary.cutoffHour,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = nextRunAtMs(nowMs, cutoffHour, zone) - nowMs
}
