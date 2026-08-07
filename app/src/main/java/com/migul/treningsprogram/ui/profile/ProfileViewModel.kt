package com.migul.treningsprogram.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.db.dao.AchievementDao
import com.migul.treningsprogram.data.db.dao.WorkoutSetDao
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.domain.DayBoundary
import com.migul.treningsprogram.domain.RecentPrs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userStats: UserStats? = null,
    /**
     * Brief 02: replaced `levelTitle` (the retired Rookie→Apex XP ladder). Null until the first
     * computation lands; an unrated profile is a non-null [com.migul.treningsprogram.domain.strength.StrengthProfile]
     * carrying its reason, not a null.
     */
    val strength: com.migul.treningsprogram.domain.strength.StrengthProfile? = null,
    // Stage-3 item 4: only the PRs earned in the rolling last-7-logical-days window.
    val recentPrs: List<RecentPrs.RecentPr> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
    // N5: lift goals — actives with live progress %, plus achieved history. Abandoned hidden.
    val goals: List<GoalRow> = emptyList()
)

/** N5: one Profile goal row — the goal plus its current progress (0..100; 100 for achieved). */
data class GoalRow(
    val goal: com.migul.treningsprogram.data.db.entity.LiftGoal,
    val progressPercent: Int
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val gamificationRepository: GamificationRepository,
    private val workoutSetDao: WorkoutSetDao,
    private val achievementDao: AchievementDao,
    private val goalRepository: com.migul.treningsprogram.data.repository.GoalRepository,
    private val strengthRepository: com.migul.treningsprogram.data.repository.StrengthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            gamificationRepository.userStats.collect { stats ->
                _state.update { it.copy(userStats = stats) }
                refreshStrength()
                // Recent PRs are derived from logged sets. Re-query them whenever stats
                // change (a completed workout always bumps UserStats) so returning to
                // Profile after a session shows fresh wins instead of the values captured
                // once at first construction.
                refreshRecentPrs()
            }
        }
        viewModelScope.launch {
            achievementDao.observeAll().collect { list ->
                _state.update { it.copy(achievements = list) }
            }
        }
        // Initial load so the PR section populates even before the first userStats emission.
        viewModelScope.launch { refreshRecentPrs() }
        viewModelScope.launch { refreshStrength() }
        // N5: goals list — actives first (with live progress), then achieved history.
        viewModelScope.launch {
            goalRepository.observeAll().collect { all ->
                val visible = all.filter {
                    it.status != com.migul.treningsprogram.data.db.entity.LiftGoal.STATUS_ABANDONED
                }
                val rows = visible
                    .sortedBy { it.status != com.migul.treningsprogram.data.db.entity.LiftGoal.STATUS_ACTIVE }
                    .map { goal ->
                        val pct = if (goal.status == com.migul.treningsprogram.data.db.entity.LiftGoal.STATUS_ACHIEVED) 100
                        else com.migul.treningsprogram.domain.GoalProgress.progressPercent(
                            goalRepository.currentBestFor(goal), goal.targetWeightKg
                        )
                        GoalRow(goal, pct)
                    }
                _state.update { it.copy(goals = rows) }
            }
        }
    }

    /**
     * Brief 02: recompute the strength rating. Public because a rating is relative to body weight,
     * so it can change with no workout involved — the fragment refreshes on resume so returning
     * from a weigh-in shows the new rating rather than the one captured at construction.
     */
    fun refreshStrength() {
        viewModelScope.launch {
            val profile = strengthRepository.currentProfile()
            _state.update { it.copy(strength = profile) }
        }
    }

    private suspend fun refreshRecentPrs() {
        // Fetch from a day before the logical window opens; RecentPrs treats pre-window
        // samples as baseline-extending only, so the generous cutoff is harmless.
        val fetchFromMs = System.currentTimeMillis() -
            (RecentPrs.WINDOW_DAYS + 1) * 24L * 60L * 60L * 1000L
        val windowSets = workoutSetDao.getWeightedWorkingSetsSince(fetchFromMs)
            .map { RecentPrs.SetSample(it.exerciseName, it.weightKg, it.dateMs) }
        val baseline = workoutSetDao.getMaxWeightsBefore(fetchFromMs)
            .associate { it.exerciseName to it.maxWeight }
        val prs = RecentPrs.compute(windowSets, baseline, DayBoundary.todayEpochDay())
        _state.update { it.copy(recentPrs = prs) }
    }
}
