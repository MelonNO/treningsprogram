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
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val backupScheduler: BackupScheduler,
    // N5: goal storage + reach/progress helpers for the Progress tab's goal management.
    private val goalRepository: com.migul.treningsprogram.data.repository.GoalRepository
) : ViewModel() {

    // ── N5: lift goals ────────────────────────────────────────────────────────────────────
    val goals: StateFlow<List<com.migul.treningsprogram.data.db.entity.LiftGoal>> =
        goalRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun goalCurrentBest(goal: com.migul.treningsprogram.data.db.entity.LiftGoal): Float? =
        goalRepository.currentBestFor(goal)

    fun saveGoal(name: String, targetKg: Float, isE1rm: Boolean, targetDateMs: Long) {
        viewModelScope.launch { goalRepository.createOrReplace(name, targetKg, isE1rm, targetDateMs) }
    }

    fun abandonGoal(goal: com.migul.treningsprogram.data.db.entity.LiftGoal) {
        viewModelScope.launch { goalRepository.abandon(goal) }
    }

    // ── Log tab ──────────────────────────────────────────────────────────
    // Completed real workouts only — drives the Stats tab totals (totalWorkouts / best streak),
    // so it must NOT include auto-logged rest/missed placeholders.
    val allSessions: StateFlow<List<WorkoutSession>> =
        workoutRepository.getAllCompletedSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")

    // Stage-3 item 12: the calendar start/end range replaces the Week/Month/3-Months chips.
    // null = All (default); deliberately in-memory only so every visit starts at All (A-12a).
    val logDateRange = MutableStateFlow<com.migul.treningsprogram.domain.DateRangeFilter.Range?>(null)

    // QoL item 04 — the History week-browser model: months → weeks → days derived from the
    // timeline (real workouts + REST/MISSED placeholders) joined with every completed session's
    // sets. Grouping/filter/PR math lives in the pure HistoryBrowser/HistoryPrFlags helpers
    // (unit-tested). Single sharing layer over the DAO flows (the F3 v1.24.1 lesson — never
    // stack two stateIn(WhileSubscribed) layers); null = still loading (skeleton), non-null =
    // render (possibly the empty state).
    val historyBrowser: StateFlow<com.migul.treningsprogram.domain.HistoryBrowser.Model?> =
        combine(
            workoutRepository.getHistoryTimeline(),
            workoutRepository.observeHistorySets(),
            searchQuery,
            logDateRange
        ) { sessions, sets, query, range ->
            com.migul.treningsprogram.domain.HistoryBrowser.build(
                sessions = sessions.map {
                    com.migul.treningsprogram.domain.HistoryBrowser.SessionRow(
                        it.id, it.dateMs, it.kind, it.durationMinutes
                    )
                },
                sets = sets.map {
                    com.migul.treningsprogram.domain.HistoryBrowser.SetRow(
                        it.sessionId, it.exerciseName, it.muscleGroup, it.setNumber,
                        it.reps, it.weightKg, it.isWarmup, it.loggedAtMs
                    )
                },
                query = query,
                range = range,
                todayEpochDay = com.migul.treningsprogram.domain.DayBoundary.todayEpochDay()
            ) as com.migul.treningsprogram.domain.HistoryBrowser.Model?
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Browser selection, kept here so it survives view re-creation. null week = month list.
    val browserWeekStart = MutableStateFlow<Long?>(null)
    val browserSelectedDay = MutableStateFlow<Long?>(null)

    fun openBrowserWeek(weekStartEpochDay: Long, selectedDay: Long) {
        browserWeekStart.value = weekStartEpochDay
        browserSelectedDay.value = selectedDay
    }

    fun selectBrowserDay(epochDay: Long) { browserSelectedDay.value = epochDay }

    // Week-view swipe: one-shot end-of-range events (+1 = tried past the newest visible week,
    // -1 = past the oldest). Consumed by the fragment's subtle bounce animation.
    private val _browserEdgeNudge = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val browserEdgeNudge: SharedFlow<Int> = _browserEdgeNudge.asSharedFlow()

    /**
     * Swipe navigation between browser weeks (direction -1 = older / right swipe, +1 = newer /
     * left swipe — the Program tab's mapping). Walks only the weeks visible under the active
     * search/range filter; at either end it emits the edge nudge and stays put. The destination
     * keeps the same weekday selected (future weekday → the week's default day). All the
     * resolution math is the pure HistoryBrowser helpers (unit-tested).
     */
    fun swipeBrowserWeek(direction: Int) {
        val model = historyBrowser.value ?: return
        val current = browserWeekStart.value ?: return
        val dest = com.migul.treningsprogram.domain.HistoryBrowser
            .adjacentWeekStart(model, current, direction)
            ?: run { _browserEdgeNudge.tryEmit(direction); return }
        val destWeek = model.weeksByStart[dest] ?: return
        val today = com.migul.treningsprogram.domain.DayBoundary.todayEpochDay()
        val selected = browserSelectedDay.value
        browserSelectedDay.value =
            if (selected != null)
                com.migul.treningsprogram.domain.HistoryBrowser.carriedDay(destWeek, selected, today)
            else
                com.migul.treningsprogram.domain.HistoryBrowser.defaultDay(destWeek, today)
        browserWeekStart.value = dest
    }

    /**
     * Pure read used by the drag-follow animation: the week a swipe in [direction] would land
     * on (same resolution as [swipeBrowserWeek]), or null at the range end. Lets the fragment
     * render the adjacent week into the peeking pager pane BEFORE the swipe commits, without
     * touching any browser state.
     */
    fun peekAdjacentBrowserWeek(direction: Int): com.migul.treningsprogram.domain.HistoryBrowser.Week? {
        val model = historyBrowser.value ?: return null
        val current = browserWeekStart.value ?: return null
        val dest = com.migul.treningsprogram.domain.HistoryBrowser
            .adjacentWeekStart(model, current, direction) ?: return null
        return model.weeksByStart[dest]
    }

    fun closeBrowserWeek() {
        browserWeekStart.value = null
        browserSelectedDay.value = null
    }

    suspend fun getSetsForSession(sessionId: Long): List<WorkoutSet> =
        workoutRepository.getSetsForSessionOnce(sessionId)

    /** B7: one-shot full session history (real workouts only get filtered downstream). */
    suspend fun allSessionsOnce(): List<WorkoutSession> = workoutRepository.getAllSessionsOnce()

    // ── Recap tab ────────────────────────────────────────────────────────────
    suspend fun getRecentSessions(count: Int = 30): List<WorkoutSession> =
        workoutRepository.getRecentSessions(count)

    suspend fun buildRecap(sessionId: Long): com.migul.treningsprogram.domain.model.SessionRecap? =
        workoutRepository.buildSessionRecap(sessionId)

    // Stage-3 item 3: the Recap overview getters (weekly volume/frequency points, muscle rows)
    // were removed with the overview section. RecapGraphs itself stays — WeekDelta and the Stats
    // tab still build on it.

    // ── Progress tab ──────────────────────────────────────────────────────
    val selectedExercise = MutableStateFlow("")

    // Stage-3 item 13: calendar start/end range replaces the 1M/3M/6M/All chips.
    // null = All (default); in-memory only, same non-persistence as item 12 (A-13a).
    val progressDateRange =
        MutableStateFlow<com.migul.treningsprogram.domain.DateRangeFilter.Range?>(null)

    /**
     * Exercise names for the Progress-tab picker, ordered most-trained-first (B03):
     * by distinct-session count descending, alpha tie-break. Ordering math is the pure
     * [com.migul.treningsprogram.domain.ExercisePickerSort] helper (unit-tested).
     */
    suspend fun getExerciseNames(): List<String> =
        com.migul.treningsprogram.domain.ExercisePickerSort.order(
            workoutRepository.getExerciseSessionCounts()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val strengthHistory: StateFlow<List<StrengthPoint>> =
        combine(
            selectedExercise.flatMapLatest { name ->
                if (name.isBlank()) flowOf(emptyList())
                else flow { emit(workoutRepository.getStrengthHistory(name)) }
            },
            progressDateRange
        ) { history, range ->
            history.filter {
                com.migul.treningsprogram.domain.DateRangeFilter.contains(range, it.dateMs)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Stage-3 item 1: per-session reps history INCLUDING bodyweight (0-kg) sets, windowed by the
     * same range as the strength chart. Drives the Progress reps chart for bodyweight exercises
     * (domain/RepsProgress decides whether it shows).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val repsHistory: StateFlow<List<com.migul.treningsprogram.domain.RepsProgress.Point>> =
        combine(
            selectedExercise.flatMapLatest { name ->
                if (name.isBlank()) flowOf(emptyList())
                else flow { emit(workoutRepository.getSessionRepsHistory(name)) }
            },
            progressDateRange
        ) { history, range ->
            history
                .filter { com.migul.treningsprogram.domain.DateRangeFilter.contains(range, it.dateMs) }
                .map {
                    com.migul.treningsprogram.domain.RepsProgress.Point(
                        it.dateMs, it.maxWeight, it.bestReps
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** Stats "this week" pulse: sets + sessions vs last week (Monday-based logical weeks). */
    suspend fun getWeekDelta(): com.migul.treningsprogram.domain.WeekDelta.Delta =
        com.migul.treningsprogram.domain.WeekDelta.compute(
            setDayEpochs = workoutRepository.getWorkingSetDayEpochs(),
            trainingDayEpochs = workoutRepository.getTrainingDayEpochs(),
            todayEpochDay = com.migul.treningsprogram.domain.DayBoundary.todayEpochDay(),
        )

    /**
     * QoL item 03: estimated calories for the current Monday-week — the sum of the same
     * per-session estimates the Recap shows (CalorieEstimator; derived on the fly, 0 = hide).
     */
    suspend fun getWeeklyCalories(): Int =
        workoutRepository.estimateWeeklyCalories(
            com.migul.treningsprogram.domain.DayBoundary.todayEpochDay()
        )

    /** F5: muscle × week set-count grid for the volume heatmap (last [weeks] weeks).
     *  Timestamps are shifted to LOGICAL millis first (Item 7 day boundary) so an 01:00
     *  session lands in the same week the rest of the app files it under. */
    suspend fun getVolumeHeatmap(weeks: Int = 8): com.migul.treningsprogram.domain.VolumeHeatmap.Grid =
        com.migul.treningsprogram.domain.VolumeHeatmap.build(
            workoutRepository.getMuscleSetDays(weeks).map {
                it.muscleGroup to com.migul.treningsprogram.domain.DayBoundary.toLogicalMillis(it.dateMs)
            },
            weeks = weeks,
            nowMs = com.migul.treningsprogram.domain.DayBoundary.toLogicalMillis(System.currentTimeMillis()),
        )
    suspend fun getRepRanges(): List<RepRange> = workoutRepository.getRepRangeDistribution()
    suspend fun getTrainingDays(): List<Long> = workoutRepository.getTrainingDayEpochs()
    suspend fun getTotalSets(): Int = workoutRepository.getTotalSets()
    suspend fun getTotalVolume(): Float = workoutRepository.getTotalVolumeKg()

    /**
     * Stage-3 item 11: the session a tapped heatmap cell (muscle × week) should open — the most
     * recent session that week with working sets for that muscle (HeatmapDrill, A-11a). Uses the
     * same LOGICAL-millis shift as the heatmap build so the drill target matches the drawn cell.
     */
    suspend fun resolveHeatmapSession(muscle: String, weekStartMs: Long, weeks: Int = 8): Long? =
        com.migul.treningsprogram.domain.HeatmapDrill.resolve(
            workoutRepository.getMuscleSessionDays(weeks).map {
                com.migul.treningsprogram.domain.HeatmapDrill.Row(
                    it.sessionId,
                    com.migul.treningsprogram.domain.DayBoundary.toLogicalMillis(it.dateMs),
                    it.muscleGroup
                )
            },
            muscle, weekStartMs
        )

    // Stage-3 item 15: the CSV export (exportCsv + the Stats-tab share button) was removed —
    // Settings → Backup & Data's JSON export/import is the data-portability path.
}
