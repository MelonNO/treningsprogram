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
 */
@HiltViewModel
class RecapTargetViewModel @Inject constructor() : ViewModel() {
    var pendingSessionId: Long? = null
    var openRequested: Boolean = false

    /** Request that History switch to the Recap tab for [sessionId] (null = latest). */
    fun request(sessionId: Long?) {
        pendingSessionId = sessionId
        openRequested = true
    }

    /** Consume the tab-switch request. */
    fun consumeOpenRequest(): Boolean {
        val r = openRequested
        openRequested = false
        return r
    }
}
