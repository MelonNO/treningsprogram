package com.migul.treningsprogram.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.db.dao.WeeklySummaryDao
import com.migul.treningsprogram.data.db.entity.WeeklySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * B1: backs the scrollable weekly-coach-summary history screen. Observes all persisted summaries
 * newest-first; the Fragment shows an empty state when the list is empty.
 */
@HiltViewModel
class WeeklySummaryViewModel @Inject constructor(
    weeklySummaryDao: WeeklySummaryDao
) : ViewModel() {

    val summaries: StateFlow<List<WeeklySummary>> =
        weeklySummaryDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
