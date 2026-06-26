package com.migul.treningsprogram.ui.history

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Activity-scoped hand-off so external entry points (Home post-workout button,
 * the completion modal, History rows) can ask the History tab to open the Recap
 * sub-tab for a specific session.
 *
 * [pendingSessionId] == null means "the latest session". Both flags are consumed
 * (set back to false / null) by their readers so they only fire once.
 *
 * [pendingHighlightMuscle] (B06) is an optional fine-grain muscle label
 * (e.g. "Quads") that the Recap view should visually highlight: when a recovering
 * muscle on Home is tapped, the opened session highlights every exercise that hit
 * that muscle. It is one-shot — consumed via [consumeHighlightMuscle] — so sessions
 * opened by any other means (History rows, post-workout button) get no highlight.
 */
@HiltViewModel
class RecapTargetViewModel @Inject constructor() : ViewModel() {
    var pendingSessionId: Long? = null
    var openRequested: Boolean = false
    var pendingHighlightMuscle: String? = null

    /**
     * Request that History switch to the Recap tab for [sessionId] (null = latest),
     * optionally highlighting the exercises that hit [highlightMuscle] (null = no highlight).
     */
    fun request(sessionId: Long?, highlightMuscle: String? = null) {
        pendingSessionId = sessionId
        pendingHighlightMuscle = highlightMuscle
        openRequested = true
    }

    /** Consume the tab-switch request. */
    fun consumeOpenRequest(): Boolean {
        val r = openRequested
        openRequested = false
        return r
    }

    /** Consume the highlight request (one-shot): returns the muscle to highlight, then clears it. */
    fun consumeHighlightMuscle(): String? {
        val m = pendingHighlightMuscle
        pendingHighlightMuscle = null
        return m
    }
}
