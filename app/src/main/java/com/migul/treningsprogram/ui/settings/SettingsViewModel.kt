package com.migul.treningsprogram.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.thisMonday
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefs: PreferencesManager,
    private val aiRepository: AiRepository,
    private val workoutRepository: WorkoutRepository,
    private val gamificationRepository: GamificationRepository,
    private val gymPresetDao: GymPresetDao,
    private val gson: Gson
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _generateStatus = MutableStateFlow<String?>(null)
    val generateStatus = _generateStatus.asStateFlow()

    private val _resetDone = MutableStateFlow(false)
    val resetDone = _resetDone.asStateFlow()

    private val _lastAttemptCount = MutableStateFlow(prefs.lastGenerationAttemptCount)
    val lastAttemptCount = _lastAttemptCount.asStateFlow()

    data class RetryEntry(val attempt: Int, val reason: String, val failed: Boolean)
    private val _retryLog = MutableStateFlow<List<RetryEntry>>(emptyList())
    val retryLog = _retryLog.asStateFlow()

    fun save(
        apiKey: String,
        daysPerWeek: Int,
        goal: String,
        experience: String,
        sessionDurationMinutes: Int = 60,
        separateCardioDays: Boolean = false,
        injuries: String = "",
        priorityMuscles: String = "",
        dislikedExercises: String = ""
    ) {
        prefs.apiKey = apiKey
        prefs.daysPerWeek = daysPerWeek
        prefs.fitnessGoal = goal
        prefs.experienceLevel = experience
        prefs.sessionDurationMinutes = sessionDurationMinutes
        prefs.separateCardioDays = separateCardioDays
        prefs.injuries = injuries
        prefs.priorityMuscles = priorityMuscles
        prefs.dislikedExercises = dislikedExercises
        _saved.value = true
        _saved.value = false
    }

    fun resetAllWorkouts() {
        viewModelScope.launch {
            workoutRepository.resetAllWorkouts()
            gamificationRepository.resetAll()
            _resetDone.value = true
            _resetDone.value = false
        }
    }

    fun generateProgram(daysPerWeek: Int, goal: String, experience: String, sessionDurationMinutes: Int) {
        doGenerate(daysPerWeek, goal, experience, sessionDurationMinutes)
    }

    fun generateProgramWithOnboarding(
        onboardingContext: String,
        daysPerWeek: Int,
        goal: String,
        experience: String,
        sessionDurationMinutes: Int
    ) {
        prefs.hasCompletedOnboarding = true
        prefs.onboardingContext = onboardingContext
        doGenerate(daysPerWeek, goal, experience, sessionDurationMinutes)
    }

    private fun doGenerate(daysPerWeek: Int, goal: String, experience: String, sessionDurationMinutes: Int) {
        viewModelScope.launch {
            _isGenerating.value = true
            _generateStatus.value = null
            _retryLog.value = emptyList()
            val capturedReasons = mutableListOf<String>()
            val preset = gymPresetDao.getById(prefs.selectedGymPresetId)
            val equipment: List<String> = preset?.let {
                runCatching {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson<List<String>>(it.equipmentJson, type)
                }.getOrElse { emptyList() }
            } ?: emptyList()
            val result = aiRepository.generateAdaptedProgram(
                daysPerWeek = daysPerWeek,
                goal = goal,
                experience = experience,
                sessionDurationMinutes = sessionDurationMinutes,
                equipment = equipment,
                equipmentNotes = preset?.notes ?: "",
                separateCardioDays = prefs.separateCardioDays,
                injuries = prefs.injuries,
                priorityMuscles = prefs.priorityMuscles,
                dislikedExercises = prefs.dislikedExercises,
                onboardingContext = prefs.onboardingContext,
                onProgress = { msg ->
                    _generateStatus.value = msg
                    val prefix = "Attempt "
                    val marker = " rejected: "
                    if (msg.startsWith(prefix) && msg.contains(marker)) {
                        capturedReasons.add(msg.substringAfter(marker))
                    }
                }
            )
            result.onSuccess { generationResult ->
                workoutRepository.savePlan(thisMonday(), generationResult.exercises)
                prefs.lastAutoGenerateWeek = java.text.SimpleDateFormat("yyyy-'W'ww", java.util.Locale.getDefault()).format(java.util.Date())
                prefs.lastGenerationAttemptCount = generationResult.attemptCount
                _lastAttemptCount.value = generationResult.attemptCount
                _retryLog.value = capturedReasons.mapIndexed { i, r -> RetryEntry(i + 1, r, false) }
                _generateStatus.value = if (capturedReasons.isNotEmpty())
                    "Program generated after ${generationResult.attemptCount} attempts (${capturedReasons.size} rejected)"
                else
                    "New program generated!"
            }.onFailure {
                _retryLog.value = capturedReasons.mapIndexed { i, r ->
                    RetryEntry(i + 1, r, i == capturedReasons.lastIndex)
                }
                _generateStatus.value = "Program rejected after all ${AiRepository.MAX_GENERATION_ATTEMPTS} attempts"
            }
            _isGenerating.value = false
        }
    }
}
