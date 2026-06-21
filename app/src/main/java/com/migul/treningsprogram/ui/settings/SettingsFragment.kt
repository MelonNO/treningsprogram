package com.migul.treningsprogram.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.R
import com.migul.treningsprogram.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private val gymPresetsViewModel: GymPresetsViewModel by viewModels()

    private val goals = listOf("Strength", "Hypertrophy", "Endurance", "Weight Loss")
    private val experiences = listOf("Beginner", "Intermediate", "Advanced")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val goalAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, goals)
        goalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGoal.adapter = goalAdapter

        val expAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, experiences)
        expAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerExperience.adapter = expAdapter

        val prefs = viewModel.prefs
        binding.etApiKey.setText(prefs.apiKey)
        binding.etDaysPerWeek.setText(prefs.daysPerWeek.toString())
        binding.etSessionDuration.setText(prefs.sessionDurationMinutes.toString())
        binding.spinnerGoal.setSelection(goals.indexOf(prefs.fitnessGoal).coerceAtLeast(0))
        binding.spinnerExperience.setSelection(experiences.indexOf(prefs.experienceLevel).coerceAtLeast(0))
        binding.switchSeparateCardio.isChecked = prefs.separateCardioDays

        binding.btnManagePresets.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_gym_presets)
        }

        binding.btnResetWorkouts.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset All Workouts?")
                .setMessage("This will permanently delete all workout sessions and sets. This cannot be undone.")
                .setPositiveButton("Delete All") { _, _ -> viewModel.resetAllWorkouts() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnGenerateNow.setOnClickListener {
            val days = binding.etDaysPerWeek.text.toString().toIntOrNull()?.coerceIn(1, 7) ?: prefs.daysPerWeek
            val duration = binding.etSessionDuration.text.toString().toIntOrNull()?.coerceIn(15, 180) ?: prefs.sessionDurationMinutes
            val apiKey = binding.etApiKey.text.toString().trim()
            if (apiKey.isBlank()) {
                Snackbar.make(binding.root, "Enter your Claude API key first.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.apiKey = apiKey
            viewModel.generateProgram(
                daysPerWeek = days,
                goal = goals[binding.spinnerGoal.selectedItemPosition],
                experience = experiences[binding.spinnerExperience.selectedItemPosition],
                sessionDurationMinutes = duration
            )
        }

        binding.btnSave.setOnClickListener {
            val days = binding.etDaysPerWeek.text.toString().toIntOrNull()?.coerceIn(1, 7) ?: 4
            val duration = binding.etSessionDuration.text.toString().toIntOrNull()?.coerceIn(15, 180) ?: 60
            viewModel.save(
                apiKey = binding.etApiKey.text.toString().trim(),
                daysPerWeek = days,
                goal = goals[binding.spinnerGoal.selectedItemPosition],
                experience = experiences[binding.spinnerExperience.selectedItemPosition],
                sessionDurationMinutes = duration,
                separateCardioDays = binding.switchSeparateCardio.isChecked
            )
            Snackbar.make(binding.root, getString(R.string.settings_saved), Snackbar.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    gymPresetsViewModel.presets.collect { presets ->
                        val selectedId = gymPresetsViewModel.selectedPresetId
                        val active = presets.find { it.id == selectedId }
                        binding.tvActivePreset.text = if (active != null) "Active: ${active.name}" else "No preset selected"
                    }
                }
                launch {
                    viewModel.isGenerating.collect { loading ->
                        binding.progressGenerate.visibility = if (loading) View.VISIBLE else View.GONE
                        binding.btnGenerateNow.isEnabled = !loading
                    }
                }
                launch {
                    viewModel.generateStatus.collect { status ->
                        if (status != null) {
                            binding.tvGenerateStatus.text = status
                            binding.tvGenerateStatus.visibility = View.VISIBLE
                        } else {
                            binding.tvGenerateStatus.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.lastAttemptCount.collect { count ->
                        if (count > 0) {
                            binding.layoutAttemptCounter.visibility = View.VISIBLE
                            binding.tvAttemptCounter.text = "$count / ${com.migul.treningsprogram.data.repository.AiRepository.MAX_GENERATION_ATTEMPTS}"
                            binding.tvAttemptCounter.setTextColor(
                                requireContext().getColor(
                                    if (count >= com.migul.treningsprogram.data.repository.AiRepository.MAX_GENERATION_ATTEMPTS)
                                        com.google.android.material.R.color.design_default_color_error
                                    else
                                        android.R.color.holo_green_dark
                                )
                            )
                        } else {
                            binding.layoutAttemptCounter.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.resetDone.collect { done ->
                        if (done) Snackbar.make(binding.root, "All workout history deleted.", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
