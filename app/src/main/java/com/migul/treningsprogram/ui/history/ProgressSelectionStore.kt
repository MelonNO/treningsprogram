package com.migul.treningsprogram.ui.history

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Body-progress batch 2026-08-04 (brief 03) — remembers which exercise the Progress tab is showing
 * for the LIFETIME OF THE PROCESS.
 *
 * The tab's default is re-rolled "once per app launch" and a manual switch sticks "for the rest of
 * the session" (user decision 11). [HistoryViewModel] cannot hold that: it is scoped to
 * [HistoryFragment], which is a navigation destination — walking to Home and back destroys and
 * recreates it, which would re-roll the default several times per launch.
 *
 * A Hilt `@Singleton` lives exactly as long as the process, which is the definition of "app launch"
 * here. Deliberately NOT persisted to disk — the next launch must be able to pick differently
 * (brief 03: "successive app launches can yield different default exercises").
 */
@Singleton
class ProgressSelectionStore @Inject constructor() {

    /**
     * The exercise the Progress tab should show, or null when nothing has been chosen yet this
     * launch (in which case the next tab open rolls the random default).
     */
    @Volatile
    var selected: String? = null
}
