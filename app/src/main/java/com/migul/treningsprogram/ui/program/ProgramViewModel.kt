package com.migul.treningsprogram.ui.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.Program
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.friendlyAiErrorMessage
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
        // E2: ensure an active program exists so the switcher + plan flows have a target.
        viewModelScope.launch { workoutRepository.ensureActiveProgramId() }
    }

    // ── E2: program switcher state ───────────────────────────────────────────────────────────────

    val programs: StateFlow<List<Program>> = workoutRepository.observePrograms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProgram: StateFlow<Program?> = workoutRepository.observeActiveProgram()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** E2: true while the active program is in a stall/fatigue-triggered deload week (M2). */
    val deloadActive: StateFlow<Boolean> = activeProgram.map { it?.isDeloadActive ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun switchProgram(programId: Long) {
        viewModelScope.launch { workoutRepository.switchActiveProgram(programId) }
    }

    /** Save the current week's plan under a new name and switch to that program. */
    fun saveCurrentAsProgram(name: String) {
        viewModelScope.launch { workoutRepository.saveCurrentAsProgram(name) }
    }

    fun renameActiveProgram(name: String) {
        val id = activeProgram.value?.id ?: return
        viewModelScope.launch { workoutRepository.renameProgram(id, name) }
    }

    fun deleteProgram(programId: Long) {
        viewModelScope.launch { workoutRepository.deleteProgram(programId) }
    }

    /** Toggle whether the active program is a periodized mesocycle block of [weeks] weeks. */
    fun setMesocycle(weeks: Int) {
        val p = activeProgram.value ?: return
        viewModelScope.launch {
            workoutRepository.updateProgram(
                p.copy(
                    mesocycleWeeks = weeks,
                    blockStartWeek = if (weeks > 0 && p.blockStartWeek == 0L) thisMonday() else p.blockStartWeek
                )
            )
        }
    }

    /** Assumption N: toggle whether automatic weekly AI re-adaptation is frozen for this program. */
    fun setFrozen(frozen: Boolean) {
        val p = activeProgram.value ?: return
        viewModelScope.launch { workoutRepository.updateProgram(p.copy(isFrozen = frozen)) }
    }

    val weekPlan: StateFlow<List<PlannedExercise>> =
        workoutRepository.getPlannedForWeek(thisMonday())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // B2: the current week's "why did the program change?" rationale. It is stamped onto every
    // row of the week, so the first non-blank row carries it. Blank for old plans (pre-feature)
    // and when the model returned none → the Program tab hides the card (neutral state).
    val weekRationale: StateFlow<String> =
        weekPlan.map { plan -> plan.firstOrNull { it.rationale.isNotBlank() }?.rationale ?: "" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _selectedDay = MutableStateFlow(currentDayOfWeek())
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    val selectedDayExercises: StateFlow<List<PlannedExercise>> =
        combine(weekPlan, selectedDay) { plan, day -> plan.filter { it.dayOfWeek == day } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Progress in workout-days: (days with ≥1 logged exercise) / (days with ≥1 planned exercise)
    val weekProgress: StateFlow<Pair<Int, Int>> =
        weekPlan.map { plan ->
            val byDay = plan.groupBy { it.dayOfWeek }
            val workoutDays = byDay.values.filter { it.isNotEmpty() }
            val doneDays = workoutDays.count { exercises -> exercises.any { it.isLogged } }
            doneDays to workoutDays.size
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

    // ── E1: manual program editing (edit / delete / add / reorder a day's exercises) ────────────────
    // All edits go through the program-scoped repository so they persist and reflect everywhere the
    // plan is shown (Program tab, Home today-view, guided logging) and survive app restart. It is
    // ACCEPTED that the next regeneration replaces these manual edits.

    /**
     * Edit a planned exercise's TARGET (sets/reps/weight/notes) in place. This is an edit of the
     * plan target only; it intentionally does NOT touch isLogged/actuals — unlike swapExercise (which
     * replaces the exercise entirely), changing the target of the SAME exercise should not silently
     * un-log an already-logged set. Mirrors the in-place updatePlannedExercise pattern of logExercise.
     */
    fun editExercise(exercise: PlannedExercise, sets: Int, reps: String, weight: Float, notes: String) {
        viewModelScope.launch {
            workoutRepository.editPlannedExerciseFields(
                exercise.copy(sets = sets, targetReps = reps, targetWeightKg = weight, notes = notes)
            )
        }
    }

    /**
     * Delete a planned exercise from its day, re-indexing the remaining rows by list position.
     * Routed through the repository's atomic editDayPlan: the transform runs against the day's
     * freshly-persisted rows, so rapid delete/reorder/add in succession can't clobber one another.
     */
    fun deleteExercise(exercise: PlannedExercise) {
        viewModelScope.launch {
            workoutRepository.editDayPlan(thisMonday(), exercise.dayOfWeek) { current ->
                com.migul.treningsprogram.domain.DayPlanEditor.remove(current, exercise)
            }
        }
    }

    /** Add a new exercise to the end of [day]'s plan, re-indexing so orderInDay = list position. */
    fun addExercise(day: Int, name: String, sets: Int, reps: String, weight: Float, notes: String) {
        viewModelScope.launch {
            workoutRepository.editDayPlan(thisMonday(), day) { current ->
                val newRow = PlannedExercise(
                    weekStart = thisMonday(),
                    dayOfWeek = day,
                    orderInDay = current.size,
                    exerciseName = name,
                    sets = sets,
                    targetReps = reps,
                    targetWeightKg = weight,
                    notes = notes
                )
                com.migul.treningsprogram.domain.DayPlanEditor.add(current, newRow)
            }
        }
    }

    /** Move an exercise up or down within its day, re-indexing orderInDay by new list position. */
    fun moveExercise(exercise: PlannedExercise, up: Boolean) {
        viewModelScope.launch {
            workoutRepository.editDayPlan(thisMonday(), exercise.dayOfWeek) { current ->
                com.migul.treningsprogram.domain.DayPlanEditor.move(current, exercise, up)
            }
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
                injurySeverity = prefsManager.injurySeverity,
                priorityMuscles = prefsManager.priorityMuscles,
                dislikedExercises = prefsManager.dislikedExercises,
                muscleFocus = muscleFocus,
                onProgress = { _dayGenerationStatus.value = it }
            ).onSuccess { exercises ->
                workoutRepository.saveDayPlan(weekStart, dayOfWeek, exercises)
                _dayGenerationStatus.value = ""
            }.onFailure { e ->
                _dayGenerationError.value = friendlyAiErrorMessage(e)
                _dayGenerationStatus.value = ""
            }
            _isDayGenerating.value = false
        }
    }

    /**
     * E2: regenerate the ENTIRE active program for this week through the deload-aware generation
     * path (L1 + M2). This is the reachable on-demand trigger: it computes the stall-triggered
     * deload decision (reusing B3's StallDetector via the repository), conveys the mesocycle /
     * deload context to the model, replaces this week's plan, and persists the deload flag so the
     * Home/Program deload indicators update. Mirrors MainActivity's weekly auto-generation.
     */
    fun regenerateFullProgram(equipment: List<String>, equipmentNotes: String) {
        if (prefsManager.apiKey.isBlank()) {
            _dayGenerationError.value = "Set your API key in Profile → Settings first."
            return
        }
        viewModelScope.launch {
            _isDayGenerating.value = true
            _dayGenerationError.value = null
            val monday = thisMonday()
            val active = workoutRepository.ensureActiveProgramId().let {
                workoutRepository.getActiveProgramOnce()
            }
            val stalledLifts = workoutRepository.computeStalledLifts()
            // Deload is a once-per-WEEK state machine: a deload week is exactly one week, and the
            // NEXT week clears it (nextDeloadState's "if currentlyDeloading → exit"). That EXIT is
            // meant to fire on a week TRANSITION, not on a re-generation of the same week. A manual
            // "Regenerate program now" tapped again INSIDE an active deload week was re-running the
            // exit branch and silently dropping the deload mid-week. Guard only that exit: when we
            // are already deloading AND merely replacing this same week's existing plan, KEEP the
            // deload. Entering a deload (stalls newly reached) still works normally, and the auto-gen
            // path (which only ever runs on an empty/new week) is unaffected.
            val replacingCurrentWeek =
                workoutRepository.getActiveProgramPlanForWeek(monday).isNotEmpty()
            val isDeload = com.migul.treningsprogram.domain.DeloadPolicy.nextDeloadStateForRegen(
                currentlyDeloading = active?.isDeloadActive ?: false,
                stalledCount = stalledLifts.size,
                replacingCurrentWeek = replacingCurrentWeek
            )
            val mesocycle = active?.let { p ->
                com.migul.treningsprogram.data.repository.MesocycleContext(
                    mesocycleWeeks = p.mesocycleWeeks,
                    weekInBlock = workoutRepository.weekInBlock(p, monday),
                    isDeload = isDeload,
                    stalledLifts = stalledLifts
                )
            } ?: com.migul.treningsprogram.data.repository.MesocycleContext(
                isDeload = isDeload, stalledLifts = stalledLifts
            )

            aiRepository.generateAdaptedProgram(
                daysPerWeek = prefsManager.daysPerWeek,
                goal = prefsManager.fitnessGoal,
                experience = prefsManager.experienceLevel,
                sessionDurationMinutes = prefsManager.sessionDurationMinutes,
                equipment = equipment,
                equipmentNotes = equipmentNotes,
                separateCardioDays = prefsManager.separateCardioDays,
                injuries = prefsManager.injuries,
                injurySeverity = prefsManager.injurySeverity,
                priorityMuscles = prefsManager.priorityMuscles,
                dislikedExercises = prefsManager.dislikedExercises,
                onboardingContext = prefsManager.onboardingContext,
                mesocycle = mesocycle,
                onProgress = { _dayGenerationStatus.value = it }
            ).onSuccess { generationResult ->
                workoutRepository.savePlan(
                    monday,
                    generationResult.exercises.map { it.copy(rationale = generationResult.rationale) }
                )
                workoutRepository.setActiveDeload(isDeload)
                _dayGenerationStatus.value = ""
            }.onFailure { e ->
                _dayGenerationError.value = if (e is IllegalStateException && e.message?.startsWith("Program rejected") == true)
                    e.message ?: "Program rejected after all attempts"
                else
                    friendlyAiErrorMessage(e)
                _dayGenerationStatus.value = ""
            }
            _isDayGenerating.value = false
        }
    }
}
