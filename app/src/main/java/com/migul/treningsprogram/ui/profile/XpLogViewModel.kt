package com.migul.treningsprogram.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.db.dao.XpEventDao
import com.migul.treningsprogram.data.db.entity.XpEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * U2: backs the XP log screen. Observes all recorded XP events newest-first; the Fragment shows an
 * empty state when the list is empty (fresh install or before any event has accrued).
 */
@HiltViewModel
class XpLogViewModel @Inject constructor(
    xpEventDao: XpEventDao
) : ViewModel() {

    val events: StateFlow<List<XpEvent>> =
        xpEventDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
