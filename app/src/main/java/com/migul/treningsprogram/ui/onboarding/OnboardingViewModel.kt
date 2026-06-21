package com.migul.treningsprogram.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.domain.model.OnboardingQuestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _questions = MutableStateFlow<List<OnboardingQuestion>>(emptyList())
    val questions = _questions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadQuestions(goal: String, experience: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            aiRepository.getOnboardingQuestions(goal, experience)
                .onSuccess { _questions.value = it }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }
}
