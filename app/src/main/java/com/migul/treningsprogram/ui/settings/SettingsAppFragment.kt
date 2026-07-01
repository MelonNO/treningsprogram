package com.migul.treningsprogram.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.databinding.FragmentSettingsAppBinding
import com.migul.treningsprogram.domain.DayBoundary
import dagger.hilt.android.AndroidEntryPoint

/**
 * Item 4: the "App Settings" screen — the single home for the two app-wide controls the user asked
 * to gather here: the day-boundary (day-reset) hour picker and the auto-rebalance-week toggle. Both
 * were relocated here (from Training Profile and the Program tab's options dialog respectively);
 * nothing else lives here.
 */
@AndroidEntryPoint
class SettingsAppFragment : Fragment() {

    private var _binding: FragmentSettingsAppBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    // Supported day-boundary hours (0..6).
    private val dayBoundaryHours = (DayBoundary.MIN_CUTOFF_HOUR..DayBoundary.MAX_CUTOFF_HOUR).toList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsAppBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDayBoundarySpinner()
        setupAutoRebalanceSwitch()
    }

    // Item 1: 24-hour labels (no AM/PM anywhere). Range is 00:00..06:00; annotate midnight + default.
    private fun setupDayBoundarySpinner() {
        val labels = dayBoundaryHours.map { hour ->
            val base = when (hour) {
                0 -> "00:00 (midnight)"
                else -> "%02d:00".format(hour)
            }
            if (hour == DayBoundary.DEFAULT_CUTOFF_HOUR) "$base — default" else base
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDayBoundary.adapter = adapter

        val saved = viewModel.prefs.dayBoundaryHour
        binding.spinnerDayBoundary.setSelection(dayBoundaryHours.indexOf(saved).coerceAtLeast(0))

        binding.spinnerDayBoundary.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val hour = dayBoundaryHours.getOrElse(position) { DayBoundary.DEFAULT_CUTOFF_HOUR }
                if (hour != viewModel.prefs.dayBoundaryHour) {
                    viewModel.prefs.dayBoundaryHour = hour  // persists + updates the process-wide holder
                    Snackbar.make(binding.root, "Day boundary saved", Snackbar.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // Item 2: default ON for anyone who has not explicitly chosen (handled in PreferencesManager).
    // Set the initial checked state BEFORE attaching the listener so simply displaying the screen
    // never writes the preference — only a real user toggle persists a value.
    private fun setupAutoRebalanceSwitch() {
        binding.switchAutoRebalance.isChecked = viewModel.prefs.autoRebalanceEnabled
        binding.switchAutoRebalance.setOnCheckedChangeListener { _, isChecked ->
            viewModel.prefs.autoRebalanceEnabled = isChecked
            Snackbar.make(
                binding.root,
                if (isChecked) "Auto-rebalance is ON" else "Auto-rebalance is OFF",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
