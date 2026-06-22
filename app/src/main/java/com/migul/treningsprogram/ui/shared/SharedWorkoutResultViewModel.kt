package com.migul.treningsprogram.ui.shared

import androidx.lifecycle.ViewModel
import com.migul.treningsprogram.domain.model.WorkoutResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SharedWorkoutResultViewModel @Inject constructor() : ViewModel() {

    private var resultForProgram: WorkoutResult? = null
    private var resultForHome: WorkoutResult? = null

    fun setResult(result: WorkoutResult) {
        resultForProgram = result
        resultForHome = result
    }

    fun consumeForProgram(): WorkoutResult? = resultForProgram.also { resultForProgram = null }
    fun consumeForHome(): WorkoutResult? = resultForHome.also { resultForHome = null }
}
