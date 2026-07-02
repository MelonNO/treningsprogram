package com.migul.treningsprogram.ui.common

/**
 * F6 — the in-app "What's new" source. One entry per released version, newest
 * first. Shown after an update for every entry newer than the last version the
 * user has seen (capped, so a long-skipped user isn't buried).
 *
 * Keep entries short and user-facing — this is not the git log.
 */
object Changelog {

    data class Entry(val versionCode: Int, val versionName: String, val highlights: List<String>)

    val ENTRIES = listOf(
        Entry(
            versionCode = 63, versionName = "1.24.0",
            highlights = listOf(
                "Recap, redesigned: a hero session header with stat chips, a new \"Earned this session\" card (achievements + PRs), and specific muscles — triceps, not just arms",
                "Bodyweight exercises finally chart: the Progress tab plots your reps over time (plus the weight chart once you add load)",
                "Pick any date range: History and Progress now filter with a start/end calendar instead of preset chips",
                "Tap a square on the Stats heatmap to jump straight to that session's recap",
                "Profile now spotlights the PRs you set in the last 7 days",
                "Exercise images animate in the library, the weight keypad closes when you tap elsewhere, skeleton loaders while data loads, and App Settings moved to the top",
            ),
        ),
        Entry(
            versionCode = 62, versionName = "1.23.0",
            highlights = listOf(
                "Streaks are schedule-aware: rest days never break your chain — only missed planned days do",
                "Notification center: workout reminders, streak alerts, weekly summary and challenge nudges — each individually toggleable",
                "The AI now knows your body weight, and Home + Progress show your weight trend",
                "Weekly challenges 2.0 with a Perfect Week bonus for completing every planned day",
                "Achievement gallery: categories, rarity tiers, live progress bars and a \"next up\" strip",
                "Finishing a workout is now a celebration — and a \"beat last time\" chip with a live PR flash cheers you on while logging",
            ),
        ),
        Entry(
            versionCode = 61, versionName = "1.22.0",
            highlights = listOf(
                "Your own rest times: enable \"Use my own rest times\" in Settings → Training or the setup wizard — one time for heavy compounds, one for accessories — and both the rest timer and generated programs are sized around them",
                "The rest sheet now says whose time it is: \"AI suggested\" vs \"Your time\"",
                "+30/−30 on the rest timer now sticks for the remaining sets of that exercise (this session only)",
                "The exercise timer no longer resets when you minimize the app — it survives even an app restart mid-workout",
                "Cleaner Home header (decorative globe removed)",
            ),
        ),
        Entry(
            versionCode = 60, versionName = "1.21.0",
            highlights = listOf(
                "Swipe right on the Program week card to look back at past (logged) weeks",
                "Richer Stats: this-week pulse with vs-last-week deltas, segmented rep-range bar, gradient muscle bars, glowing charts, pill tabs",
                "Plate calculator knows your gym: bar weight, plate set and loadable dumbbells per gym preset (50 mm home bar by default)",
                "All weekly graphs now agree on Monday-based weeks",
            ),
        ),
        Entry(
            versionCode = 59, versionName = "1.20.0",
            highlights = listOf(
                "Deeper Auros look: signature particle art, aurora ghost buttons, on-brand dialogs and menus",
                "Weekly muscle-volume heatmap in History → Stats",
                "Plate-loading readout in the weight keypad for barbell lifts",
                "Optional workout-day reminders (Profile → Settings → App Settings)",
                "Home-screen widget with today's workout",
                "Haptic confirmation when logging sets",
                "Backups now keep your rest days, day boundary and auto-rebalance choice",
                "Failed automatic weekly generation now retries on the next launch",
            ),
        ),
        Entry(
            versionCode = 58, versionName = "1.19.2",
            highlights = listOf(
                "Luminous gradient buttons for primary actions",
                "Polished rest-timer, onboarding and welcome dialogs",
            ),
        ),
        Entry(
            versionCode = 57, versionName = "1.19.1",
            highlights = listOf(
                "Aurora hero glow and brighter day badges",
                "Refined search and call-to-action icons",
            ),
        ),
        Entry(
            versionCode = 56, versionName = "1.19.0",
            highlights = listOf(
                "Complete visual redesign: the dark-teal Auros theme across every screen",
                "New Inter typeface and eyebrow section headers",
            ),
        ),
    )

    /** Entries newer than [sinceVersionCode], newest first, capped at [max]. */
    fun entriesSince(sinceVersionCode: Int, max: Int = 3): List<Entry> =
        ENTRIES.filter { it.versionCode > sinceVersionCode }.take(max)
}
