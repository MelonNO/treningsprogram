package com.migul.treningsprogram.ui.history

import androidx.lifecycle.ViewModel
import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RecapTrendsViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    suspend fun getStrengthHistory(name: String): List<StrengthPoint> =
        workoutRepository.getStrengthHistory(name)
}
