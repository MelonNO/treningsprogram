package com.migul.treningsprogram.domain

import kotlin.random.Random

/**
 * Body-progress batch 2026-08-04 (brief 03) — which exercise the Progress tab opens on.
 *
 * The tab used to open with nothing selected and empty charts. It now opens on a RANDOM exercise
 * drawn from the user's 15 most-logged ones (user decision 11), so there is always something to
 * look at.
 *
 * "Most logged" is assumption A2: the number of DISTINCT workout sessions an exercise appears in,
 * warm-up or working set alike — which is exactly the ordering
 * [ExercisePickerSort.order] already produces for the picker (it wraps
 * `WorkoutSetDao.getExerciseSessionCounts`, a `COUNT(DISTINCT sessionId)` over all sets). So the
 * pool is simply the head of that already-ordered list; this helper never re-ranks anything.
 *
 * The pick's LIFETIME (once per app launch, manual choices sticking for the rest of the session)
 * lives in [com.migul.treningsprogram.ui.history.ProgressSelectionStore] — a process-scoped
 * singleton, deliberately not persisted to disk. This object is only the choosing rule.
 */
object ProgressDefaultExercise {

    /** Decision 11: the pool is the 15 most-logged exercises. */
    const val POOL_SIZE = 15

    /**
     * The candidate pool: the first [POOL_SIZE] of [orderedNames] (which MUST already be in
     * most-sessions-first order). Fewer than 15 distinct exercises logged ⇒ the pool is simply
     * whatever exists; none ⇒ empty, and the caller keeps today's empty state.
     */
    fun pool(orderedNames: List<String>): List<String> = orderedNames.take(POOL_SIZE)

    /**
     * A uniformly random exercise from [pool], or null when nothing has been logged.
     * [random] is injectable so the choice is unit-testable (and so successive app launches can
     * genuinely differ — see ProgressDefaultExerciseTest).
     */
    fun pick(orderedNames: List<String>, random: Random = Random.Default): String? {
        val candidates = pool(orderedNames)
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }

    /**
     * What the Progress tab should show, given the exercise [remembered] for this app launch (null
     * on the first open of the process) and the picker's ordered [orderedNames].
     *
     * - a remembered name that still exists WINS — that is what makes both the once-per-launch
     *   default and a manual switch survive leaving and re-entering the tab (decision 11);
     * - anything else (nothing remembered yet, or a remembered name that no longer exists — e.g. a
     *   half-typed entry, or an exercise whose last session was deleted) rolls a fresh random pick;
     * - no logged exercises at all returns null, and the caller keeps the existing empty state.
     */
    fun resolve(
        orderedNames: List<String>,
        remembered: String?,
        random: Random = Random.Default
    ): String? =
        if (remembered != null && orderedNames.contains(remembered)) remembered
        else pick(orderedNames, random)
}
