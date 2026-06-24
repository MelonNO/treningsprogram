package com.migul.treningsprogram.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.autoGenWeekKey
import com.migul.treningsprogram.data.repository.thisMonday
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val workoutRepository: WorkoutRepository,
    val prefs: PreferencesManager,
    private val gymPresetDao: GymPresetDao,
    private val gson: Gson
) : ViewModel() {

    val presets = gymPresetDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _generationDone = MutableStateFlow(false)
    val generationDone = _generationDone.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError = _generationError.asStateFlow()

    private val _generationStatus = MutableStateFlow("")
    val generationStatus = _generationStatus.asStateFlow()

    private val _attemptCount = MutableStateFlow(0)
    val attemptCount = _attemptCount.asStateFlow()

    private val _rejectionReasons = MutableStateFlow<List<String>>(emptyList())
    val rejectionReasons = _rejectionReasons.asStateFlow()

    fun selectPreset(id: Long) {
        prefs.selectedGymPresetId = id
    }

    fun addPresetAndSelect(name: String, equipment: List<String>, notes: String) {
        viewModelScope.launch {
            val id = gymPresetDao.insert(GymPreset(name = name, equipmentJson = gson.toJson(equipment), notes = notes))
            prefs.selectedGymPresetId = id
        }
    }

    fun getEquipmentList(preset: GymPreset): List<String> = runCatching {
        val type = object : TypeToken<List<String>>() {}.type
        gson.fromJson<List<String>>(preset.equipmentJson, type) ?: emptyList()
    }.getOrElse { emptyList() }

    fun generateProgram(
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

            val preset = gymPresetDao.getById(prefs.selectedGymPresetId)
            val equipment = getEquipmentFromSelectedPreset()
            prefs.wizardEquipment = equipment.joinToString(",")

            aiRepository.generateAdaptedProgram(
                daysPerWeek = daysPerWeek,
                goal = goal,
                experience = experience,
                sessionDurationMinutes = sessionDurationMinutes,
                equipment = equipment,
                equipmentNotes = preset?.notes ?: "",
                separateCardioDays = separateCardioDays,
                injuries = prefs.injuries,
                injurySeverity = prefs.injurySeverity,
                priorityMuscles = prefs.priorityMuscles,
                dislikedExercises = prefs.dislikedExercises,
                onProgress = { _generationStatus.value = it }
            ).onSuccess { result ->
                workoutRepository.savePlan(thisMonday(), result.exercises)
                prefs.lastAutoGenerateWeek = autoGenWeekKey()
                prefs.lastGenerationAttemptCount = result.attemptCount
                prefs.hasCompletedOnboarding = true
                _attemptCount.value = result.attemptCount
                _rejectionReasons.value = result.rejectionReasons
                _generationDone.value = true
            }.onFailure { e ->
                _generationError.value = e.message
            }

            _isGenerating.value = false
        }
    }

    private suspend fun getEquipmentFromSelectedPreset(): List<String> {
        val preset = gymPresetDao.getById(prefs.selectedGymPresetId) ?: return emptyList()
        return getEquipmentList(preset)
    }
}
