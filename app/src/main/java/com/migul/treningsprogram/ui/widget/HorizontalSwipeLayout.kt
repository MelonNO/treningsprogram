package com.migul.treningsprogram.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * FrameLayout that recognises horizontal swipes ACROSS its whole area — including over
 * clickable children (it intercepts the stream once a horizontal drag is unambiguous, so
 * child taps keep working). Used by the History week view to page between weeks.
 *
 * Gesture arbitration against ANCESTOR pagers (the swipe-zoning fix, v1.28.0): History's
 * sub-tabs live in a ViewPager2, whose internal RecyclerView is an ancestor of this layout
 * and would otherwise intercept every horizontal drag before we ever see a MOVE — that is
 * exactly the bug that made the v1.27.0 week swipe unusable. So on ACTION_DOWN we claim
 * the stream with requestDisallowInterceptTouchEvent(true); if the drag then proves
 * VERTICAL we surrender the claim (so the enclosing scroll view scrolls normally), and if
 * it proves HORIZONTAL we keep it (so the swipe pages weeks, never sub-tabs). Touches
 * that start OUTSIDE this layout are untouched — the sub-tab pager keeps them.
 *
 * PAGER MODE (finger-following animation): when the layout has exactly TWO children they
 * act as pager panes — a FRONT pane showing the current page and a BACK pane for the
 * adjacent one. Once a drag proves horizontal, the front pane follows the finger and
 * [onPrepareAdjacent] asks the consumer to render the adjacent page into the back pane
 * (exposed as [backView]), which peeks in from the matching edge:
 *   • finger RIGHT (dx > 0, going BACK in time) → adjacent page enters from the LEFT;
 *   • finger LEFT → adjacent page enters from the RIGHT.
 * On release past [minSwipePx] the panes settle forward, the roles swap (synchronously —
 * animations only settle translations) and the swipe callback fires; short of the
 * threshold both panes snap back and nothing fires. If [onPrepareAdjacent] returns false
 * (no page that way) the drag rubber-bands with heavy damping and springs back on release
 * — the end-of-range nudge — and the swipe callback never fires.
 *
 * With fewer than two children (or no [onPrepareAdjacent] set) the widget degrades to the
 * old fire-on-release behaviour: same zoning, same threshold, no visuals.
 */
class HorizontalSwipeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** Called once per completed swipe. Right swipe = finger moves right (going BACK in time). */
    var onSwipeRight: (() -> Unit)? = null
    var onSwipeLeft: (() -> Unit)? = null

    /**
     * Pager-mode seam: called when a horizontal drag establishes (or flips) direction.
     * `fingerRight = true` means the finger moves right (revealing the OLDER page from the
     * left edge). The consumer renders the adjacent page into [backView] and returns true;
     * returning false means there is no page that way (range end → rubber band).
     * Unset ⇒ treated as "page exists" so release-callbacks keep firing (legacy behaviour).
     */
    var onPrepareAdjacent: ((fingerRight: Boolean) -> Boolean)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minSwipePx = 56f * resources.displayMetrics.density
    private val edgeMaxPx = 48f * resources.displayMetrics.density

    private var downX = 0f
    private var downY = 0f
    private var intercepting = false
    private var surrendered = false   // drag judged vertical → ancestors own the stream
    private var dragSign = 0          // +1 finger right, -1 finger left, 0 undetermined
    private var adjacentAvailable = false

    /** Which pane currently shows the committed page (pager mode only). */
    private var frontIndex = 0

    /** The pane showing the current page — consumers render the open page here. */
    val frontView: View? get() = if (childCount >= 2) getChildAt(frontIndex) else getChildAt(0)

    /** The pane [onPrepareAdjacent] should be rendered into (null outside pager mode). */
    val backView: View? get() = if (childCount >= 2) getChildAt(1 - frontIndex) else null

    override fun onFinishInflate() {
        super.onFinishInflate()
        backView?.visibility = GONE
    }

    /** Shared per-event state machine — DOWN/MOVE may arrive via intercept OR touch
     *  depending on whether a child claimed the DOWN; both paths must agree. */
    private fun onGestureEvent(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                intercepting = false; surrendered = false
                dragSign = 0; adjacentAvailable = false
                settleInstantly()   // catch a tap landing mid-settle: jump to the rest state
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (surrendered) return
                if (!intercepting) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                        intercepting = true
                    } else if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                        surrendered = true
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return
                    }
                }
                if (intercepting) updateDrag(ev.x - downX)
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
                val commit = intercepting && dragSign != 0 && adjacentAvailable &&
                    abs(dx) >= minSwipePx && (dx > 0f) == (dragSign > 0)
                if (commit) {
                    settleCommit()
                    if (dragSign > 0) onSwipeRight?.invoke() else onSwipeLeft?.invoke()
                } else if (intercepting) {
                    settleBack()
                }
                resetGesture()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (intercepting) settleBack()
                resetGesture()
            }
        }
        return true
    }

    private fun resetGesture() {
        intercepting = false; surrendered = false
        dragSign = 0; adjacentAvailable = false
    }

    // ── Pager visuals ──────────────────────────────────────────────────────────────────────

    /** Finger tracking: front pane follows the drag; back pane rides one width behind on
     *  the revealed side. Direction (re-)establishment asks [onPrepareAdjacent] once per
     *  side. Translation eases in from zero past the touch slop so the pickup is jump-free. */
    private fun updateDrag(dxTotal: Float) {
        val sign = if (dxTotal > 0f) 1 else if (dxTotal < 0f) -1 else 0
        if (sign != 0 && sign != dragSign) {
            dragSign = sign
            adjacentAvailable = onPrepareAdjacent?.invoke(sign > 0) ?: true
            backView?.let { back ->
                back.animate().cancel()
                back.visibility = if (adjacentAvailable) VISIBLE else GONE
            }
        }
        val front = frontView ?: return
        if (childCount < 2) return
        val visual = (abs(dxTotal) - touchSlop).coerceAtLeast(0f) * dragSign
        front.animate().cancel()
        if (adjacentAvailable) {
            front.translationX = visual
            backView?.translationX = visual - dragSign * width
        } else {
            front.translationX = (visual * EDGE_DRAG_FACTOR).coerceIn(-edgeMaxPx, edgeMaxPx)
        }
    }

    /** Commit: swap pane roles NOW (so consumers re-rendering "the front pane" from the
     *  swipe callback hit the pane already showing the new page), then let the animations
     *  merely settle translations — outgoing pane out, incoming pane to rest. */
    private fun settleCommit() {
        val outgoing = frontView ?: return
        val incoming = backView ?: return
        if (childCount < 2 || incoming.visibility != VISIBLE) return
        frontIndex = 1 - frontIndex
        outgoing.animate().cancel()
        incoming.animate().cancel()
        outgoing.animate()
            .translationX(dragSign * width.toFloat())
            .setDuration(SETTLE_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                outgoing.visibility = GONE
                outgoing.translationX = 0f
            }
            .start()
        incoming.animate()
            .translationX(0f)
            .setDuration(SETTLE_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** Below-threshold release or cancel: front snaps home; a peeking back pane slides out.
     *  At a range end the damped drag springs back with a slight overshoot — that bounce IS
     *  the end-of-range nudge. */
    private fun settleBack() {
        if (childCount < 2) return
        val front = frontView ?: return
        front.animate().cancel()
        val interpolator =
            if (adjacentAvailable) DecelerateInterpolator() else OvershootInterpolator()
        front.animate()
            .translationX(0f)
            .setDuration(SETTLE_MS)
            .setInterpolator(interpolator)
            .start()
        backView?.let { back ->
            if (back.visibility == VISIBLE && dragSign != 0) {
                back.animate().cancel()
                back.animate()
                    .translationX(-dragSign * width.toFloat())
                    .setDuration(SETTLE_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        back.visibility = GONE
                        back.translationX = 0f
                    }
                    .start()
            }
        }
    }

    /** A new touch landing while a settle animation is in flight: finish it instantly.
     *  frontIndex already points at the committed page, so snapping front to rest and
     *  hiding the back pane completes EITHER settle direction correctly. */
    private fun settleInstantly() {
        if (childCount < 2) return
        frontView?.let { it.animate().cancel(); it.translationX = 0f; it.visibility = VISIBLE }
        backView?.let { it.animate().cancel(); it.translationX = 0f; it.visibility = GONE }
    }

    private companion object {
        const val SETTLE_MS = 220L
        const val EDGE_DRAG_FACTOR = 0.3f
    }
}
