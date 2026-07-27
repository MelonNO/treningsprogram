package com.migul.treningsprogram.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * FrameLayout that recognises horizontal swipes ACROSS its whole area — including over
 * clickable children (it intercepts the stream once a horizontal drag is unambiguous, so
 * child taps keep working). Used by the History week view to page between weeks.
 *
 * Gesture arbitration against ANCESTOR pagers (the swipe-zoning fix): History's sub-tabs
 * live in a ViewPager2, whose internal RecyclerView is an ancestor of this layout and
 * would otherwise intercept every horizontal drag before we ever see a MOVE — that is
 * exactly the bug that made the v1.27.0 week swipe unusable. So on ACTION_DOWN we claim
 * the stream with requestDisallowInterceptTouchEvent(true); if the drag then proves
 * VERTICAL we surrender the claim (so the enclosing scroll view scrolls normally), and if
 * it proves HORIZONTAL we keep it (so the swipe pages weeks, never sub-tabs). Touches
 * that start OUTSIDE this layout are untouched — the sub-tab pager keeps them.
 */
class HorizontalSwipeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** Called once per completed swipe. Right swipe = finger moves right (going BACK in time). */
    var onSwipeRight: (() -> Unit)? = null
    var onSwipeLeft: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minSwipePx = 56f * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var intercepting = false
    private var surrendered = false   // drag judged vertical → ancestors own the stream

    /** Shared per-event state machine — DOWN/MOVE may arrive via intercept OR touch
     *  depending on whether a child claimed the DOWN; both paths must agree. */
    private fun onGestureEvent(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                intercepting = false; surrendered = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (intercepting || surrendered) return
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                    intercepting = true
                } else if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                    surrendered = true
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        onGestureEvent(ev)
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        onGestureEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                if (intercepting && abs(dx) >= minSwipePx) {
                    if (dx > 0) onSwipeRight?.invoke() else onSwipeLeft?.invoke()
                }
                intercepting = false; surrendered = false
            }
            MotionEvent.ACTION_CANCEL -> {
                intercepting = false; surrendered = false
            }
        }
        return true
    }
}
