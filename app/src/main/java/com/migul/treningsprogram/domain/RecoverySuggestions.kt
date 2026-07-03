package com.migul.treningsprogram.domain

/**
 * B5 — the rest-day active-recovery suggestion: a small BUILT-IN static catalog (A-A1 — no AI,
 * no external content), text-only (A-A2), biased AWAY from whatever the recovery model
 * currently marks as recovering (sore legs ⇒ never lunges; upper-body mobility or a walk
 * instead). Rotated by logical day so it doesn't repeat daily.
 *
 * Tone contract: an invitation, never an obligation — nothing here touches streaks, XP,
 * challenges, stats or logged history; ignoring it is a perfectly completed rest day.
 *
 * Pure + Android-free: selection is unit-tested against recovery inputs.
 */
object RecoverySuggestions {

    /** One text-only suggestion. [stresses] uses the recovery model's FINE muscle labels. */
    data class Suggestion(val title: String, val line: String, val stresses: Set<String>)

    private val LEGS = setOf("Quads", "Hamstrings", "Glutes", "Calves")
    private val BACK = setOf("Lower Back", "Upper Back")
    private val SHOULDERS = setOf("Front Delts", "Side Delts", "Rear Delts")

    /** Gentle walk variants — stress nothing meaningfully; the universal fallback family. */
    private val WALKS = listOf(
        Suggestion("Easy walk", "20–30 minutes at a chat-friendly pace — blood flow, zero cost.", emptySet()),
        Suggestion("Sunlight stroll", "A 15-minute daylight walk — recovery likes daylight as much as legs do.", emptySet()),
        Suggestion("Podcast walk", "Pick an episode, walk until it ends. Pace irrelevant.", emptySet()),
        Suggestion("Evening wind-down walk", "10–20 slow minutes after dinner — digestion and sleep both say thanks.", emptySet()),
    )

    /** Equipment-free mobility moves, tagged with what they actually load. */
    private val MOBILITY = listOf(
        Suggestion("Hip-flexor openers", "2 sets of a half-kneeling hip-flexor stretch per side, 45 s each.", setOf("Quads", "Glutes")),
        Suggestion("World's-greatest stretch", "3 slow reps per side of the lunge-with-rotation flow.", LEGS + setOf("Core")),
        Suggestion("Deep-squat sits", "Accumulate 2–3 minutes resting in the bottom of a squat.", setOf("Quads", "Glutes", "Calves")),
        Suggestion("Calf + ankle circles", "30 slow ankle circles each way per side, then a gentle wall calf stretch.", setOf("Calves")),
        Suggestion("Thoracic openers", "10 slow thread-the-needles per side — free the mid-back.", BACK),
        Suggestion("Cat-cow flow", "2 easy minutes of slow cat-cow — the spine's favorite rest-day move.", setOf("Lower Back", "Core")),
        Suggestion("Doorway chest stretch", "3 × 30 s per side in a doorway — undo the pressing.", setOf("Chest") + SHOULDERS),
        Suggestion("Shoulder pass-throughs", "10 slow arm circles + 10 towel pass-throughs.", SHOULDERS),
        Suggestion("Neck + trap release", "5 slow half-circles each way, then 30 s of gentle ear-to-shoulder holds.", setOf("Upper Back")),
        Suggestion("Wrist + forearm care", "A minute of gentle wrist circles and flexor/extensor stretches.", setOf("Biceps", "Triceps")),
        Suggestion("Hamstring floss", "8 slow toe-reach hinges per side — move into the stretch, don't yank.", setOf("Hamstrings", "Lower Back")),
        Suggestion("Couch-stretch quads", "45 s per side of the rear-foot-elevated quad stretch. Breathe.", setOf("Quads")),
    )

    val CATALOG: List<Suggestion> = WALKS + MOBILITY

    /**
     * Picks today's suggestion:
     *  - candidates = catalog entries that do NOT stress a currently-recovering muscle;
     *  - a brand-new user (nothing recovering) gets a sensible generic pick — a walk;
     *  - everything sore ⇒ the walk family is always safe;
     *  - rotation by [epochDay] so consecutive rest days vary.
     */
    fun pick(recoveringMuscles: Set<String>, epochDay: Long): Suggestion {
        val pool = when {
            recoveringMuscles.isEmpty() -> WALKS
            else -> CATALOG.filter { it.stresses.intersect(recoveringMuscles).isEmpty() }
                .ifEmpty { WALKS }
        }
        val idx = ((epochDay % pool.size) + pool.size) % pool.size
        return pool[idx.toInt()]
    }

    /**
     * The card's visibility gate: rest day only (no plan), nothing in progress, nothing already
     * logged today, feature not switched off (A-A3 permanent switch), not dismissed TODAY
     * (per-day dismiss returns on the next rest day by construction).
     */
    fun shouldShow(
        planEmpty: Boolean,
        hasActiveSession: Boolean,
        completedToday: Boolean,
        enabled: Boolean,
        dismissedEpochDay: Long,
        todayEpochDay: Long
    ): Boolean =
        planEmpty && !hasActiveSession && !completedToday && enabled &&
            dismissedEpochDay != todayEpochDay
}
