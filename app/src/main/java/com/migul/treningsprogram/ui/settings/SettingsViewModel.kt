package com.migul.treningsprogram.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.BodyMeasurementDao
import com.migul.treningsprogram.data.db.dao.ExerciseDao
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.db.dao.PlannedExerciseDao
import com.migul.treningsprogram.data.db.dao.ProgramDao
import com.migul.treningsprogram.data.db.dao.WeeklySummaryDao
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.ExerciseResolutionLog
import com.migul.treningsprogram.data.CrashLog
import com.migul.treningsprogram.data.PromptLog
import com.migul.treningsprogram.data.RejectionLog
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.friendlyAiErrorMessage
import com.migul.treningsprogram.data.repository.ExportRepository
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.cloud.DriveBackupUploader
import com.migul.treningsprogram.data.cloud.GoogleDriveAuth
import com.migul.treningsprogram.data.backup.BackupScheduler
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
    private val exerciseDao: ExerciseDao,
    private val programDao: ProgramDao,
    private val weeklySummaryDao: WeeklySummaryDao,
    private val gson: Gson,
    private val exportRepository: ExportRepository,
    val googleDriveAuth: GoogleDriveAuth,
    private val driveBackupUploader: DriveBackupUploader,
    private val backupScheduler: BackupScheduler,
    private val resolutionLog: ExerciseResolutionLog,
    private val generationState: com.migul.treningsprogram.domain.GenerationState,
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

    // _saved is a dead field (no collector) — kept as SharedFlow stub to avoid removing a public API
    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _generateStatus = MutableStateFlow<String?>(null)
    val generateStatus = _generateStatus.asStateFlow()

    // S7 fix: _resetDone was MutableStateFlow(false) with value=true;value=false — StateFlow
    // conflates: both assignments may be observed as a single null-transition if the collector
    // hasn't resumed between them. Use SharedFlow (extraBufferCapacity=1) so the true emission
    // is buffered and delivered even if the collector is momentarily suspended.
    private val _resetDone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resetDone = _resetDone.asSharedFlow()

    private val _factoryResetDone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val factoryResetDone = _factoryResetDone.asSharedFlow()

    private val _exportJson = MutableStateFlow<String?>(null)
    val exportJson = _exportJson.asStateFlow()

    // S7 fix: _importResult and _cloudMessage were MutableStateFlow<String?>(null) using the
    // "value = msg; value = null" one-shot pattern. StateFlow conflates: both writes happen
    // synchronously on the same coroutine resume, so the non-null value can be missed by the
    // collector. Converted to SharedFlow (extraBufferCapacity=1) which buffers the message.
    private val _importResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val importResult = _importResult.asSharedFlow()

    // ---- Cloud backup (Google Drive appDataFolder) ----------------------------------------------

    /** Whether a real Web client ID has been configured (placeholder => false). */
    val cloudConfigured: Boolean get() = googleDriveAuth.isConfigured

    /** Display name/email of the signed-in Google account, or null. */
    private val _cloudAccount = MutableStateFlow<String?>(null)
    val cloudAccount = _cloudAccount.asStateFlow()

    /** Last cloud-backup timestamp (RFC 3339), or null if none / not signed in. */
    private val _cloudLastBackup = MutableStateFlow<String?>(null)
    val cloudLastBackup = _cloudLastBackup.asStateFlow()

    /** True while a cloud upload/download is in flight. */
    private val _cloudBusy = MutableStateFlow(false)
    val cloudBusy = _cloudBusy.asStateFlow()

    /** One-shot user-facing message for cloud operations. */
    private val _cloudMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val cloudMessage = _cloudMessage.asSharedFlow()

    /** Re-read the signed-in account and refresh the last-backup time. */
    fun refreshCloudStatus() {
        val account = googleDriveAuth.lastSignedInAccount()
        _cloudAccount.value = account?.email ?: account?.displayName
        if (account != null && googleDriveAuth.isConfigured) {
            viewModelScope.launch {
                runCatching { driveBackupUploader.lastBackupTime() }
                    .onSuccess { _cloudLastBackup.value = it }
                    .onFailure { _cloudLastBackup.value = null }
            }
        } else {
            _cloudLastBackup.value = null
        }
    }

    /** Call after a successful sign-in ActivityResult to update status. */
    fun onSignedIn() {
        refreshCloudStatus()
        _cloudMessage.tryEmit("Connected to Google Drive.")
    }

    fun onSignInFailed(reason: String?) {
        _cloudMessage.tryEmit("Google sign-in failed${if (reason != null) ": $reason" else "."}")
    }

    fun signOutCloud() {
        googleDriveAuth.signOut {
            _cloudAccount.value = null
            _cloudLastBackup.value = null
            _cloudMessage.tryEmit("Disconnected from Google Drive.")
        }
    }

    /** Export the current data and upload it to Drive appDataFolder. */
    fun backupToCloud() {
        viewModelScope.launch {
            _cloudBusy.value = true
            runCatching {
                val json = exportRepository.exportToJson()
                driveBackupUploader.upload(json)
            }.onSuccess {
                refreshCloudStatus()
                _cloudMessage.tryEmit("Backed up to cloud.")
            }.onFailure {
                _cloudMessage.tryEmit("Cloud backup failed: ${it.message}")
            }
            _cloudBusy.value = false
        }
    }

    /** Download the latest cloud backup and MERGE it into local data. */
    fun restoreFromCloud() {
        viewModelScope.launch {
            _cloudBusy.value = true
            runCatching {
                val json = driveBackupUploader.downloadLatest()
                    ?: throw IllegalStateException("No cloud backup found.")
                exportRepository.importFromJson(json)
            }.onSuccess {
                _cloudMessage.tryEmit("Restored from cloud.")
            }.onFailure {
                _cloudMessage.tryEmit("Cloud restore failed: ${it.message}")
            }
            _cloudBusy.value = false
        }
    }

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
        // Profile/settings changes are backup-eligible preferences (the API key is excluded by the
        // export engine). Request a coalesced backup; the apiKey write above is not what triggers it.
        backupScheduler.requestBackup()
        _saved.value = true
        _saved.value = false
    }

    fun exportBackup() {
        viewModelScope.launch {
            runCatching { exportRepository.exportToJson() }
                .onSuccess { _exportJson.value = it; _exportJson.value = null }
                .onFailure { _importResult.tryEmit("Export failed: ${it.message}") }
        }
    }

    fun importBackup(json: String) {
        viewModelScope.launch {
            runCatching { exportRepository.importFromJson(json) }
                .onSuccess { _importResult.tryEmit("Backup restored successfully!") }
                .onFailure { _importResult.tryEmit("Import failed: ${it.message}") }
        }
    }

    fun consumeExportJson(block: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { exportRepository.exportToJson() }
                .onSuccess { block(it) }
                .onFailure { _importResult.tryEmit("Export failed: ${it.message}") }
        }
    }

    fun resetAllWorkouts() {
        viewModelScope.launch {
            workoutRepository.resetAllWorkouts()
            gamificationRepository.resetAll()
            resolutionLog.clearMisses()
            refreshUnrecognized()
            _resetDone.tryEmit(Unit)
        }
    }

    fun factoryReset() {
        viewModelScope.launch {
            // Clear ALL DB tables so the app is truly "freshly installed" after restart.
            workoutRepository.resetAllWorkouts()   // sessions + sets
            gamificationRepository.resetAll()       // user_stats + achievements
            plannedExerciseDao.deleteAll()
            weeklySummaryDao.deleteAll()            // S7 fix: coach summaries were not cleared
            programDao.deleteAll()                  // S7 fix: named programs were not cleared
            exerciseDao.deleteAll()                 // S7 fix: exercise library was not cleared
            gymPresetDao.deleteAll()
            AppDatabase.seedPresets(gymPresetDao)
            bodyMeasurementDao.deleteAll()
            resolutionLog.clearMisses()
            // Clear all preferences (API key, onboarding status, profile, everything).
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
            // Item 8: publish the app-scoped signal so the Program tab can show a generating animation.
            generationState.begin()
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
            // B08: honour the user's rest-day selection (rest-day mode derives days/week; count mode
            // falls back to the passed-in count). Item 9: this Settings generate path now PRESERVES
            // already-logged days and rebalances the rest of the week around them — reusing the same
            // keep-logged mechanism as the Program tab's "Regenerate (keep logged days)" (feed the
            // logged days to the model as fixed context; persist via savePlanPreservingLoggedDays).
            val monday = thisMonday()
            val eff = com.migul.treningsprogram.domain.TrainingDaySelection.effective(prefs.restDaysCsv, daysPerWeek)
            val mesocycle = workoutRepository.buildRegenMesocycle(monday)
            val currentPlan = workoutRepository.getActiveProgramPlanForWeek(monday)
            val loggedDays = com.migul.treningsprogram.domain.RegeneratePlanner.loggedDays(currentPlan)
            val lockedExercises = com.migul.treningsprogram.domain.RegeneratePlanner.lockedExercises(currentPlan)
            val result = aiRepository.generateAdaptedProgram(
                daysPerWeek = eff.daysPerWeek,
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
                mesocycle = mesocycle,
                restDays = eff.restDays,
                lockedExercises = lockedExercises,
                onProgress = { msg ->
                    _generateStatus.value = msg
                    generationState.update(msg)   // Item 8: mirror status to the Program-tab animation
                    val prefix = "Attempt "
                    val marker = " rejected: "
                    if (msg.startsWith(prefix) && msg.contains(marker)) {
                        capturedReasons.add(msg.substringAfter(marker))
                    }
                }
            )
            result.onSuccess { generationResult ->
                // B2: stamp the week's rationale onto every row so any row of the week carries it.
                // Item 9: preserve logged days — only NON-logged days are cleared/replaced.
                workoutRepository.savePlanPreservingLoggedDays(
                    monday,
                    generationResult.exercises.map { it.copy(rationale = generationResult.rationale) },
                    loggedDays
                )
                workoutRepository.setActiveDeload(mesocycle.isDeload)
                prefs.lastAutoGenerateWeek = autoGenWeekKey()
                prefs.lastGenerationAttemptCount = generationResult.attemptCount
                _lastAttemptCount.value = generationResult.attemptCount
                _retryLog.value = capturedReasons.mapIndexed { i, r -> RetryEntry(i + 1, r, false) }
                _generateStatus.value = if (capturedReasons.isNotEmpty())
                    "Program generated after ${generationResult.attemptCount} attempts (${capturedReasons.size} rejected)"
                else
                    "New program generated!"
            }.onFailure { e ->
                _retryLog.value = capturedReasons.mapIndexed { i, r ->
                    RetryEntry(i + 1, r, i == capturedReasons.lastIndex)
                }
                _generateStatus.value = if (e is IllegalStateException && e.message?.startsWith("Program rejected") == true)
                    "Program rejected after all ${AiRepository.MAX_GENERATION_ATTEMPTS} attempts"
                else
                    friendlyAiErrorMessage(e)
            }
            _isGenerating.value = false
            generationState.end()   // Item 8: clear the Program-tab animation (success or failure)
        }
    }
}
