package com.migul.treningsprogram.ui.log

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout

/**
 * Stage-3 item 16: a LinearLayout that lets an observer peek at every ACTION_DOWN before normal
 * dispatch, WITHOUT consuming it. Used as the log-screen root so an open weight keypad can be
 * dismissed by a tap anywhere outside it while the tap still performs its normal action
 * (pass-through, assumption A-16a).
 */
class TouchObservingLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Invoked with each ACTION_DOWN before children see it. Never consumes the event. */
    var onDispatchDown: ((MotionEvent) -> Unit)? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) onDispatchDown?.invoke(ev)
        return super.dispatchTouchEvent(ev)
    }
}
