package com.migul.treningsprogram.ui.history

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.migul.treningsprogram.domain.ChartTouch
import kotlin.math.abs

/**
 * QoL item 09 — shared touch-to-read ("scrub") gesture for the hand-drawn Stats charts.
 * The owning view forwards onTouchEvent here and draws a marker at [selectedIndex].
 *
 * Cooperation with the vertically scrolling parent (NestedScrollView) works in two stages:
 *   • ACTION_DOWN selects the nearest point immediately but does NOT disallow parent
 *     interception — a vertical drag lets the scroller steal the gesture as usual (this view
 *     then receives ACTION_CANCEL and the marker clears), so the page never fights the chart;
 *   • once the finger's travel is clearly horizontal (|dx| > touchSlop and |dx| > |dy|) the
 *     gesture is claimed via requestDisallowInterceptTouchEvent(true) so the user can scrub
 *     along the series without a vertical wobble dropping the marker.
 *
 * A still press-and-hold keeps the marker without any claim — the scroller only intercepts on
 * vertical movement. The marker dismisses on finger-up or cancel (brief: "disappears naturally
 * on release").
 */
internal class ChartScrub(
    private val view: View,
    private val dates: () -> List<Long>
) {
    /** Index of the currently touched point in the view's series; -1 = no marker. */
    var selectedIndex = -1
        private set

    private var downX = 0f
    private var downY = 0f
    private var claimed = false
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop

    /** @return true when the event belongs to an active/starting scrub. */
    fun onTouchEvent(ev: MotionEvent, plotLeft: Float, plotWidth: Float): Boolean {
        val datesMs = dates()
        // Charts with < 2 points render the empty-state text — no marker there (no phantoms).
        if (datesMs.size < 2) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                claimed = false
                select(ChartTouch.nearestIndex(datesMs, plotLeft, plotWidth, ev.x))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!claimed) {
                    val dx = abs(ev.x - downX)
                    val dy = abs(ev.y - downY)
                    if (dx > slop && dx > dy) {
                        claimed = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                select(ChartTouch.nearestIndex(datesMs, plotLeft, plotWidth, ev.x))
                return true
            }
            MotionEvent.ACTION_UP -> {
                view.performClick()
                clear()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clear()
                return true
            }
        }
        return false
    }

    private fun select(index: Int) {
        if (index != selectedIndex) {
            selectedIndex = index
            view.invalidate()
        }
    }

    private fun clear() {
        claimed = false
        view.parent?.requestDisallowInterceptTouchEvent(false)
        if (selectedIndex != -1) {
            selectedIndex = -1
            view.invalidate()
        }
    }
}
