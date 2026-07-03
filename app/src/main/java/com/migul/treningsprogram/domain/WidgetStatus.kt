package com.migul.treningsprogram.domain

/**
 * B10 — what the widget's streak/challenge accent line shows for a given state. Pure so the
 * data-selection is unit-testable; the provider only renders the returned string.
 *
 * Sources of truth: the streak is the stored post-R1 plan-adherence streak
 * (UserStats.currentStreak — never recomputed here) and the challenges are the week's drawn
 * set. A user with no streak AND no active challenges gets NULL — the line disappears
 * entirely (clean layout, no zeros and empty bars).
 */
object WidgetStatus {

    fun line(streak: Int, challengesDone: Int, challengesTotal: Int): String? {
        val parts = buildList {
            if (streak > 0) {
                val flame = if (streak >= 7) "🔥🔥" else "🔥"
                add("$flame $streak-day streak")
            }
            if (challengesTotal > 0) {
                add("🎯 Challenges $challengesDone/$challengesTotal")
            }
        }
        return if (parts.isEmpty()) null else parts.joinToString("  ·  ")
    }
}
