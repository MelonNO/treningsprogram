package com.migul.treningsprogram.ui.common

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import com.migul.treningsprogram.R

/**
 * Stage-3 item 2 — skeleton loaders for the data-heavy History sub-tabs.
 *
 * The skeleton containers are content-shaped blocks defined in the tab layouts
 * (@drawable/bg_skeleton_block); this helper only toggles them with a gentle
 * shimmer pulse (A-02b) and guards against flicker: [showDelayed] waits a beat
 * before appearing, so imperceptibly fast loads never flash a skeleton.
 */
object Skeleton {

    /** Loads faster than this never show a skeleton at all (no flicker). */
    private const val SHOW_DELAY_MS = 150L

    /** Shows [skeleton] with the shimmer pulse immediately. */
    fun show(skeleton: View) {
        cancelPending(skeleton)
        skeleton.visibility = View.VISIBLE
        skeleton.startAnimation(AlphaAnimation(1f, 0.45f).apply {
            duration = 650
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        })
    }

    /** Shows [skeleton] only if [hide] hasn't been called within the grace delay. */
    fun showDelayed(skeleton: View) {
        cancelPending(skeleton)
        val r = Runnable { show(skeleton) }
        skeleton.setTag(R.id.skeleton_pending_show, r)
        skeleton.postDelayed(r, SHOW_DELAY_MS)
    }

    /** Hides [skeleton] and cancels any pending delayed show. */
    fun hide(skeleton: View) {
        cancelPending(skeleton)
        skeleton.clearAnimation()
        skeleton.visibility = View.GONE
    }

    private fun cancelPending(skeleton: View) {
        (skeleton.getTag(R.id.skeleton_pending_show) as? Runnable)?.let {
            skeleton.removeCallbacks(it)
            skeleton.setTag(R.id.skeleton_pending_show, null)
        }
    }
}
