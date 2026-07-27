package com.migul.treningsprogram

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.migul.treningsprogram.ui.widget.HorizontalSwipeLayout
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Pager mode of HorizontalSwipeLayout — the finger-following week-swipe animation:
 *
 *   • during a horizontal drag the FRONT pane follows the finger and the BACK pane
 *     (holding the adjacent page, rendered on demand via onPrepareAdjacent) peeks in from
 *     the revealed edge — left edge for a rightward drag (older), right edge for leftward;
 *   • release past the threshold swaps the pane roles synchronously and fires the swipe
 *     callback; short of the threshold nothing fires and the front pane settles home;
 *   • when onPrepareAdjacent reports no page (range end) the drag is heavily damped and
 *     clamped — the rubber-band nudge — and the swipe callback can never fire;
 *   • vertical drags surrender the stream and never move the panes (scrolling intact).
 *
 * HONESTY NOTE: like HorizontalSwipeZoningTest, these tests drive the widget's MotionEvent
 * state machine directly on the JVM. They prove the translation/role-swap/callback logic,
 * NOT real on-device touch dispatch, frame timing, or how the animation actually looks and
 * feels. Settle end-states additionally depend on Robolectric executing
 * ViewPropertyAnimator end-actions when the main looper is idled — a simulation, not a
 * real Choreographer. Final proof of the animation is the user's on-device check.
 */
@RunWith(RobolectricTestRunner::class)
class HorizontalSwipePagerTest {

    private lateinit var swipe: HorizontalSwipeLayout
    private lateinit var paneA: View
    private lateinit var paneB: View
    private var rights = 0
    private var lefts = 0
    private val prepareCalls = mutableListOf<Boolean>()
    private var adjacentExists = true

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val slop by lazy { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    private val minSwipePx by lazy { 56f * context.resources.displayMetrics.density }
    private val width = 1080

    @Before
    fun setUp() {
        swipe = HorizontalSwipeLayout(context)
        paneA = FrameLayout(context)
        paneB = FrameLayout(context)
        swipe.addView(paneA)
        swipe.addView(paneB)
        paneB.visibility = View.GONE   // the inflater path does this via onFinishInflate
        swipe.layout(0, 0, width, 600)
        rights = 0; lefts = 0
        prepareCalls.clear()
        adjacentExists = true
        swipe.onSwipeRight = { rights++ }
        swipe.onSwipeLeft = { lefts++ }
        swipe.onPrepareAdjacent = { fingerRight ->
            prepareCalls.add(fingerRight)
            adjacentExists
        }
    }

    private fun event(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0L, 0L, action, x, y, 0)

    private fun down(x: Float, y: Float = 100f) {
        swipe.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, x, y))
    }

    /** First MOVE goes through the intercept path (as in real dispatch); once intercepted,
     *  further MOVEs arrive via onTouchEvent. */
    private var intercepted = false
    private fun move(x: Float, y: Float = 100f) {
        val ev = event(MotionEvent.ACTION_MOVE, x, y)
        if (!intercepted) intercepted = swipe.onInterceptTouchEvent(ev)
        else swipe.onTouchEvent(ev)
    }

    private fun up(x: Float, y: Float = 100f) {
        swipe.onTouchEvent(event(MotionEvent.ACTION_UP, x, y))
        intercepted = false
    }

    private fun idleAnimations() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
    }

    // ── Finger-following drag ──────────────────────────────────────────────────────────────

    @Test
    fun rightDrag_frontFollowsFinger_backPeeksFromLeftEdge() {
        down(100f)
        move(100f + slop + 100f)
        assertEquals(100f, paneA.translationX, 0.5f)
        assertEquals(View.VISIBLE, paneB.visibility)
        assertEquals(100f - width, paneB.translationX, 0.5f)
        assertEquals(listOf(true), prepareCalls)   // fingerRight = true → older page
    }

    @Test
    fun leftDrag_backPeeksFromRightEdge() {
        down(900f)
        move(900f - slop - 150f)
        assertEquals(-150f, paneA.translationX, 0.5f)
        assertEquals(-150f + width, paneB.translationX, 0.5f)
        assertEquals(listOf(false), prepareCalls)  // fingerRight = false → newer page
    }

    @Test
    fun directionFlipMidDrag_reprepares_forTheOtherSide() {
        down(500f)
        move(500f + slop + 80f)          // right first
        move(500f - slop - 80f)          // then across zero to the left
        assertEquals(listOf(true, false), prepareCalls)
        assertEquals(-80f, paneA.translationX, 0.5f)
        assertEquals(-80f + width, paneB.translationX, 0.5f)
    }

    // ── Release: commit vs snap-back ───────────────────────────────────────────────────────

    @Test
    fun releasePastThreshold_swapsRolesSynchronously_andFiresCallback() {
        assertSame(paneA, swipe.frontView)
        down(100f)
        move(100f + slop + 20f)
        up(100f + minSwipePx + 40f)
        assertEquals(1, rights)
        assertEquals(0, lefts)
        assertSame("commit must promote the peeking pane to front", paneB, swipe.frontView)
        // After the settle animation runs, the outgoing pane is parked: hidden, at rest.
        idleAnimations()
        assertEquals(0f, paneB.translationX, 0.5f)
        assertEquals(View.GONE, paneA.visibility)
        assertEquals(0f, paneA.translationX, 0.5f)
    }

    @Test
    fun releaseBelowThreshold_snapsBack_firesNothing_keepsFront() {
        down(100f)
        move(100f + slop + 20f)
        up(100f + slop + 24f)
        assertEquals(0, rights)
        assertEquals(0, lefts)
        assertSame(paneA, swipe.frontView)
        idleAnimations()
        assertEquals(0f, paneA.translationX, 0.5f)
        assertEquals(View.GONE, paneB.visibility)
    }

    @Test
    fun cancelMidDrag_settlesBack_withoutCallback() {
        down(100f)
        move(100f + slop + 100f)
        swipe.onTouchEvent(event(MotionEvent.ACTION_CANCEL, 100f + slop + 100f, 100f))
        intercepted = false
        assertEquals(0, rights + lefts)
        assertSame(paneA, swipe.frontView)
        idleAnimations()
        assertEquals(0f, paneA.translationX, 0.5f)
        assertEquals(View.GONE, paneB.visibility)
    }

    // ── Range ends: rubber band, never a commit ────────────────────────────────────────────

    @Test
    fun edgeDrag_isDampedAndClamped_backPaneStaysHidden() {
        adjacentExists = false
        down(100f)
        move(100f + slop + 100f)
        assertEquals(30f, paneA.translationX, 0.5f)          // 100 * 0.3 damping
        assertEquals(View.GONE, paneB.visibility)
        move(100f + slop + 1000f)
        assertEquals(48f, paneA.translationX, 0.5f)          // clamped at 48dp (density 1)
    }

    @Test
    fun edgeRelease_beyondThresholdDistance_neverFiresCallback() {
        adjacentExists = false
        down(100f)
        move(100f + slop + 20f)
        up(100f + minSwipePx + 200f)
        assertEquals(0, rights)
        assertEquals(0, lefts)
        assertSame(paneA, swipe.frontView)
        idleAnimations()
        assertEquals(0f, paneA.translationX, 0.5f)
    }

    // ── Zoning still intact in pager mode ──────────────────────────────────────────────────

    @Test
    fun verticalDrag_neverMovesPanes_orPreparesAdjacent() {
        down(100f, 100f)
        move(102f, 100f + slop + 4f)
        move(104f, 100f + 300f)
        assertEquals(0f, paneA.translationX, 0.5f)
        assertEquals(View.GONE, paneB.visibility)
        assertTrue(prepareCalls.isEmpty())
        assertFalse(intercepted)
    }

    @Test
    fun newDownMidSettle_finishesSettleInstantly() {
        down(100f)
        move(100f + slop + 20f)
        up(100f + minSwipePx + 40f)      // commit; settle animation in flight
        assertSame(paneB, swipe.frontView)
        down(500f)                       // catch during settle → jump to rest state
        assertEquals(0f, paneB.translationX, 0.5f)
        assertEquals(View.VISIBLE, paneB.visibility)
        assertEquals(View.GONE, paneA.visibility)
        assertEquals(0f, paneA.translationX, 0.5f)
    }

    // ── Legacy mode (no panes) keeps the old contract ──────────────────────────────────────

    @Test
    fun withoutPanes_callbacksStillFireOnRelease() {
        val bare = HorizontalSwipeLayout(context)
        var fired = 0
        bare.onSwipeRight = { fired++ }
        assertNull(bare.backView)
        var i = bare.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 100f))
        val mv = event(MotionEvent.ACTION_MOVE, 100f + slop + 10f, 100f)
        if (!i) i = bare.onInterceptTouchEvent(mv)
        bare.onTouchEvent(event(MotionEvent.ACTION_UP, 100f + minSwipePx + 30f, 100f))
        assertTrue(i)
        assertEquals(1, fired)
    }
}
