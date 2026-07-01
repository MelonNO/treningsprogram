package com.migul.treningsprogram.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Item 8: app-scoped in-progress signal for a FULL-program generation launched from a Settings
 * screen (Training Profile "Generate now" or AI & Program "Generate Now").
 *
 * The Settings view model writes it around the generation; the Program tab observes it to show its
 * own generating animation if/when the user navigates there. This is ADDITIVE — the Settings screen
 * keeps its own status flow; both surfaces reflect the SAME generation via this single shared signal
 * ([A8-1]). It intentionally does NOT drive an auto-switch to the Program tab.
 *
 * A process-wide @Singleton so the Settings VM and the Program VM (distinct, per-fragment view
 * models) share one instance.
 */
@Singleton
class GenerationState @Inject constructor() {

    private val _fullGenerating = MutableStateFlow(false)
    /** True while a full-program generation launched from Settings is in progress. */
    val fullGenerating: StateFlow<Boolean> = _fullGenerating.asStateFlow()

    private val _status = MutableStateFlow("")
    /** The current progress/status text of that generation (empty when idle). */
    val status: StateFlow<String> = _status.asStateFlow()

    fun begin() { _status.value = ""; _fullGenerating.value = true }
    fun update(status: String) { _status.value = status }
    fun end() { _fullGenerating.value = false; _status.value = "" }
}
