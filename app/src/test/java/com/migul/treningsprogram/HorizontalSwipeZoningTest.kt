package com.migul.treningsprogram

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.migul.treningsprogram.ui.widget.HorizontalSwipeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Swipe-zoning fix — guards HorizontalSwipeLayout's gesture arbitration against ancestor
 * pagers (History's sub-tab ViewPager2 stole the v1.27.0 week swipe on device):
 *
 *   • ACTION_DOWN claims the stream via parent.requestDisallowInterceptTouchEvent(true),
 *     so an ancestor pager cannot intercept a swipe that STARTS on the week view;
 *   • a drag that proves horizontal KEEPS the claim and fires the week-swipe callback;
 *   • a drag that proves vertical SURRENDERS the claim (scroll views must keep working)
 *     and never fires a swipe — even if the finger later moves horizontally;
 *   • sub-threshold horizontal drags fire nothing.
 *
 * HONESTY NOTE: these tests drive the widget's MotionEvent state machine directly on the
 * JVM. They prove the widget requests/releases parent non-interception correctly — they
 * CANNOT prove end-to-end that a real ViewPager2 on a device yields the gesture (the
 * original bug shipped despite green tests precisely because JVM tests don't exercise
 * real touch dispatch). Final proof of item 03 is the user's on-device check.
 */
@RunWith(RobolectricTestRunner::class)
class HorizontalSwipeZoningTest {

    /** Parent that records every requestDisallowInterceptTouchEvent it is asked for. */
    private class RecordingParent(context: Context) : FrameLayout(context) {
        val disallowCalls = mutableListOf<Boolean>()
        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            disallowCalls.add(disallowIntercept)
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    private lateinit var parent: RecordingParent
    private lateinit var swipe: HorizontalSwipeLayout
    private var rights = 0
    private var lefts = 0

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val slop by lazy { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    private val minSwipePx by lazy { 56f * context.resources.displayMetrics.density }

    @Before
    fun setUp() {
        parent = RecordingParent(context)
        swipe = HorizontalSwipeLayout(context)
        parent.addView(swipe)
        rights = 0; lefts = 0
        swipe.onSwipeRight = { rights++ }
        swipe.onSwipeLeft = { lefts++ }
    }

    private fun event(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0L, 0L, action, x, y, 0)

    /** DOWN via the intercept path (a child owns the stream), MOVEs likewise until the
     *  widget intercepts, then the remainder via onTouchEvent — mirroring real dispatch. */
    private fun drive(points: List<Pair<Float, Float>>, endAction: Int = MotionEvent.ACTION_UP) {
        var intercepted = swipe.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, points.first().first, points.first().second))
        for ((x, y) in points.drop(1)) {
            val ev = event(MotionEvent.ACTION_MOVE, x, y)
            if (!intercepted) intercepted = swipe.onInterceptTouchEvent(ev)
            else swipe.onTouchEvent(ev)
        }
        val end = event(endAction, points.last().first, points.last().second)
        if (intercepted) swipe.onTouchEvent(end) else swipe.onInterceptTouchEvent(end)
    }

    // ── The claim ──────────────────────────────────────────────────────────────────────

    @Test
    fun downClaimsParentNonInterception() {
        swipe.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 100f))
        assertEquals(listOf(true), parent.disallowCalls)
    }

    // ── Horizontal drags: swipe fires, claim is kept ───────────────────────────────────

    @Test
    fun horizontalRightSwipe_firesOlderCallback_andKeepsClaim() {
        drive(listOf(100f to 100f, 100f + slop + 4f to 102f, 100f + minSwipePx + 20f to 104f))
        assertEquals(1, rights)
        assertEquals(0, lefts)
        assertFalse("claim must never be surrendered on a horizontal drag",
            parent.disallowCalls.contains(false))
    }

    @Test
    fun horizontalLeftSwipe_firesNewerCallback() {
        drive(listOf(300f to 100f, 300f - slop - 4f to 102f, 300f - minSwipePx - 20f to 104f))
        assertEquals(0, rights)
        assertEquals(1, lefts)
        assertFalse(parent.disallowCalls.contains(false))
    }

    @Test
    fun shortHorizontalDrag_belowSwipeThreshold_firesNothing() {
        // Past slop (so the widget intercepts) but short of the 56dp swipe minimum.
        drive(listOf(100f to 100f, 100f + slop + 4f to 100f, 100f + slop + 8f to 100f))
        assertEquals(0, rights)
        assertEquals(0, lefts)
    }

    // ── Vertical drags: claim surrendered, no swipe ────────────────────────────────────

    @Test
    fun verticalDrag_surrendersClaim_soScrollingStillWorks() {
        drive(listOf(100f to 100f, 102f to 100f + slop + 4f, 104f to 100f + 200f))
        assertEquals(listOf(true, false), parent.disallowCalls)
        assertEquals(0, rights)
        assertEquals(0, lefts)
    }

    @Test
    fun verticalThenHorizontal_staysSurrendered_neverSwipes() {
        drive(listOf(
            100f to 100f,
            102f to 100f + slop + 4f,               // proves vertical → surrendered
            100f + minSwipePx + 40f to 100f + slop  // late horizontal must NOT reclaim
        ))
        assertEquals(listOf(true, false), parent.disallowCalls)
        assertEquals(0, rights)
        assertEquals(0, lefts)
    }

    // ── Stream reset ───────────────────────────────────────────────────────────────────

    @Test
    fun cancelResetsState_nextGestureWorksFresh() {
        drive(listOf(100f to 100f, 102f to 100f + slop + 4f), endAction = MotionEvent.ACTION_CANCEL)
        parent.disallowCalls.clear()
        drive(listOf(100f to 100f, 100f + slop + 4f to 100f, 100f + minSwipePx + 20f to 100f))
        assertTrue(parent.disallowCalls.first())
        assertEquals(1, rights)
    }

    @Test
    fun tapWithoutDrag_firesNothing() {
        drive(listOf(100f to 100f))
        assertEquals(0, rights)
        assertEquals(0, lefts)
    }
}
