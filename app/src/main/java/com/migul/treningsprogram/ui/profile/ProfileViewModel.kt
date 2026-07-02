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
    val levelTitle: String = "Rookie",
    // Stage-3 item 4: only the PRs earned in the rolling last-7-logical-days window.
    val recentPrs: List<RecentPrs.RecentPr> = emptyList(),
    val achievements: List<Achievement> = emptyList()
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val gamificationRepository: GamificationRepository,
    private val workoutSetDao: WorkoutSetDao,
    private val achievementDao: AchievementDao
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            gamificationRepository.userStats.collect { stats ->
                _state.update {
                    it.copy(
                        userStats = stats,
                        levelTitle = GamificationRepository.levelTitle(stats?.level ?: 1)
                    )
                }
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
