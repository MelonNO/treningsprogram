package com.migul.treningsprogram.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.thisMonday
import com.migul.treningsprogram.domain.model.OnboardingQuestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val workoutRepository: WorkoutRepository,
    val prefs: PreferencesManager,
    private val gymPresetDao: GymPresetDao,
    private val gson: Gson
) : ViewModel() {

    private val _questions = MutableStateFlow<List<OnboardingQuestion>>(emptyList())
    val questions = _questions.asStateFlow()

    private val _isLoadingQuestions = MutableStateFlow(false)
    val isLoadingQuestions = _isLoadingQuestions.asStateFlow()

    private val _questionsError = MutableStateFlow<String?>(null)
    val questionsError = _questionsError.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _generationDone = MutableStateFlow(false)
    val generationDone = _generationDone.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError = _generationError.asStateFlow()

    private val _attemptCount = MutableStateFlow(0)
    val attemptCount = _attemptCount.asStateFlow()

    fun loadQuestions(goal: String, experience: String) {
        viewModelScope.launch {
            _isLoadingQuestions.value = true
            _questionsError.value = null
            aiRepository.getOnboardingQuestions(goal, experience)
                .onSuccess { _questions.value = it }
                .onFailure { _questionsError.value = it.message }
            _isLoadingQuestions.value = false
        }
    }

    fun generateProgram(
        onboardingContext: String,
        goal: String,
        experience: String,
        daysPerWeek: Int,
        sessionDurationMinutes: Int,
        separateCardioDays: Boolean,
        apiKey: String
    ) {
        viewModelScope.launch {
            _isGenerating.value = true
            _generationError.value = null

            prefs.apiKey = apiKey
            prefs.fitnessGoal = goal
            prefs.experienceLevel = experience
            prefs.daysPerWeek = daysPerWeek
            prefs.sessionDurationMinutes = sessionDurationMinutes
            prefs.separateCardioDays = separateCardioDays
            prefs.onboardingContext = onboardingContext

            val preset = gymPresetDao.getById(prefs.selectedGymPresetId)
            val equipment: List<String> = preset?.let {
                runCatching {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson<List<String>>(it.equipmentJson, type)
                }.getOrElse { emptyList() }
            } ?: emptyList()

            aiRepository.generateAdaptedProgram(
                daysPerWeek = daysPerWeek,
                goal = goal,
                experience = experience,
                sessionDurationMinutes = sessionDurationMinutes,
                equipment = equipment,
                equipmentNotes = preset?.notes ?: "",
                separateCardioDays = separateCardioDays,
                onboardingContext = onboardingContext
            ).onSuccess { result ->
                workoutRepository.savePlan(thisMonday(), result.exercises)
                prefs.lastAutoGenerateWeek = SimpleDateFormat("yyyy-'W'ww", Locale.getDefault()).format(Date())
                prefs.lastGenerationAttemptCount = result.attemptCount
                prefs.hasCompletedOnboarding = true
                _attemptCount.value = result.attemptCount
                _generationDone.value = true
            }.onFailure { e ->
                _generationError.value = e.message
            }

            _isGenerating.value = false
        }
    }
}
