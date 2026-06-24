package com.migul.treningsprogram.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.backup.BackupScheduler
import com.migul.treningsprogram.data.db.dao.*
import com.migul.treningsprogram.data.db.entity.*
import com.migul.treningsprogram.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val backupScheduler: BackupScheduler
) : ViewModel() {

    // ── Log tab ──────────────────────────────────────────────────────────
    val allSessions: StateFlow<List<WorkoutSession>> =
        workoutRepository.getAllCompletedSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")
    val dateFilter = MutableStateFlow(DateFilter.ALL)

    enum class DateFilter { ALL, WEEK, MONTH, THREE_MONTHS }

    val filteredSessions: StateFlow<List<WorkoutSession>> =
        combine(allSessions, searchQuery, dateFilter) { sessions, query, filter ->
            val now = System.currentTimeMillis()
            val cutoff = when (filter) {
                DateFilter.WEEK -> now - 7 * 86_400_000L
                DateFilter.MONTH -> now - 30 * 86_400_000L
                DateFilter.THREE_MONTHS -> now - 90 * 86_400_000L
                DateFilter.ALL -> 0L
            }
            val filtered = sessions.filter { it.dateMs >= cutoff }
            if (query.isBlank()) {
                filtered
            } else {
                val fmt = SimpleDateFormat("dd MMM yyyy EEE", Locale.getDefault())
                filtered.filter { s ->
                    fmt.format(Date(s.dateMs)).contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getSetsForSession(sessionId: Long): List<WorkoutSet> =
        workoutRepository.getSetsForSessionOnce(sessionId)

    fun updateSessionDate(session: WorkoutSession, newDateMs: Long) {
        viewModelScope.launch { workoutRepository.updateSession(session.copy(dateMs = newDateMs)) }
    }

    fun deleteSet(set: WorkoutSet) {
        viewModelScope.launch { workoutRepository.deleteSet(set) }
    }

    // ── Recap tab ────────────────────────────────────────────────────────────
    suspend fun getRecentSessions(count: Int = 30): List<WorkoutSession> =
        workoutRepository.getRecentSessions(count)

    suspend fun buildRecap(sessionId: Long): com.migul.treningsprogram.domain.model.SessionRecap? =
        workoutRepository.buildSessionRecap(sessionId)

    // ── Progress tab ──────────────────────────────────────────────────────
    val selectedExercise = MutableStateFlow("")
    val timeWindowMonths = MutableStateFlow(0)

    suspend fun getExerciseNames(): List<String> = workoutRepository.getDistinctExerciseNames()

    @OptIn(ExperimentalCoroutinesApi::class)
    val strengthHistory: StateFlow<List<StrengthPoint>> =
        combine(
            selectedExercise.flatMapLatest { name ->
                if (name.isBlank()) flowOf(emptyList())
                else flow { emit(workoutRepository.getStrengthHistory(name)) }
            },
            timeWindowMonths
        ) { history, months ->
            if (months == 0) history
            else {
                val cutoff = System.currentTimeMillis() - months * 30 * 86_400_000L
                history.filter { it.dateMs >= cutoff }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prs: StateFlow<List<ExercisePrWithDate>> =
        workoutRepository.observePRsWithDate()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Stalled lifts surfaced on the Progress tab (feature B3). A lift is stalled when its estimated
     * 1RM has not improved across the last [com.migul.treningsprogram.domain.StallDetector.STALL_WINDOW]
     * consecutive sessions (double-progression-aware — reps climbing at the same load does NOT flag).
     * Each entry is (exerciseName, rule-based suggestion). Recomputed whenever completed sessions
     * change, so logging a heavier/higher-rep set clears the alert.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val stalledLifts: StateFlow<List<Pair<String, String>>> =
        allSessions.mapLatest {
            workoutRepository.getDistinctExerciseNames()
                .filter { name ->
                    com.migul.treningsprogram.domain.StallDetector.isStalled(
                        workoutRepository.getStrengthHistory(name)
                    )
                }
                .map { name ->
                    name to com.migul.treningsprogram.domain.StallDetector.suggestionFor(name)
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bodyMeasurements: StateFlow<List<BodyMeasurement>> =
        bodyMeasurementDao.getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addBodyWeight(weightKg: Float) {
        viewModelScope.launch {
            bodyMeasurementDao.insert(BodyMeasurement(dateMs = System.currentTimeMillis(), weightKg = weightKg))
            backupScheduler.requestBackup()
        }
    }

    fun deleteBodyMeasurement(m: BodyMeasurement) {
        viewModelScope.launch { bodyMeasurementDao.delete(m) }
    }

    // ── Stats tab ──────────────────────────────────────────────────────────
    suspend fun getMuscleVolume(): List<MuscleVolume> = workoutRepository.getMuscleGroupVolume()
    suspend fun getRepRanges(): List<RepRange> = workoutRepository.getRepRangeDistribution()
    suspend fun getTrainingDays(): List<Long> = workoutRepository.getTrainingDayEpochs()
    suspend fun getTotalSets(): Int = workoutRepository.getTotalSets()
    suspend fun getTotalVolume(): Float = workoutRepository.getTotalVolumeKg()
    fun exportCsv(sessions: List<WorkoutSession>, callback: (String) -> Unit) {
        viewModelScope.launch {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sb = StringBuilder("Date,Duration(min),Exercise,Set,Reps,Weight(kg)\n")
            sessions.forEach { session ->
                val date = fmt.format(Date(session.dateMs))
                val dur = session.durationMinutes
                val sets = workoutRepository.getSetsForSessionOnce(session.id)
                sets.forEach { set ->
                    sb.append("$date,$dur,${set.exerciseName},${set.setNumber},${set.reps},${set.weightKg}\n")
                }
            }
            callback(sb.toString())
        }
    }
}
