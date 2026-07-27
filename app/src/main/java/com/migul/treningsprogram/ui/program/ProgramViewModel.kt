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
    private val gson: Gson,
    generationState: com.migul.treningsprogram.domain.GenerationState,
    private val generationRunner: com.migul.treningsprogram.domain.GenerationRunner
) : ViewModel() {

    // Item 8: app-scoped full-generation signal (a generation launched from a Settings screen). The
    // Program tab observes these to show its own generating animation; single-day regen keeps its own
    // isDayGenerating/dayGenerationStatus (untouched).
    val fullGenerating: StateFlow<Boolean> = generationState.fullGenerating
    val fullGenerationStatus: StateFlow<String> = generationState.status

    // Item 4 (rest-UX 2026-07): the Program screen's "~Xm" labels use the SAME estimator as the
    // generation gate, so in manual rest mode they must count the user's category times too —
    // otherwise the displayed and enforced numbers would disagree. Null = AI mode.
    val manualRestTimes: com.migul.treningsprogram.domain.ManualRestTimes?
        get() = prefsManager.manualRestTimes

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

    // The Program tab is CURRENT-WEEK-ONLY (past weeks live exclusively in History's week
    // browser — the swipe-to-past-weeks paging was removed with its read-only view state).
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

    // Progress in workout-days: (days with ≥1 logged exercise) / (days with ≥1 planned exercise).
    // R4: the math lives in WeekCompletion so the Perfect Week award provably uses THE SAME rule.
    val weekProgress: StateFlow<Pair<Int, Int>> =
        weekPlan.map { plan -> com.migul.treningsprogram.domain.WeekCompletion.dayCounts(plan) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0)

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

    /** Item 02: the "exercises to avoid" list of [presetId] — follows whichever gym a regen targets. */
    fun getAvoidForPreset(presetId: Long): List<String> {
        if (presetId == -1L) return emptyList()
        return com.migul.treningsprogram.domain.GymExclusions
            .parse(presets.value.find { it.id == presetId }?.avoidExercisesJson)
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
        // Item 05: app scope — a triggered auto-rebalance (an AI generation) must survive
        // backgrounding; the FGS is held only inside runRebalance, so plain edits stay silent.
        generationRunner.scope.launch {
            val beforeNames = currentDayNames(exercise.dayOfWeek)
            workoutRepository.updatePlannedExercise(
                exercise.copy(exerciseName = newName, isLogged = false, actualWeightKg = 0f, actualReps = "", actualSets = 0)
            )
            maybeRebalanceAfterManualEdit(exercise.dayOfWeek, beforeNames)
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
        generationRunner.scope.launch {   // item 05: possible auto-rebalance tail must survive
            val beforeNames = currentDayNames(exercise.dayOfWeek)
            workoutRepository.editDayPlan(thisMonday(), exercise.dayOfWeek) { current ->
                com.migul.treningsprogram.domain.DayPlanEditor.remove(current, exercise)
            }
            maybeRebalanceAfterManualEdit(exercise.dayOfWeek, beforeNames)
        }
    }

    /** Add a new exercise to the end of [day]'s plan, re-indexing so orderInDay = list position. */
    fun addExercise(day: Int, name: String, sets: Int, reps: String, weight: Float, notes: String) {
        generationRunner.scope.launch {   // item 05: possible auto-rebalance tail must survive
            val beforeNames = currentDayNames(day)
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
            maybeRebalanceAfterManualEdit(day, beforeNames)
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

    fun regenerateDay(
        dayOfWeek: Int, equipment: List<String>, equipmentNotes: String, muscleFocus: String = "",
        // Item 02: the exclusions of the gym this regen TARGETS (the swap dialog's picked preset).
        avoidExercises: List<String> = emptyList()
    ) {
        if (prefsManager.apiKey.isBlank()) {
            _dayGenerationError.value = "Set your API key in Profile → Settings first."
            return
        }
        // Item 05: app-scoped + foreground-service-protected — the day regen (and any chained
        // auto-rebalance) completes and saves even if the user backgrounds/locks immediately.
        generationRunner.launch {
            _isDayGenerating.value = true
            _dayGenerationError.value = null
            val weekStart = thisMonday()
            val currentPlan = weekPlan.value
            // P1: snapshot the day's focus BEFORE the regen so we can tell if it genuinely changed.
            val beforeNames = currentDayNames(dayOfWeek)
            var rebalanced = false

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
                gymAvoidExercises = avoidExercises,
                muscleFocus = muscleFocus,
                onProgress = { _dayGenerationStatus.value = it }
            ).onSuccess { exercises ->
                workoutRepository.saveDayPlan(weekStart, dayOfWeek, exercises)
                _dayGenerationStatus.value = ""
                // P1: if auto-rebalance is on and this regen CHANGED the day's primary muscle focus,
                // rebalance the rest of the week around the (now locked) changed day. runRebalance owns
                // _isDayGenerating from here so the progress animation stays up with no flicker.
                if (prefsManager.autoRebalanceEnabled &&
                    com.migul.treningsprogram.domain.MuscleFocus.changed(beforeNames, exercises.map { it.exerciseName })
                ) {
                    val (eq, notes, avoid) = resolveEquipment()
                    runRebalance(setOf(dayOfWeek), eq, notes, showNothingError = false, avoidExercises = avoid)
                    rebalanced = true
                }
            }.onFailure { e ->
                _dayGenerationError.value = friendlyAiErrorMessage(e)
                _dayGenerationStatus.value = ""
            }
            if (!rebalanced) _isDayGenerating.value = false
        }
    }

    /** B08: effective rest-day/count selection for generation (rest-day mode vs count fallback). */
    private fun effectiveSelection() =
        com.migul.treningsprogram.domain.TrainingDaySelection.effective(
            prefsManager.restDaysCsv, prefsManager.daysPerWeek
        )

    // ── P1: auto-rebalance toggle ────────────────────────────────────────────────────────────────
    val autoRebalanceEnabled: Boolean get() = prefsManager.autoRebalanceEnabled
    fun setAutoRebalanceEnabled(enabled: Boolean) { prefsManager.autoRebalanceEnabled = enabled }

    /** The current names (in order) of a day's plan from the live week, for focus-change detection. */
    private fun currentDayNames(day: Int): List<String> =
        weekPlan.value.filter { it.dayOfWeek == day }.sortedBy { it.orderInDay }.map { it.exerciseName }

    /** Equipment + notes + avoid-list for the user's currently-selected gym preset (for internally-triggered regens). */
    private suspend fun resolveEquipment(): Triple<List<String>, String, List<String>> {
        val presetId = prefsManager.selectedGymPresetId
        if (presetId == -1L) return Triple(emptyList(), "", emptyList())
        val preset = gymPresetDao.getById(presetId) ?: return Triple(emptyList(), "", emptyList())
        val equip = runCatching {
            gson.fromJson<List<String>>(preset.equipmentJson, object : TypeToken<List<String>>() {}.type)
        }.getOrElse { emptyList() }
        // Item 02: the gym's exclusion list travels with its equipment on every internal regen.
        return Triple(equip ?: emptyList(), preset.notes,
            com.migul.treningsprogram.domain.GymExclusions.parse(preset.avoidExercisesJson))
    }

    /**
     * P1: after a MANUAL structural edit (add/delete/swap), rebalance the week iff auto-rebalance is on
     * AND the edit genuinely changed the day's PRIMARY MUSCLE FOCUS. Field-only edits (sets/reps/notes)
     * and reorders never call this. A day the user has already logged is left alone (preserve-logged).
     */
    private suspend fun maybeRebalanceAfterManualEdit(day: Int, beforeNames: List<String>) {
        if (!prefsManager.autoRebalanceEnabled) return
        val monday = thisMonday()
        val afterRows = workoutRepository.getActiveProgramPlanForWeek(monday).filter { it.dayOfWeek == day }
        if (afterRows.any { it.isLogged }) return  // never auto-rebalance off a logged day
        val afterNames = afterRows.sortedBy { it.orderInDay }.map { it.exerciseName }
        if (com.migul.treningsprogram.domain.MuscleFocus.changed(beforeNames, afterNames)) {
            val (eq, notes, avoid) = resolveEquipment()
            runRebalance(setOf(day), eq, notes, showNothingError = false, avoidExercises = avoid)
        }
    }

    /**
     * P1/P2: shared week-rebalance — regenerate the current week's NON-locked days around the
     * locked/logged days, using the normal generation animation. [extraLockedDays] are days to LOCK
     * beyond the already-logged days (P1 locks the changed day; P2 passes none since today is logged
     * after the move). Reuses the B09 preserve-logged path, so logged days/sets are never touched.
     */
    private suspend fun runRebalance(
        extraLockedDays: Set<Int>,
        equipment: List<String>,
        equipmentNotes: String,
        showNothingError: Boolean,
        // Item 02: the target gym's exclusions (defaults to none for legacy callers).
        avoidExercises: List<String> = emptyList()
    ) {
        if (prefsManager.apiKey.isBlank()) {
            if (showNothingError) _dayGenerationError.value = "Set your API key in Profile → Settings first."
            return
        }
        _isDayGenerating.value = true
        _dayGenerationError.value = null
        val monday = thisMonday()
        val currentPlan = workoutRepository.getActiveProgramPlanForWeek(monday)
        val loggedDays = com.migul.treningsprogram.domain.RegeneratePlanner.loggedDays(currentPlan)
        val preserveDays = loggedDays + extraLockedDays
        val lockedExercises = currentPlan.filter { it.dayOfWeek in preserveDays }
        val eff = effectiveSelection()

        if (com.migul.treningsprogram.domain.RegeneratePlanner.nothingToRegenerate(preserveDays.size, eff.daysPerWeek)) {
            if (showNothingError) _dayGenerationError.value =
                "All ${eff.daysPerWeek} of this week's training days are already set — nothing to rebalance."
            _isDayGenerating.value = false
            return
        }

        // Item 05: hold the foreground service across the actual generation + save (the cheap
        // guards above stay outside so a no-op rebalance never flashes the service notification).
        // All runRebalance callers already run on the app-scoped GenerationRunner.
        generationRunner.withProtection {
            val mesocycle = workoutRepository.buildRegenMesocycle(monday)
            aiRepository.generateAdaptedProgram(
                daysPerWeek = eff.daysPerWeek,
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
                gymAvoidExercises = avoidExercises,
                onboardingContext = prefsManager.onboardingContext,
                mesocycle = mesocycle,
                restDays = eff.restDays,
                lockedExercises = lockedExercises,
                onProgress = { _dayGenerationStatus.value = it }
            ).onSuccess { generationResult ->
                workoutRepository.savePlanPreservingLoggedDays(
                    monday,
                    generationResult.exercises.map { it.copy(rationale = generationResult.rationale) },
                    preserveDays
                )
                workoutRepository.setActiveDeload(mesocycle.isDeload)
                _dayGenerationStatus.value = ""
            }.onFailure { e ->
                _dayGenerationError.value = if (e is IllegalStateException && e.message?.startsWith("Program rejected") == true)
                    e.message ?: "Program rejected after all attempts"
                else
                    friendlyAiErrorMessage(e)
                _dayGenerationStatus.value = ""
            }
        }
        _isDayGenerating.value = false
    }

    /**
     * B09: the DEFAULT Program-tab "regenerate" action. Preserves every day that has ≥1 logged
     * exercise (kept exactly as-is, including its logged rows) and regenerates every other day,
     * feeding the already-trained days to the AI as fixed context so it rebalances around them.
     * Never deletes logged sets/history (only planned_exercises for non-logged days are replaced).
     */
    fun regeneratePreservingLoggedDays(equipment: List<String>, equipmentNotes: String, avoidExercises: List<String> = emptyList()) {
        generationRunner.scope.launch {   // item 05: survives backgrounding; FGS held inside runRebalance
            runRebalance(extraLockedDays = emptySet(), equipment = equipment, equipmentNotes = equipmentNotes, showNothingError = true, avoidExercises = avoidExercises)
        }
    }

    /**
     * P2: after a "do another day's workout today" move is COMMITTED on workout completion, rebalance
     * the week — ALWAYS, regardless of the P1 auto-rebalance toggle. Today is already a logged day
     * (the move marked it), so the vacated source day and the other non-logged days regenerate around
     * the logged days. Triggered when the user returns to the Program tab post-completion.
     */
    fun rebalanceAfterDayMove() {
        generationRunner.scope.launch {   // item 05: survives backgrounding; FGS held inside runRebalance
            val (eq, notes, avoid) = resolveEquipment()
            runRebalance(extraLockedDays = emptySet(), equipment = eq, equipmentNotes = notes, showNothingError = false, avoidExercises = avoid)
        }
    }

}
