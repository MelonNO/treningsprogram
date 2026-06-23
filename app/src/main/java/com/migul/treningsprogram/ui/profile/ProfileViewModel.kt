package com.migul.treningsprogram.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.db.dao.AchievementDao
import com.migul.treningsprogram.data.db.dao.ExercisePr
import com.migul.treningsprogram.data.db.dao.WorkoutSetDao
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.repository.GamificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userStats: UserStats? = null,
    val levelTitle: String = "Rookie",
    val totalVolumeKg: Float = 0f,
    val totalSets: Int = 0,
    val topPrs: List<ExercisePr> = emptyList(),
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
                // Volume/sets/top-PR tiles are derived from logged sets. Re-query them
                // whenever stats change (a completed workout always bumps UserStats) so
                // returning to Profile after a session shows fresh totals instead of the
                // values captured once at first construction.
                refreshSetTotals()
            }
        }
        viewModelScope.launch {
            achievementDao.observeAll().collect { list ->
                _state.update { it.copy(achievements = list) }
            }
        }
        // Initial load so the tiles populate even before the first userStats emission.
        viewModelScope.launch { refreshSetTotals() }
    }

    private suspend fun refreshSetTotals() {
        val vol = workoutSetDao.getTotalVolumeKg()
        val sets = workoutSetDao.getTotalSets()
        val prs = workoutSetDao.getTopPersonalRecords()
        _state.update { it.copy(totalVolumeKg = vol, totalSets = sets, topPrs = prs) }
    }
}
