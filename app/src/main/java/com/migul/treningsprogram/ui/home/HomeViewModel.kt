package com.migul.treningsprogram.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.preferences.DailyChallengeManager
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.data.repository.thisMonday
import com.migul.treningsprogram.domain.model.DailyChallenge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val gamificationRepository: GamificationRepository,
    private val dailyChallengeManager: DailyChallengeManager,
    private val gymPresetDao: GymPresetDao
) : ViewModel() {

    val userStats: StateFlow<UserStats?> = gamificationRepository.userStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeSession: StateFlow<WorkoutSession?> = workoutRepository.allSessions
        .map { it.firstOrNull { s -> !s.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayCompleted: StateFlow<Boolean> = workoutRepository.getAllCompletedSessions()
        .map { sessions ->
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            sessions.any { it.dateMs >= todayStart }
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

    init {
        viewModelScope.launch { workoutRepository.ensureExercisesPopulated() }
        viewModelScope.launch { AppDatabase.seedPresets(gymPresetDao) }
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
