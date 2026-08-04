package com.migul.treningsprogram.domain

import java.time.LocalDate

/**
 * Body-progress batch 2026-08-04 (brief 02) — the Body tab's time-scale control.
 *
 * User decision 7: the charts default to **3 months** and can be switched to 1M / 6M / 1Y / All,
 * plus a calendar-picked custom start–end range (A6). The presets resolve to the very same
 * [DateRangeFilter.Range] (inclusive epoch-day bounds) that the custom picker produces, so every
 * chart in the tab filters through one code path regardless of how the window was chosen.
 *
 * Pure object — the preset→range arithmetic is unit-tested rather than trusted to the fragment.
 */
object BodyProgressRange {

    /** null [months] = "All time" (no bound). */
    enum class Preset(val label: String, val months: Long?) {
        M1("1M", 1),
        M3("3M", 3),
        M6("6M", 6),
        Y1("1Y", 12),
        ALL("All", null)
    }

    /** Decision 7: the tab opens on a 3-month window. */
    val DEFAULT: Preset = Preset.M3

    /** The presets in the order the chips are laid out. */
    val PRESETS: List<Preset> = listOf(Preset.M1, Preset.M3, Preset.M6, Preset.Y1, Preset.ALL)

    /**
     * The inclusive window [preset] covers relative to [todayEpochDay], or null for
     * [Preset.ALL] (which every consumer already reads as "no filtering").
     *
     * The window ENDS today: a 3-month view is "the last three months up to and including today",
     * not a calendar quarter. Month arithmetic goes through [LocalDate.minusMonths] so it clamps
     * correctly at month ends (31 Mar − 1 month = 28/29 Feb) instead of drifting on a fixed
     * day count.
     */
    fun rangeFor(preset: Preset, todayEpochDay: Long): DateRangeFilter.Range? {
        val months = preset.months ?: return null
        val end = LocalDate.ofEpochDay(todayEpochDay)
        val start = end.minusMonths(months)
        return DateRangeFilter.Range(start.toEpochDay(), end.toEpochDay())
    }

    /**
     * The label for the control's current state: the preset's own short label, or the custom
     * range's date span (reusing [DateRangeFilter.label] so it reads identically to the History
     * and Progress tabs' range buttons).
     */
    fun labelFor(preset: Preset?, custom: DateRangeFilter.Range?): String = when {
        custom != null -> DateRangeFilter.label(custom)
        preset != null -> preset.label
        else -> Preset.ALL.label
    }
}
