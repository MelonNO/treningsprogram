package com.migul.treningsprogram.ui.shared

import androidx.lifecycle.ViewModel
import com.migul.treningsprogram.domain.model.WorkoutResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SharedWorkoutResultViewModel @Inject constructor() : ViewModel() {

    private var resultForProgram: WorkoutResult? = null
    private var resultForHome: WorkoutResult? = null
    var workoutDayOfWeek: Int = 0
        private set

    fun setResult(result: WorkoutResult, dayOfWeek: Int) {
        resultForProgram = result
        resultForHome = result
        workoutDayOfWeek = dayOfWeek
    }

    fun consumeForProgram(): WorkoutResult? = resultForProgram.also { resultForProgram = null }
    fun consumeForHome(): WorkoutResult? = resultForHome.also { resultForHome = null }
}
