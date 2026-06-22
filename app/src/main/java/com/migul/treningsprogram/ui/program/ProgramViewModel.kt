package com.migul.treningsprogram.ui.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.data.repository.thisMonday
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProgramViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val aiRepository: AiRepository,
    private val prefsManager: PreferencesManager,
    private val gymPresetDao: GymPresetDao,
    private val gson: Gson
) : ViewModel() {

    init {
        viewModelScope.launch { workoutRepository.backfillPlannedExercises() }
    }

    val weekPlan: StateFlow<List<PlannedExercise>> =
        workoutRepository.getPlannedForWeek(thisMonday())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDay = MutableStateFlow(currentDayOfWeek())
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    val selectedDayExercises: StateFlow<List<PlannedExercise>> =
        combine(weekPlan, selectedDay) { plan, day -> plan.filter { it.dayOfWeek == day } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekProgress: StateFlow<Pair<Int, Int>> =
        weekPlan.map { plan ->
            plan.count { it.isLogged } to plan.size
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0)

    val presets: StateFlow<List<GymPreset>> = gymPresetDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPresetId: Long get() = prefsManager.selectedGymPresetId

    fun getEquipmentForPreset(presetId: Long): List<String> {
        if (presetId == -1L) return emptyList()
        val preset = presets.value.find { it.id == presetId } ?: return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(preset.equipmentJson, object : TypeToken<List<String>>() {}.type)
        }.getOrElse { emptyList() }
    }

    fun getNotesForPreset(presetId: Long): String {
        if (presetId == -1L) return ""
        return presets.value.find { it.id == presetId }?.notes ?: ""
    }

    fun selectDay(day: Int) { _selectedDay.value = day }

    fun logExercise(exercise: PlannedExercise, actualWeight: Float, actualReps: String, actualSets: Int) {
        viewModelScope.launch {
            workoutRepository.updatePlannedExercise(
                exercise.copy(isLogged = true, actualWeightKg = actualWeight, actualReps = actualReps, actualSets = actualSets)
            )
        }
    }

    fun unlogExercise(exercise: PlannedExercise) {
        viewModelScope.launch {
            workoutRepository.updatePlannedExercise(
                exercise.copy(isLogged = false, actualWeightKg = 0f, actualReps = "", actualSets = 0)
            )
        }
    }

    fun swapExercise(exercise: PlannedExercise, newName: String) {
        viewModelScope.launch {
            workoutRepository.updatePlannedExercise(
                exercise.copy(exerciseName = newName, isLogged = false, actualWeightKg = 0f, actualReps = "", actualSets = 0)
            )
        }
    }

    private val _isDayGenerating = MutableStateFlow(false)
    val isDayGenerating: StateFlow<Boolean> = _isDayGenerating.asStateFlow()

    private val _dayGenerationError = MutableStateFlow<String?>(null)
    val dayGenerationError: StateFlow<String?> = _dayGenerationError.asStateFlow()

    private val _dayGenerationStatus = MutableStateFlow("")
    val dayGenerationStatus: StateFlow<String> = _dayGenerationStatus.asStateFlow()

    fun clearDayGenerationError() { _dayGenerationError.value = null }

    fun regenerateDay(dayOfWeek: Int, equipment: List<String>, equipmentNotes: String, muscleFocus: String = "") {
        if (prefsManager.apiKey.isBlank()) {
            _dayGenerationError.value = "Set your API key in Profile → Settings first."
            return
        }
        viewModelScope.launch {
            _isDayGenerating.value = true
            _dayGenerationError.value = null
            val weekStart = thisMonday()
            val currentPlan = weekPlan.value

            aiRepository.generateSingleDayProgram(
                dayOfWeek = dayOfWeek,
                equipment = equipment,
                equipmentNotes = equipmentNotes,
                goal = prefsManager.fitnessGoal,
                experience = prefsManager.experienceLevel,
                sessionDurationMinutes = prefsManager.sessionDurationMinutes,
                existingWeekPlan = currentPlan,
                injuries = prefsManager.injuries,
                priorityMuscles = prefsManager.priorityMuscles,
                dislikedExercises = prefsManager.dislikedExercises,
                muscleFocus = muscleFocus,
                onProgress = { _dayGenerationStatus.value = it }
            ).onSuccess { exercises ->
                workoutRepository.saveDayPlan(weekStart, dayOfWeek, exercises)
                _dayGenerationStatus.value = ""
            }.onFailure { e ->
                _dayGenerationError.value = e.message ?: "Generation failed"
                _dayGenerationStatus.value = ""
            }
            _isDayGenerating.value = false
        }
    }
}
