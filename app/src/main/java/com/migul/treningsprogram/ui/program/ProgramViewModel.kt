package com.migul.treningsprogram.ui.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.data.repository.thisMonday
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProgramViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    val weekPlan: StateFlow<List<PlannedExercise>> =
        workoutRepository.getPlannedForWeek(thisMonday())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDay = MutableStateFlow(currentDayOfWeek())
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    val selectedDayExercises: StateFlow<List<PlannedExercise>> =
        combine(weekPlan, selectedDay) { plan, day -> plan.filter { it.dayOfWeek == day } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekProgress: StateFlow<Pair<Int, Int>> =
        weekPlan.map { plan ->
            plan.count { it.isLogged } to plan.size
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0)

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
        viewModelScope.launch {
            workoutRepository.updatePlannedExercise(
                exercise.copy(exerciseName = newName, isLogged = false, actualWeightKg = 0f, actualReps = "", actualSets = 0)
            )
        }
    }
}
