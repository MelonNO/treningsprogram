package com.migul.treningsprogram.ui.common

/**
 * P5: rotating, informative + friendly wait copy shown WHILE a plan generates, so the wait is less
 * boring. This is purely presentational — it never replaces the real per-attempt status (which every
 * wait screen keeps on its own line); it is the changing companion text. Any content/tone is
 * acceptable ([P5-A1]); these mix training tips, app facts, and light encouragement.
 *
 * Pure object (no Android) so the rotation indexing is unit-testable. Screens cycle [tip] on a timer.
 */
object GenerationTips {

    val messages: List<String> = listOf(
        "Reading your recent sessions to set real starting weights…",
        "Tip: progressive overload — add a rep or a little load when a set starts to feel easy.",
        "Balancing your week so the same muscle isn't hit two days in a row.",
        "Did you know? Your plan adapts to what you actually logged, not a fixed template.",
        "Tip: leaving 1–2 reps in the tank on most sets lets you train hard and still recover.",
        "Checking each day fits your session-length target…",
        "Tip: compound lifts first, isolation last — that order is built into every day.",
        "Picking fresh exercises so you're not repeating last week's movements.",
        "Tip: sleep and protein do as much for progress as the program does.",
        "Running a quality review pass over the plan before it's saved…",
        "Tip: consistency beats intensity — showing up most weeks is what moves the needle.",
        "Almost there — putting the finishing touches on your week."
    )

    /** The wait message at rotation step [index] (wraps around the list). */
    fun tip(index: Int): String =
        if (messages.isEmpty()) "" else messages[Math.floorMod(index, messages.size)]
}
