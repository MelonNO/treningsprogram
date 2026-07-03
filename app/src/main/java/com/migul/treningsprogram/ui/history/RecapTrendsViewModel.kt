package com.migul.treningsprogram.ui.history

import androidx.lifecycle.ViewModel
import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RecapTrendsViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val goalRepository: com.migul.treningsprogram.data.repository.GoalRepository
) : ViewModel() {

    suspend fun getStrengthHistory(name: String): List<StrengthPoint> =
        workoutRepository.getStrengthHistory(name)

    /** N5: this exercise's ACTIVE goal, for the target line on the trend charts. */
    suspend fun activeGoalFor(name: String): com.migul.treningsprogram.data.db.entity.LiftGoal? =
        goalRepository.activeForExercise(name)
}
