package com.migul.treningsprogram.ui.strength

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.repository.StrengthRepository
import com.migul.treningsprogram.domain.strength.StrengthProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Brief 02 (2026-08-07) — backs the Strength breakdown screen.
 *
 * Ratings are derived, never stored, so there is nothing to observe: the profile is *recomputed*
 * from logged history on every load. That is why this refreshes on resume rather than collecting a
 * Flow — the two things most likely to change a rating from this screen (setting your sex, logging
 * a weigh-in) both happen on OTHER screens, and the user comes back expecting the answer to have
 * moved.
 */
@HiltViewModel
class StrengthBreakdownViewModel @Inject constructor(
    private val strengthRepository: StrengthRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val profile: StrengthProfile? = null,
        /** Set only when the read itself failed; the screen then says so instead of showing zeros. */
        val failed: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, failed = false)
            val result = runCatching { strengthRepository.currentProfile() }
            _state.value = UiState(
                loading = false,
                profile = result.getOrNull(),
                failed = result.isFailure,
            )
        }
    }
}
