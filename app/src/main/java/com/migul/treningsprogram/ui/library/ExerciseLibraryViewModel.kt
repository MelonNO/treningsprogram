package com.migul.treningsprogram.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.DbExerciseEntry
import com.migul.treningsprogram.data.ExerciseCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Drives the BROWSE-ONLY exercise library (E3). Reads the FULL bundled catalog from
 * [ExerciseCatalog.entries] (loaded once at app start in TreningsprogramApp) and exposes
 * a reactively filtered list plus the available filter options.
 *
 * Filtering is delegated to the pure [ExerciseLibraryFilter] so the logic stays JVM-testable.
 */
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor() : ViewModel() {

    /** Sentinel used by the UI for the "all" option in each filter. */
    val allEntries: List<DbExerciseEntry> = ExerciseCatalog.entries

    val muscleGroups: List<String> = ExerciseLibraryFilter.muscleGroups(allEntries)
    val equipmentOptions: List<String> = ExerciseLibraryFilter.equipmentOptions(allEntries)

    private val _query = MutableStateFlow("")
    private val _muscle = MutableStateFlow<String?>(null)
    private val _equipment = MutableStateFlow<String?>(null)

    val query: StateFlow<String> = _query.asStateFlow()
    val muscle: StateFlow<String?> = _muscle.asStateFlow()
    val equipment: StateFlow<String?> = _equipment.asStateFlow()

    val results: StateFlow<List<DbExerciseEntry>> =
        combine(_query, _muscle, _equipment) { q, m, eq ->
            ExerciseLibraryFilter.filter(allEntries, q, m, eq)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ExerciseLibraryFilter.filter(allEntries)
        )

    fun setQuery(q: String) { _query.value = q }
    fun setMuscle(m: String?) { _muscle.value = m }
    fun setEquipment(eq: String?) { _equipment.value = eq }
}
