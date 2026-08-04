package com.migul.treningsprogram.domain

import kotlin.math.log10

/**
 * Body-progress batch 2026-08-04 (brief 02) — body-fat estimation from tape measurements.
 *
 * The app stores ONLY raw girths ([com.migul.treningsprogram.data.db.entity.BodyMetric]); the body
 * fat percentage is DERIVED here on every read from the profile's current height/sex. That is
 * deliberate: assumption A5 allows a user to log measurements before they have set height/sex, and
 * deriving means those historical entries light up retroactively the moment the profile is filled
 * in (a stored percentage would have been frozen at null forever).
 *
 * The reported value is the equal-weight AVERAGE of the two standard published estimators
 * (user decision 3):
 *
 *  - **US Navy** (Hodgdon & Beckett), metric log10 form:
 *      men:   495 / (1.0324  − 0.19077·log10(waist − neck)       + 0.15456·log10(height)) − 450
 *      women: 495 / (1.29579 − 0.35004·log10(waist + hip − neck) + 0.22100·log10(height)) − 450
 *  - **RFM** (Relative Fat Mass, Woolcott & Bergman 2018):
 *      men:   64 − 20·(height / waist)
 *      women: 76 − 20·(height / waist)
 *
 * Everything is centimetres (assumption A1). No weight is involved anywhere and no fat MASS is
 * produced — the user explicitly withdrew that (decision 4).
 *
 * Pure object, no Android — all of it is unit-tested. Non-physical inputs return null rather than
 * being clamped, so a computed percentage is always the honest formula output; the UI is what
 * refuses absurd input (see [isPlausibleWaist] and friends).
 */
object BodyComposition {

    const val SEX_MALE = "Male"
    const val SEX_FEMALE = "Female"

    /** True when [sex] is a value the sex-specific formulas understand ("" = profile not set). */
    fun isKnownSex(sex: String?): Boolean = sex == SEX_MALE || sex == SEX_FEMALE

    /** Hip is collected and displayed for women only (user decision 2) — men never see the field. */
    fun needsHip(sex: String?): Boolean = sex == SEX_FEMALE

    /**
     * US Navy body-fat percentage, or null when the inputs cannot produce a defined result
     * (unknown sex, non-positive height, or a log10 argument <= 0 — e.g. a neck logged wider than
     * the waist). [hipCm] is required for women and ignored for men.
     */
    fun navy(sex: String?, heightCm: Float?, waistCm: Float?, neckCm: Float?, hipCm: Float?): Float? {
        if (!isKnownSex(sex)) return null
        val h = heightCm ?: return null
        val waist = waistCm ?: return null
        val neck = neckCm ?: return null
        if (h <= 0f || waist <= 0f || neck <= 0f) return null

        val result = if (sex == SEX_MALE) {
            val girth = waist - neck
            if (girth <= 0f) return null
            495.0 / (1.0324 - 0.19077 * log10(girth.toDouble()) + 0.15456 * log10(h.toDouble())) - 450.0
        } else {
            val hip = hipCm ?: return null
            if (hip <= 0f) return null
            val girth = waist + hip - neck
            if (girth <= 0f) return null
            495.0 / (1.29579 - 0.35004 * log10(girth.toDouble()) + 0.22100 * log10(h.toDouble())) - 450.0
        }
        return if (result.isFinite()) result.toFloat() else null
    }

    /**
     * Relative Fat Mass percentage, or null when sex is unknown or height/waist are non-positive.
     * RFM needs no neck or hip — but the app still only SHOWS a body fat number when the full
     * Navy input set is present, because the reported figure is the average of both (A4).
     */
    fun rfm(sex: String?, heightCm: Float?, waistCm: Float?): Float? {
        if (!isKnownSex(sex)) return null
        val h = heightCm ?: return null
        val waist = waistCm ?: return null
        if (h <= 0f || waist <= 0f) return null
        val base = if (sex == SEX_MALE) 64.0 else 76.0
        val result = base - 20.0 * (h.toDouble() / waist.toDouble())
        return if (result.isFinite()) result.toFloat() else null
    }

    /**
     * The body fat percentage the app displays: the equal-weight average of [navy] and [rfm].
     *
     * Returns null unless BOTH estimators are computable — i.e. height + sex are set on the profile
     * AND the entry carries waist + neck (plus hip for women, A4). Decision 4: waist + neck are the
     * trigger; an entry without them simply has no body-fat point on the chart.
     */
    fun estimate(
        sex: String?,
        heightCm: Float?,
        waistCm: Float?,
        neckCm: Float?,
        hipCm: Float? = null
    ): Float? {
        val navy = navy(sex, heightCm, waistCm, neckCm, hipCm) ?: return null
        val rfm = rfm(sex, heightCm, waistCm) ?: return null
        return (navy + rfm) / 2f
    }

    // ── Input sanity (UI-side guards) ───────────────────────────────────────────────────────
    // The formulas above never clamp; these keep obviously mistyped values (a 900 cm waist) out of
    // the database in the first place, which matters because a single absurd row would wreck the
    // chart scale for every other point.

    const val MIN_HEIGHT_CM = 100f
    const val MAX_HEIGHT_CM = 250f
    const val MIN_WAIST_CM = 30f
    const val MAX_WAIST_CM = 250f
    const val MIN_NECK_CM = 15f
    const val MAX_NECK_CM = 90f
    const val MIN_HIP_CM = 40f
    const val MAX_HIP_CM = 250f
    const val MIN_WEIGHT_KG = 20f
    const val MAX_WEIGHT_KG = 400f

    fun isPlausibleHeight(cm: Float): Boolean = cm in MIN_HEIGHT_CM..MAX_HEIGHT_CM
    fun isPlausibleWaist(cm: Float): Boolean = cm in MIN_WAIST_CM..MAX_WAIST_CM
    fun isPlausibleNeck(cm: Float): Boolean = cm in MIN_NECK_CM..MAX_NECK_CM
    fun isPlausibleHip(cm: Float): Boolean = cm in MIN_HIP_CM..MAX_HIP_CM
    fun isPlausibleWeight(kg: Float): Boolean = kg in MIN_WEIGHT_KG..MAX_WEIGHT_KG
}
