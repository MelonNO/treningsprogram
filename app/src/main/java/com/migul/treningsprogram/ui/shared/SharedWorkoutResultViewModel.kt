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

    // P2: set true when a completed workout COMMITTED a "do another day today" move, so the Program
    // tab knows to rebalance the week (regardless of the P1 toggle). Consumed once on the Program tab.
    private var moveRebalancePending = false

    fun setResult(result: WorkoutResult, dayOfWeek: Int) {
        resultForProgram = result
        resultForHome = result
        workoutDayOfWeek = dayOfWeek
    }

    fun setMoveRebalancePending() { moveRebalancePending = true }
    fun consumeMoveRebalancePending(): Boolean = moveRebalancePending.also { moveRebalancePending = false }

    fun consumeForProgram(): WorkoutResult? = resultForProgram.also { resultForProgram = null }
    fun consumeForHome(): WorkoutResult? = resultForHome.also { resultForHome = null }
}
