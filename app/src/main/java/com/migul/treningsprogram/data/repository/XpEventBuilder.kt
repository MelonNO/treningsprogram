package com.migul.treningsprogram.data.repository

import com.migul.treningsprogram.data.db.entity.XpEvent

/**
 * U2: pure helper that turns the components of a single workout's XP award into itemized
 * [XpEvent] rows for the XP log.
 *
 * HARD CONSTRAINT: this is pure OBSERVATION — it never invents or alters XP amounts. The caller
 * passes in the already-computed component amounts (baseXp, setXp, prXp, bonusChallengeXp) exactly
 * as awarded by GamificationRepository; this helper only describes them. The sum of the emitted
 * rows' amounts always equals the total xpEarned (verified by [U2XpLogTest]).
 *
 * Itemization: one row per non-zero component, so the user sees what earned each chunk. baseXp is
 * always emitted (workout completion always grants it). Zero-value optional components are skipped
 * to avoid noise.
 */
object XpEventBuilder {

    fun buildWorkoutEvents(
        timestampMs: Long,
        sessionId: Long?,
        baseXp: Int,
        setXp: Int,
        setCount: Int,
        prXp: Int,
        prCount: Int,
        bonusChallengeXp: Int,
        challengeNames: List<String> = emptyList()
    ): List<XpEvent> {
        val events = mutableListOf<XpEvent>()

        // Base completion XP — always present for a completed workout.
        events += XpEvent(
            timestampMs = timestampMs,
            amount = baseXp,
            reason = "Workout completed",
            sessionId = sessionId
        )

        if (setXp > 0) {
            events += XpEvent(
                timestampMs = timestampMs,
                amount = setXp,
                reason = "$setCount ${plural(setCount, "set", "sets")} logged",
                sessionId = sessionId
            )
        }

        if (prXp > 0) {
            events += XpEvent(
                timestampMs = timestampMs,
                amount = prXp,
                reason = "Personal record ×$prCount",
                sessionId = sessionId
            )
        }

        if (bonusChallengeXp > 0) {
            val reason = if (challengeNames.isNotEmpty()) {
                "Daily challenge: " + challengeNames.joinToString(", ")
            } else {
                "Daily challenge bonus"
            }
            events += XpEvent(
                timestampMs = timestampMs,
                amount = bonusChallengeXp,
                reason = reason,
                sessionId = sessionId
            )
        }

        return events
    }

    private fun plural(n: Int, one: String, many: String): String = if (n == 1) one else many
}
