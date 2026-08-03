package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import kotlin.math.roundToInt

/**
 * QoL item 03: rough estimated-calories-burned figure for a logged session, derived ON THE FLY from
 * stored data only — nothing is persisted (no schema, prefs, or backup-format change). Every surface
 * that shows calories (workout-complete summary, session Recap, Stats weekly total) calls THIS
 * object, so the three surfaces always agree.
 *
 * ## Model
 * Standard MET energy math: `kcal = MET × bodyWeightKg × hours` (the Compendium of Physical
 * Activities convention that 1 MET ≈ 1 kcal per kg of body weight per hour). The session's MET is a
 * blend of a strength constant and a cardio constant, weighted by the share of the session's logged
 * sets whose muscle group is "Cardio":
 *
 * `MET = STRENGTH_MET + (CARDIO_MET − STRENGTH_MET) × cardioSetShare`
 *
 * Constants (deliberately conservative mid-range Compendium values, stable across releases):
 *  - [STRENGTH_MET] = 4.0 — Compendium code 02052 ("resistance training, multiple exercises,
 *    8–15 reps") is 3.5 METs and vigorous free-weight work is 5.0–6.0; 4.0 is a defensible
 *    session-average for typical training INCLUDING the rest periods the stored duration spans.
 *  - [CARDIO_MET] = 7.0 — the logged modality is unknown (bike ~6.8, rowing ~7.0, elliptical ~5.0,
 *    running 6 mph ~9.8), so 7.0 sits mid-range for moderate-vigorous mixed cardio.
 *
 * ## Rules and edge handling
 *  - REST/MISSED auto-logged placeholder rows ([WorkoutSession.isPlaceholder]) get NO figure
 *    (null) and therefore contribute 0 to weekly totals.
 *  - Warm-up sets COUNT toward the cardio/strength mix: energy is spent on them too, and the
 *    duration term already spans them. This is applied identically on all surfaces.
 *  - Body weight per session = most recent weigh-in AT OR BEFORE the session's timestamp; if
 *    weigh-ins exist but all postdate the session, the earliest weigh-in (nearest known value);
 *    [DEFAULT_BODY_WEIGHT_KG] = 75 kg ONLY when no weigh-in exists at all (brief A2).
 *  - Clamps: body weight is coerced into [MIN_BODY_WEIGHT_KG]..[MAX_BODY_WEIGHT_KG] (typo guard);
 *    duration is capped at [MAX_DURATION_MINUTES] (clock-skew guard); a stored duration of 0
 *    (legacy/sub-minute sessions) falls back to [FALLBACK_MINUTES_PER_SET] × set count — MEASURED:
 *    real sessions in the 2026-08-03 estimator calibration ran a median 4.4 minutes per logged set
 *    (n=18 sessions), and the corrected [WorkoutTimeEstimator] per-set math agrees (~3.5–4 min).
 *  - Output is rounded to the nearest 10 kcal with a 10 kcal floor, and [format] prefixes "~",
 *    so the figure always READS as an estimate, never as an exact claim.
 */
object CalorieEstimator {

    /** Session-average METs for resistance training including between-set rest. */
    const val STRENGTH_MET = 4.0f

    /** Mid-range METs for unknown-modality moderate-vigorous cardio. */
    const val CARDIO_MET = 7.0f

    /** Brief-mandated fallback used ONLY when the user has never logged a body weight. */
    const val DEFAULT_BODY_WEIGHT_KG = 75f

    /**
     * Fallback session length when the stored duration is 0 (legacy/sub-minute sessions).
     * 2026-08-03: raised 3 → 4 with the estimator correction — real sessions measure a median
     * ~4.4 min per logged set; the old 3 was "in line with" the old OPTIMISTIC formula.
     */
    const val FALLBACK_MINUTES_PER_SET = 4

    /** Sanity ceiling on the duration term (6 h) — guards absurd stored values (clock skew). */
    const val MAX_DURATION_MINUTES = 360

    /** Plausible-adult clamp band for the body-weight term — guards typo weigh-ins. */
    const val MIN_BODY_WEIGHT_KG = 30f
    const val MAX_BODY_WEIGHT_KG = 250f

    /** One completed session plus everything the weekly total needs to place and estimate it. */
    data class SessionInput(
        val session: WorkoutSession,
        val sets: List<WorkoutSet>,
        /** The session's LOGICAL epoch-day (caller maps via [DayBoundary.logicalEpochDay]). */
        val logicalEpochDay: Long,
    )

    /** Where the body-weight term came from — surfaced by the item-03 explanation. */
    enum class BodyWeightSource {
        /** Most recent weigh-in at or before the session. */ WEIGH_IN,
        /** All weigh-ins postdate the session; earliest one used. */ EARLIEST_WEIGH_IN,
        /** No weigh-ins at all; [DEFAULT_BODY_WEIGHT_KG] used. */ DEFAULT,
    }

    /**
     * QoL 2026-08 item 03: every REAL input and intermediate of one session's estimate, so the
     * tappable explanation can show the actual numbers. [kcal] IS the chip figure —
     * [estimateSession] delegates here, so the walkthrough and the chip can never disagree.
     */
    data class Breakdown(
        val minutes: Int,                    // duration term actually used (after cap/fallback)
        val usedPerSetFallback: Boolean,     // true = stored duration was 0; sets × 3 min used
        val totalSets: Int,
        val cardioSets: Int,
        val met: Float,                      // blended intensity
        val bodyWeightKg: Float,             // clamped weight actually used
        val bodyWeightSource: BodyWeightSource,
        val rawKcal: Float,                  // met × weight × hours, before rounding
        val kcal: Int,                       // the rounded chip figure
    )

    /**
     * The body weight to use for a session dated [sessionDateMs]: the most recent weigh-in at or
     * before that instant; else the earliest weigh-in on record (all postdate the session); else
     * [DEFAULT_BODY_WEIGHT_KG]. The result is clamped to the plausible band.
     */
    fun bodyWeightFor(sessionDateMs: Long, weighIns: List<BodyMeasurement>): Float =
        bodyWeightWithSource(sessionDateMs, weighIns).first

    /** [bodyWeightFor] plus WHERE the value came from (for the item-03 explanation). */
    fun bodyWeightWithSource(
        sessionDateMs: Long,
        weighIns: List<BodyMeasurement>,
    ): Pair<Float, BodyWeightSource> {
        val atOrBefore = weighIns.filter { it.dateMs <= sessionDateMs }.maxByOrNull { it.dateMs }
        if (atOrBefore != null) {
            return atOrBefore.weightKg.coerceIn(MIN_BODY_WEIGHT_KG, MAX_BODY_WEIGHT_KG) to
                BodyWeightSource.WEIGH_IN
        }
        val earliest = weighIns.minByOrNull { it.dateMs }
            ?: return DEFAULT_BODY_WEIGHT_KG to BodyWeightSource.DEFAULT
        return earliest.weightKg.coerceIn(MIN_BODY_WEIGHT_KG, MAX_BODY_WEIGHT_KG) to
            BodyWeightSource.EARLIEST_WEIGH_IN
    }

    /**
     * Estimated kcal for one session, rounded to the nearest 10 (min 10), or null when no figure
     * must be shown: REST/MISSED placeholder rows and sessions with no logged sets.
     *
     * @param sets ALL of the session's sets, warm-ups included (they weight the MET blend).
     */
    fun estimateSession(session: WorkoutSession, sets: List<WorkoutSet>, bodyWeightKg: Float): Int? =
        breakdown(session, sets, bodyWeightKg)?.kcal

    /**
     * The full item-03 breakdown for one session, or null exactly when [estimateSession] shows no
     * figure (placeholder rows, no sets). Identical math — this IS the estimate's code path.
     */
    fun breakdown(
        session: WorkoutSession,
        sets: List<WorkoutSet>,
        bodyWeightKg: Float,
        bodyWeightSource: BodyWeightSource = BodyWeightSource.WEIGH_IN,
    ): Breakdown? {
        if (session.isPlaceholder) return null
        if (sets.isEmpty()) return null
        val usedFallback = session.durationMinutes <= 0
        val minutes = (if (!usedFallback) session.durationMinutes
                       else sets.size * FALLBACK_MINUTES_PER_SET)
            .coerceAtMost(MAX_DURATION_MINUTES)
        val cardioSets = sets.count { it.muscleGroup.equals("Cardio", ignoreCase = true) }
        val cardioShare = cardioSets / sets.size.toFloat()
        val met = STRENGTH_MET + (CARDIO_MET - STRENGTH_MET) * cardioShare
        val weight = bodyWeightKg.coerceIn(MIN_BODY_WEIGHT_KG, MAX_BODY_WEIGHT_KG)
        val kcal = met * weight * (minutes / 60f)
        return Breakdown(
            minutes = minutes,
            usedPerSetFallback = usedFallback,
            totalSets = sets.size,
            cardioSets = cardioSets,
            met = met,
            bodyWeightKg = weight,
            bodyWeightSource = bodyWeightSource,
            rawKcal = kcal,
            kcal = ((kcal / 10f).roundToInt() * 10).coerceAtLeast(10),
        )
    }

    /**
     * Weekly total for the Stats pulse: the sum of the per-session estimates (exactly the figures
     * the Recap shows) of every session whose logical day falls in the CURRENT Monday-based week
     * (same week math as [WeekDelta], via [RecapGraphs.mondayOfEpochDay]). Placeholder rows
     * estimate to null and thus contribute 0.
     */
    fun weeklyTotal(inputs: List<SessionInput>, weighIns: List<BodyMeasurement>, todayEpochDay: Long): Int {
        val monday = RecapGraphs.mondayOfEpochDay(todayEpochDay)
        return inputs
            .filter { it.logicalEpochDay in monday until monday + 7 }
            .sumOf { input ->
                estimateSession(
                    input.session, input.sets, bodyWeightFor(input.session.dateMs, weighIns)
                ) ?: 0
            }
    }

    /** The one approximate presentation every surface uses — e.g. "~ 320 kcal". */
    fun format(kcal: Int): String = "~ $kcal kcal"
}
