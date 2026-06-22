package com.migul.treningsprogram.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.data.preferences.DailyChallengeManager
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.data.repository.thisMonday
import com.migul.treningsprogram.domain.model.DailyChallenge
import com.migul.treningsprogram.domain.model.WorkoutResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val gamificationRepository: GamificationRepository,
    private val dailyChallengeManager: DailyChallengeManager
) : ViewModel() {

    private val restTimerFallbackSeconds = 90

    private val _sessionId = MutableStateFlow<Long?>(null)
    private val _sessionStartMs = MutableStateFlow(0L)
    private val _dayOfWeek = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val sets: StateFlow<List<WorkoutSet>> = _sessionId
        .filterNotNull()
        .flatMapLatest { workoutRepository.getSetsForSession(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _guidedPlan = MutableStateFlow<List<PlannedExercise>>(emptyList())
    val guidedPlan: StateFlow<List<PlannedExercise>> = _guidedPlan.asStateFlow()

    // True once loadGuidedPlan() has finished — prevents empty-plan flash from triggering freestyle mode
    private val _planLoaded = MutableStateFlow(false)
    val planLoaded: StateFlow<Boolean> = _planLoaded.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    val currentExercise: StateFlow<PlannedExercise?> =
        combine(_guidedPlan, _currentIndex) { plan, idx ->
            plan.getOrNull(idx)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val setsForCurrentExercise: StateFlow<List<WorkoutSet>> =
        combine(sets, currentExercise) { allSets, exercise ->
            if (exercise == null) emptyList()
            else allSets.filter { it.exerciseName == exercise.exerciseName }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Per-exercise timer: resets automatically whenever currentIndex changes
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentExerciseElapsedMs: StateFlow<Long> = _currentIndex
        .flatMapLatest {
            flow {
                val start = System.currentTimeMillis()
                while (true) {
                    emit(System.currentTimeMillis() - start)
                    delay(1000)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val elapsedTimeMs: StateFlow<Long> = _sessionStartMs
        .flatMapLatest { startMs ->
            if (startMs == 0L) flowOf(0L)
            else flow {
                while (true) {
                    emit(System.currentTimeMillis() - startMs)
                    delay(1000)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _todayChallenges = MutableStateFlow<List<DailyChallenge>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val challengeProgress: StateFlow<String> = sets
        .combine(_todayChallenges) { currentSets, challenges ->
            if (challenges.isEmpty()) return@combine ""
            val workingSets = currentSets.filter { !it.isWarmup }
            val muscleGroups = workingSets.map { it.muscleGroup }.toSet()
            val exerciseNames = workingSets.map { it.exerciseName }.toSet()
            challenges.joinToString("   ") { ch ->
                val metNow = when (ch.id) {
                    "complete_workout" -> workingSets.isNotEmpty()
                    "sets_10"          -> workingSets.size >= 10
                    "sets_15"          -> workingSets.size >= 15
                    "sets_20"          -> workingSets.size >= 20
                    "chest_day"        -> "Chest" in muscleGroups
                    "back_day"         -> "Back" in muscleGroups
                    "leg_day"          -> "Legs" in muscleGroups
                    "arms_day"         -> "Arms" in muscleGroups
                    "core_day"         -> "Core" in muscleGroups
                    "exercises_3"      -> exerciseNames.size >= 3
                    "exercises_5"      -> exerciseNames.size >= 5
                    else               -> false
                }
                when {
                    ch.isCompleted || metNow -> "✅ ${ch.name}"
                    ch.id == "sets_10"     -> "⬜ ${ch.name} ${workingSets.size}/10"
                    ch.id == "sets_15"     -> "⬜ ${ch.name} ${workingSets.size}/15"
                    ch.id == "sets_20"     -> "⬜ ${ch.name} ${workingSets.size}/20"
                    ch.id == "exercises_3" -> "⬜ ${ch.name} ${exerciseNames.size}/3"
                    ch.id == "exercises_5" -> "⬜ ${ch.name} ${exerciseNames.size}/5"
                    else                   -> "⬜ ${ch.name}"
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _workoutResult = MutableStateFlow<WorkoutResult?>(null)
    val workoutResult: StateFlow<WorkoutResult?> = _workoutResult.asStateFlow()

    private val _sessionAbandoned = MutableStateFlow(false)
    val sessionAbandoned: StateFlow<Boolean> = _sessionAbandoned.asStateFlow()

    private val _savedWeights = mutableMapOf<String, Float>()
    private val _savedReps = mutableMapOf<String, Int>()

    fun saveCurrentExerciseValues(exerciseName: String, weight: Float, reps: Int) {
        _savedWeights[exerciseName] = weight
        _savedReps[exerciseName] = reps
    }

    fun getSavedWeight(exerciseName: String): Float? = _savedWeights[exerciseName]
    fun getSavedReps(exerciseName: String): Int? = _savedReps[exerciseName]

    fun loadSession(sessionId: Long, dayOfWeek: Int) {
        if (_sessionId.value == sessionId) return
        _sessionId.value = sessionId
        val day = if (dayOfWeek > 0) dayOfWeek else currentDayOfWeek()
        _dayOfWeek.value = day
        viewModelScope.launch {
            val session = workoutRepository.getActiveSession()
            _sessionStartMs.value = session?.dateMs ?: System.currentTimeMillis()
            _todayChallenges.value = dailyChallengeManager.getTodayChallenges()
            loadGuidedPlan(day)
        }
    }

    fun resumeSession(dayOfWeek: Int = -1) {
        if (_sessionId.value != null) return
        viewModelScope.launch {
            val id = workoutRepository.startSession()
            _sessionId.value = id
            val session = workoutRepository.getActiveSession()
            _sessionStartMs.value = session?.dateMs ?: System.currentTimeMillis()
            val day = if (dayOfWeek > 0) dayOfWeek else currentDayOfWeek()
            _dayOfWeek.value = day
            _todayChallenges.value = dailyChallengeManager.getTodayChallenges()
            loadGuidedPlan(day)
        }
    }

    private suspend fun loadGuidedPlan(dayOfWeek: Int) {
        val plan = if (dayOfWeek > 0)
            workoutRepository.getPlannedForDay(thisMonday(), dayOfWeek).first()
        else emptyList()
        val sorted = plan.sortedBy { it.orderInDay }
        _guidedPlan.value = sorted
        val sid = _sessionId.value ?: return
        val loggedNames = workoutRepository.getSetsForSessionOnce(sid).map { it.exerciseName }.toSet()
        val firstUnlogged = sorted.indexOfFirst { it.exerciseName !in loggedNames }
        _currentIndex.value = if (firstUnlogged >= 0) firstUnlogged else 0
        _planLoaded.value = true
    }

    fun nextExercise() {
        val plan = _guidedPlan.value
        if (_currentIndex.value < plan.size - 1) _currentIndex.value++
    }

    fun previousExercise() {
        if (_currentIndex.value > 0) _currentIndex.value--
    }

    fun skipExercise() { nextExercise() }

    val isLastExercise: Boolean get() = _currentIndex.value >= _guidedPlan.value.size - 1

    fun logSet(weight: Float, reps: Int, isWarmup: Boolean, rpeLabel: String) {
        val sid = _sessionId.value ?: return
        val exercise = currentExercise.value ?: return
        viewModelScope.launch {
            val existingSets = sets.value.filter { it.exerciseName == exercise.exerciseName }
            workoutRepository.addSet(
                WorkoutSet(
                    sessionId = sid,
                    exerciseName = exercise.exerciseName,
                    muscleGroup = AppDatabase.DEFAULT_EXERCISES
                        .find { it.name == exercise.exerciseName }?.muscleGroup ?: "",
                    setNumber = existingSets.size + 1,
                    reps = reps,
                    weightKg = weight,
                    isWarmup = isWarmup,
                    rpeLabel = rpeLabel
                )
            )
        }
    }

    fun logFreestyleSet(exerciseName: String, weight: Float, reps: Int, isWarmup: Boolean, rpeLabel: String) {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            val existingSets = sets.value.filter { it.exerciseName == exerciseName }
            val muscleGroup = AppDatabase.DEFAULT_EXERCISES.find { it.name == exerciseName }?.muscleGroup ?: ""
            workoutRepository.addSet(
                WorkoutSet(
                    sessionId = sid,
                    exerciseName = exerciseName,
                    muscleGroup = muscleGroup,
                    setNumber = existingSets.size + 1,
                    reps = reps,
                    weightKg = weight,
                    isWarmup = isWarmup,
                    rpeLabel = rpeLabel
                )
            )
        }
    }

    fun addSet(exerciseName: String, muscleGroup: String, reps: Int, weightKg: Float) {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            val existingSets = sets.value.filter { it.exerciseName == exerciseName }
            workoutRepository.addSet(WorkoutSet(
                sessionId = sid, exerciseName = exerciseName, muscleGroup = muscleGroup,
                setNumber = existingSets.size + 1, reps = reps, weightKg = weightKg
            ))
        }
    }

    fun deleteSet(set: WorkoutSet) { viewModelScope.launch { workoutRepository.deleteSet(set) } }

    fun completeWorkout() {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            if (sets.value.none { !it.isWarmup }) {
                workoutRepository.deleteSession(sid)
                _sessionAbandoned.value = true
                return@launch
            }
            val durationMs = System.currentTimeMillis() - _sessionStartMs.value
            workoutRepository.completeSession(sid, (durationMs / 60_000).toInt())
            // Mark planned exercises done so week progress bar is accurate
            val loggedNames = sets.value.filter { !it.isWarmup }.map { it.exerciseName }.toSet()
            val plannedToday = workoutRepository.getPlannedForDay(thisMonday(), _dayOfWeek.value).first()
            plannedToday.filter { it.exerciseName in loggedNames && !it.isLogged }
                .forEach { workoutRepository.updatePlannedExercise(it.copy(isLogged = true)) }
            val result = gamificationRepository.processWorkoutCompletion(sid)
            _workoutResult.value = result
        }
    }

    fun clearResult() { _workoutResult.value = null }

    suspend fun getLastSets(exerciseName: String): List<WorkoutSet> =
        workoutRepository.getLastSetsForExercise(exerciseName, _sessionId.value ?: -1)

    fun getRestSecondsForCurrentExercise(): Int =
        currentExercise.value?.recommendedRestSeconds ?: restTimerFallbackSeconds

    /**
     * Replaces the exercise at the current position with [newName] for this session only.
     * The DB plan is not modified — the swap is in-memory only.
     */
    fun swapCurrentExercise(exercise: PlannedExercise, newName: String) {
        val idx  = _currentIndex.value
        val plan = _guidedPlan.value.toMutableList()
        if (idx < plan.size) {
            plan[idx] = plan[idx].copy(exerciseName = newName)
            _guidedPlan.value = plan
        }
        // Clear saved values for the old exercise so the new one gets fresh defaults
        _savedWeights.remove(exercise.exerciseName)
        _savedReps.remove(exercise.exerciseName)
    }

    fun calculatePlates(targetKg: Float, barKg: Float = 20f): String {
        var remaining = (targetKg - barKg) / 2f
        if (remaining <= 0f) return "Bar only (${barKg.toInt()}kg)"
        val plates = listOf(20f, 15f, 10f, 5f, 2.5f, 1.25f)
        val parts = mutableListOf<String>()
        for (plate in plates) {
            val count = (remaining / plate).toInt()
            if (count > 0) {
                val label = if (plate == plate.toInt().toFloat()) "${plate.toInt()}kg" else "${plate}kg"
                parts.add("${count}x $label")
                remaining -= count * plate
            }
        }
        return if (parts.isEmpty()) "Bar only" else "Per side: ${parts.joinToString(" + ")}"
    }

    fun getExerciseNames(): List<String> = AppDatabase.DEFAULT_EXERCISES.map { it.name }
}
