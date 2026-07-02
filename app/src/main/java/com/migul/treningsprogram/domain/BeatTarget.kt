package com.migul.treningsprogram.domain

/**
 * R7 — "beat last time": the pure decision logic behind the in-workout target chip and the live
 * PR flash. PREVIEW ONLY: the official PR award stays in the completion flow
 * (GamificationRepository.detectPersonalRecords) — this mirrors its rule so what flashes
 * mid-workout is exactly what completion later credits (BeatTargetTest locks the agreement).
 */
object BeatTarget {

    /**
     * Same rule as GamificationRepository.isWeightPr: a PR needs a REAL prior best, strictly
     * beaten. A first-ever performance (null previous) establishes the baseline — never a PR.
     */
    fun isPr(weightKg: Float, previousMax: Float?): Boolean =
        previousMax != null && weightKg > previousMax

    /**
     * Should logging this set fire the inline PR moment?
     *  - warm-ups never flash (they never count toward PRs);
     *  - the set must beat the HISTORICAL best ([previousMax]);
     *  - and beat anything already flashed this session ([sessionBestFlashed]) — the moment
     *    fires once per new best, not once per set.
     */
    fun shouldFlash(
        weightKg: Float,
        isWarmup: Boolean,
        previousMax: Float?,
        sessionBestFlashed: Float?
    ): Boolean =
        !isWarmup && isPr(weightKg, previousMax) &&
            (sessionBestFlashed == null || weightKg > sessionBestFlashed)

    /**
     * The number the chip shows: the historical best working weight, raised to the session's
     * flashed best once an in-session PR lands. Null = no history = no chip (a first session
     * sets baselines, never targets).
     */
    fun chipTarget(previousMax: Float?, sessionBestFlashed: Float?): Float? = when {
        previousMax == null -> null
        sessionBestFlashed != null && sessionBestFlashed > previousMax -> sessionBestFlashed
        else -> previousMax
    }
}
