package com.migul.treningsprogram.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GymPresetsViewModel @Inject constructor(
    private val dao: GymPresetDao,
    private val prefs: PreferencesManager,
    private val gson: Gson
) : ViewModel() {

    val presets: StateFlow<List<GymPreset>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPresetId: Long get() = prefs.selectedGymPresetId

    fun selectPreset(id: Long) { prefs.selectedGymPresetId = id }

    fun addPreset(name: String, equipment: List<String>, notes: String) {
        viewModelScope.launch {
            dao.insert(GymPreset(name = name, equipmentJson = gson.toJson(equipment), notes = notes))
        }
    }

    fun updatePreset(preset: GymPreset, newName: String, equipment: List<String>, notes: String) {
        viewModelScope.launch {
            dao.update(preset.copy(name = newName, equipmentJson = gson.toJson(equipment), notes = notes))
        }
    }

    fun deletePreset(preset: GymPreset) {
        viewModelScope.launch {
            dao.delete(preset)
            if (prefs.selectedGymPresetId == preset.id) prefs.selectedGymPresetId = -1L
        }
    }

    fun getEquipment(preset: GymPreset): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return try { gson.fromJson(preset.equipmentJson, type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }
}
