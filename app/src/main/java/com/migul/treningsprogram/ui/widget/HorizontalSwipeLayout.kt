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
 * child taps keep working). Used by the Program tab's week card to page between weeks.
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

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; intercepting = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!intercepting && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                    intercepting = true
                }
            }
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                if (intercepting && abs(dx) >= minSwipePx) {
                    if (dx > 0) onSwipeRight?.invoke() else onSwipeLeft?.invoke()
                }
                intercepting = false
            }
            MotionEvent.ACTION_CANCEL -> intercepting = false
        }
        return true
    }
}
