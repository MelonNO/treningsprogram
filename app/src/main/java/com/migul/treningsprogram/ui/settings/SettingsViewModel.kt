package com.migul.treningsprogram.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.BodyMeasurementDao
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.db.dao.PlannedExerciseDao
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.ExerciseResolutionLog
import com.migul.treningsprogram.data.CrashLog
import com.migul.treningsprogram.data.PromptLog
import com.migul.treningsprogram.data.RejectionLog
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.ExportRepository
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.repository.autoGenWeekKey
import com.migul.treningsprogram.data.repository.thisMonday
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val plannedExerciseDao: PlannedExerciseDao,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val gson: Gson,
    private val exportRepository: ExportRepository,
    private val resolutionLog: ExerciseResolutionLog,
    val promptLog: PromptLog,
    val rejectionLog: RejectionLog,
    val crashLog: CrashLog
) : ViewModel() {

    private val _promptLogEntries = MutableStateFlow<List<PromptLog.Entry>>(emptyList())
    val promptLogEntries = _promptLogEntries.asStateFlow()

    fun refreshPromptLog() { _promptLogEntries.value = promptLog.getAll() }
    fun clearPromptLog() { promptLog.clear(); _promptLogEntries.value = emptyList() }

    private val _rejectionLogSessions = MutableStateFlow<List<RejectionLog.Session>>(emptyList())
    val rejectionLogSessions = _rejectionLogSessions.asStateFlow()

    fun refreshRejectionLog() { _rejectionLogSessions.value = rejectionLog.getAll() }
    fun clearRejectionLog() { rejectionLog.clear(); _rejectionLogSessions.value = emptyList() }

    private val _crashLogEntries = MutableStateFlow<List<CrashLog.Entry>>(emptyList())
    val crashLogEntries = _crashLogEntries.asStateFlow()

    fun refreshCrashLog() { _crashLogEntries.value = crashLog.getAll() }
    fun clearCrashLog() { crashLog.clear(); _crashLogEntries.value = emptyList() }

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _generateStatus = MutableStateFlow<String?>(null)
    val generateStatus = _generateStatus.asStateFlow()

    private val _resetDone = MutableStateFlow(false)
    val resetDone = _resetDone.asStateFlow()

    private val _factoryResetDone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val factoryResetDone = _factoryResetDone.asSharedFlow()

    private val _exportJson = MutableStateFlow<String?>(null)
    val exportJson = _exportJson.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult = _importResult.asStateFlow()

    private val _lastAttemptCount = MutableStateFlow(prefs.lastGenerationAttemptCount)
    val lastAttemptCount = _lastAttemptCount.asStateFlow()

    data class RetryEntry(val attempt: Int, val reason: String, val failed: Boolean)
    private val _retryLog = MutableStateFlow<List<RetryEntry>>(emptyList())
    val retryLog = _retryLog.asStateFlow()

    // Exercises the image resolver couldn't match — shown in Settings for easy copy/report
    private val _unrecognizedExercises = MutableStateFlow<List<String>>(emptyList())
    val unrecognizedExercises = _unrecognizedExercises.asStateFlow()

    init {
        refreshUnrecognized()
    }

    fun refreshUnrecognized() {
        _unrecognizedExercises.value = resolutionLog.getMissReport().map { it.first }
    }

    fun clearUnrecognized() {
        resolutionLog.clearMisses()
        refreshUnrecognized()
    }

    fun save(
        apiKey: String,
        daysPerWeek: Int,
        goal: String,
        experience: String,
        sessionDurationMinutes: Int = 60,
        separateCardioDays: Boolean = false,
        injuries: String = "",
        injurySeverity: String = "",
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
        prefs.injurySeverity = injurySeverity
        prefs.priorityMuscles = priorityMuscles
        prefs.dislikedExercises = dislikedExercises
        _saved.value = true
        _saved.value = false
    }

    fun exportBackup() {
        viewModelScope.launch {
            runCatching { exportRepository.exportToJson() }
                .onSuccess { _exportJson.value = it; _exportJson.value = null }
                .onFailure { _importResult.value = "Export failed: ${it.message}"; _importResult.value = null }
        }
    }

    fun importBackup(json: String) {
        viewModelScope.launch {
            runCatching { exportRepository.importFromJson(json) }
                .onSuccess { _importResult.value = "Backup restored successfully!"; _importResult.value = null }
                .onFailure { _importResult.value = "Import failed: ${it.message}"; _importResult.value = null }
        }
    }

    fun consumeExportJson(block: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { exportRepository.exportToJson() }
                .onSuccess { block(it) }
                .onFailure { _importResult.value = "Export failed: ${it.message}"; _importResult.value = null }
        }
    }

    fun resetAllWorkouts() {
        viewModelScope.launch {
            workoutRepository.resetAllWorkouts()
            gamificationRepository.resetAll()
            resolutionLog.clearMisses()
            refreshUnrecognized()
            _resetDone.value = true
            _resetDone.value = false
        }
    }

    fun factoryReset() {
        viewModelScope.launch {
            // Clear all DB tables
            workoutRepository.resetAllWorkouts()   // sessions + sets
            gamificationRepository.resetAll()       // user_stats + achievements
            plannedExerciseDao.deleteAll()
            gymPresetDao.deleteAll()
            AppDatabase.seedPresets(gymPresetDao)
            bodyMeasurementDao.deleteAll()
            resolutionLog.clearMisses()
            // Clear all preferences (API key, onboarding status, profile, everything)
            prefs.clearAll()
            _factoryResetDone.tryEmit(Unit)
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
                injurySeverity = prefs.injurySeverity,
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
                prefs.lastAutoGenerateWeek = autoGenWeekKey()
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
