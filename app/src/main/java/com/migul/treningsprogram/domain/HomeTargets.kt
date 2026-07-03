package com.migul.treningsprogram.domain

/**
 * N1 — the pre-workout "number to beat" shown on Home's today card.
 *
 * CONTRACT: the number Home shows for an exercise is EXACTLY the in-workout Beat chip's initial
 * target for the same exercise — [BeatTarget.chipTarget] with no session best yet. One rule,
 * two surfaces: an exercise with history shows its historical best working weight; a fresh
 * exercise shows nothing (first sessions set baselines, never targets — ratified rule).
 * Warm-ups never influence the value (the underlying query is working-sets-only).
 */
object HomeTargets {

    /** Home's target for one exercise, or null = show nothing. */
    fun targetFor(previousMax: Float?): Float? = BeatTarget.chipTarget(previousMax, null)
}
