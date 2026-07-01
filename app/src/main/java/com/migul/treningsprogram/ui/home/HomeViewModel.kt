package com.migul.treningsprogram.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.backup.BackupScheduler
import com.migul.treningsprogram.data.db.dao.BodyMeasurementDao
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.preferences.DailyChallengeManager
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.data.repository.thisMonday
import com.migul.treningsprogram.domain.MuscleRecovery
import com.migul.treningsprogram.domain.model.DailyChallenge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val gamificationRepository: GamificationRepository,
    private val dailyChallengeManager: DailyChallengeManager,
    private val gymPresetDao: GymPresetDao,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val backupScheduler: BackupScheduler,
    val prefs: PreferencesManager
) : ViewModel() {

    val isFirstLaunch: Boolean get() = !prefs.hasCompletedOnboarding

    val userStats: StateFlow<UserStats?> = gamificationRepository.userStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeSession: StateFlow<WorkoutSession?> = workoutRepository.allSessions
        .map { it.firstOrNull { s -> !s.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayCompleted: StateFlow<Boolean> = workoutRepository.getAllCompletedSessions()
        .map { sessions ->
            // Item 7: "completed today" uses the LOGICAL day (a 01:00 session counts as the previous
            // day), so it agrees with today's-plan / streaks / History.
            val todayEpoch = com.migul.treningsprogram.domain.DayBoundary.todayEpochDay()
            sessions.any {
                com.migul.treningsprogram.domain.DayBoundary.logicalEpochDay(it.dateMs) == todayEpoch
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _todayPlan = MutableStateFlow<List<PlannedExercise>>(emptyList())
    val todayPlan: StateFlow<List<PlannedExercise>> = _todayPlan.asStateFlow()

    data class SessionSummary(
        val session: WorkoutSession,
        val setCount: Int,
        val exerciseCount: Int,
        val focusMuscle: String
    )

    private val _recentSessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val recentSessions: StateFlow<List<SessionSummary>> = _recentSessions.asStateFlow()

    private val _challenges = MutableStateFlow<List<DailyChallenge>>(emptyList())
    val challenges: StateFlow<List<DailyChallenge>> = _challenges.asStateFlow()

    val bodyMeasurements: StateFlow<List<BodyMeasurement>> =
        bodyMeasurementDao.getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Recovery item for one fine-grain muscle (U1).
     *
     * @param muscleLabel    fine-grain label (e.g. "Front Delts", "Quads")
     * @param state          RECOVERING only (items in other states are filtered out)
     * @param lastTrainedMs  epoch ms of the most relevant session
     * @param lastSessionId  id of the session to navigate to on tap
     * @param recoveryFraction  0..1: how far through the 48 h window (1.0 = almost ready)
     * @param remainingMs    remaining recovery millis
     */
    data class MuscleRecoveryItem(
        val muscleLabel: String,
        val state: MuscleRecovery.RecoveryState,
        val lastTrainedMs: Long,
        val lastSessionId: Long,
        val recoveryFraction: Float,
        val remainingMs: Long
    )

    /**
     * Live stream of RECOVERING-only fine-grain muscle items, ordered head-to-toe by the
     * canonical ALL_FINE_MUSCLES display order. Each item carries the remaining recovery
     * time and the last session ID for tap navigation.
     *
     * Empty when no muscles are currently recovering (first run, or all muscles rested).
     */
    val muscleRecovery: StateFlow<List<MuscleRecoveryItem>> =
        workoutRepository.observeExerciseSessionRows()
            .map { rows ->
                buildWeightedRecoveryItems(rows, System.currentTimeMillis())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Pure function: builds the list of RECOVERING items from raw exercise-session rows.
     * Extracted for testability.
     *
     * Algorithm:
     *   1. For each row, call MuscleClassifier.finerMusclesFor to get (fineLabel, weight) pairs.
     *   2. Accumulate ExerciseStimulusRecords per fine label.
     *   3. For each fine label, call MuscleRecovery.computeRecovery.
     *   4. Keep only RECOVERING results, map to MuscleRecoveryItem.
     *   5. Sort by ALL_FINE_MUSCLES order (stable, head-to-toe).
     */
    internal fun buildWeightedRecoveryItems(
        rows: List<com.migul.treningsprogram.data.db.dao.ExerciseSessionRow>,
        nowMs: Long
    ): List<MuscleRecoveryItem> {
        // Accumulate stimulus records per fine-grain muscle label
        val stimuliByMuscle = mutableMapOf<String, MutableList<MuscleRecovery.ExerciseStimulusRecord>>()

        for (row in rows) {
            val fineMuscles = MuscleClassifier.finerMusclesFor(row.exerciseName)
            for ((fineLabel, weight) in fineMuscles) {
                stimuliByMuscle.getOrPut(fineLabel) { mutableListOf() }.add(
                    MuscleRecovery.ExerciseStimulusRecord(
                        sessionId = row.sessionId,
                        sessionDateMs = row.sessionDateMs,
                        exerciseName = row.exerciseName,
                        weight = weight,
                        // Item 12: carry this exercise/session's logged effort so recovery scales by it.
                        effortLevel = row.effortLevel
                    )
                )
            }
        }

        // For each fine-grain muscle, compute recovery and keep only RECOVERING
        val orderIndex = MuscleClassifier.ALL_FINE_MUSCLES.withIndex()
            .associate { (idx, label) -> label to idx }

        return stimuliByMuscle.entries.mapNotNull { (label, stimuli) ->
            // Item 12: per-muscle base recovery window (effort scaling applied inside computeRecovery).
            val result = MuscleRecovery.computeRecovery(
                stimuli, nowMs, MuscleRecovery.baseRecoveryMsFor(label)
            ) ?: return@mapNotNull null
            if (result.state != MuscleRecovery.RecoveryState.RECOVERING) return@mapNotNull null
            MuscleRecoveryItem(
                muscleLabel = label,
                state = result.state,
                lastTrainedMs = result.lastTrainedMs,
                lastSessionId = result.lastSessionId,
                recoveryFraction = result.recoveryFraction,
                remainingMs = result.remainingMs
            )
        }.sortedBy { orderIndex[it.muscleLabel] ?: Int.MAX_VALUE }
    }

    fun addBodyWeight(weightKg: Float) {
        viewModelScope.launch {
            bodyMeasurementDao.insert(BodyMeasurement(dateMs = System.currentTimeMillis(), weightKg = weightKg))
            backupScheduler.requestBackup()
        }
    }

    fun deleteBodyMeasurement(m: BodyMeasurement) {
        viewModelScope.launch { bodyMeasurementDao.delete(m) }
    }

    /**
     * E2: live deload state of the active program. When true, Home shows a visible deload banner so
     * the user can tell a stall/fatigue-triggered deload week is in effect (M2). Derived from the
     * active program's isDeloadActive flag.
     */
    val deloadActive: StateFlow<Boolean> =
        workoutRepository.observeActiveProgram()
            .map { it?.isDeloadActive ?: false }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** E2: active program name, shown on Home so the user knows which program is driving "today". */
    val activeProgramName: StateFlow<String> =
        workoutRepository.observeActiveProgram()
            .map { it?.name ?: "" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        viewModelScope.launch { workoutRepository.ensureExercisesPopulated() }
        viewModelScope.launch { AppDatabase.seedPresets(gymPresetDao) }
        // E2: guarantee an active program exists (fresh installs create the DB at v13 with no
        // migration, so nothing seeds a program) before the plan flows resolve.
        viewModelScope.launch { workoutRepository.ensureActiveProgramId() }
        viewModelScope.launch {
            workoutRepository.getPlannedForDay(thisMonday(), currentDayOfWeek())
                .collect { _todayPlan.value = it }
        }
        viewModelScope.launch {
            workoutRepository.getAllCompletedSessions().collect { sessions ->
                val summaries = sessions.take(5).map { s ->
                    val sets = workoutRepository.getSetsForSessionOnce(s.id).filter { !it.isWarmup }
                    val focus = sets.groupBy { it.muscleGroup }
                        .maxByOrNull { it.value.size }?.key ?: ""
                    SessionSummary(s, sets.size, sets.map { it.exerciseName }.toSet().size, focus)
                }
                _recentSessions.value = summaries
            }
        }
        viewModelScope.launch {
            _challenges.value = dailyChallengeManager.getTodayChallenges()
        }
    }

    fun startWorkout(onSessionStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val id = workoutRepository.startSession()
            onSessionStarted(id)
        }
    }
}
