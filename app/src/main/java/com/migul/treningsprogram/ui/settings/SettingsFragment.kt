package com.migul.treningsprogram.ui.settings

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.R
import com.migul.treningsprogram.databinding.FragmentSettingsBinding
import com.migul.treningsprogram.ui.onboarding.OnboardingBottomSheet
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

        binding.etInjuries.setText(prefs.injuries)
        binding.etDislikedExercises.setText(prefs.dislikedExercises)
        val savedMuscles = prefs.priorityMuscles.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        binding.chipSettingsMuscleChest.isChecked     = "Chest" in savedMuscles
        binding.chipSettingsMuscleBack.isChecked      = "Back" in savedMuscles
        binding.chipSettingsMuscleShoulders.isChecked = "Shoulders" in savedMuscles
        binding.chipSettingsMuscleArms.isChecked      = "Arms" in savedMuscles
        binding.chipSettingsMuscleLegs.isChecked      = "Legs" in savedMuscles
        binding.chipSettingsMuscleGlutes.isChecked    = "Glutes" in savedMuscles
        binding.chipSettingsMuscleCore.isChecked      = "Core" in savedMuscles

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

        // Listen for onboarding result before any click can fire
        setFragmentResultListener(OnboardingBottomSheet.RESULT_KEY) { _, bundle ->
            val context = bundle.getString(OnboardingBottomSheet.RESULT_CONTEXT, "")
            val days = binding.etDaysPerWeek.text.toString().toIntOrNull()?.coerceIn(1, 7) ?: prefs.daysPerWeek
            val duration = binding.etSessionDuration.text.toString().toIntOrNull()?.coerceIn(15, 180) ?: prefs.sessionDurationMinutes
            viewModel.generateProgramWithOnboarding(
                onboardingContext = context,
                daysPerWeek = days,
                goal = goals[binding.spinnerGoal.selectedItemPosition],
                experience = experiences[binding.spinnerExperience.selectedItemPosition],
                sessionDurationMinutes = duration
            )
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

            if (!prefs.hasCompletedOnboarding) {
                OnboardingBottomSheet.newInstance(
                    goal = goals[binding.spinnerGoal.selectedItemPosition],
                    experience = experiences[binding.spinnerExperience.selectedItemPosition]
                ).show(childFragmentManager, OnboardingBottomSheet.RESULT_KEY)
            } else {
                viewModel.generateProgram(
                    daysPerWeek = days,
                    goal = goals[binding.spinnerGoal.selectedItemPosition],
                    experience = experiences[binding.spinnerExperience.selectedItemPosition],
                    sessionDurationMinutes = duration
                )
            }
        }

        binding.btnSave.setOnClickListener {
            val days = binding.etDaysPerWeek.text.toString().toIntOrNull()?.coerceIn(1, 7) ?: 4
            val duration = binding.etSessionDuration.text.toString().toIntOrNull()?.coerceIn(15, 180) ?: 60
            val priorityList = mutableListOf<String>()
            if (binding.chipSettingsMuscleChest.isChecked)     priorityList.add("Chest")
            if (binding.chipSettingsMuscleBack.isChecked)      priorityList.add("Back")
            if (binding.chipSettingsMuscleShoulders.isChecked) priorityList.add("Shoulders")
            if (binding.chipSettingsMuscleArms.isChecked)      priorityList.add("Arms")
            if (binding.chipSettingsMuscleLegs.isChecked)      priorityList.add("Legs")
            if (binding.chipSettingsMuscleGlutes.isChecked)    priorityList.add("Glutes")
            if (binding.chipSettingsMuscleCore.isChecked)      priorityList.add("Core")
            viewModel.save(
                apiKey = binding.etApiKey.text.toString().trim(),
                daysPerWeek = days,
                goal = goals[binding.spinnerGoal.selectedItemPosition],
                experience = experiences[binding.spinnerExperience.selectedItemPosition],
                sessionDurationMinutes = duration,
                separateCardioDays = binding.switchSeparateCardio.isChecked,
                injuries = binding.etInjuries.text.toString().trim(),
                priorityMuscles = priorityList.joinToString(","),
                dislikedExercises = binding.etDislikedExercises.text.toString().trim()
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
                            val errorColor = requireContext().getColor(com.google.android.material.R.color.design_default_color_error)
                            val successColor = requireContext().getColor(android.R.color.holo_green_dark)
                            val defaultColor = requireContext().getColor(R.color.on_surface_variant)
                            binding.tvGenerateStatus.setTextColor(
                                when {
                                    status.startsWith("Program rejected") -> errorColor
                                    status.startsWith("New program") || status.startsWith("Program generated") -> successColor
                                    else -> defaultColor
                                }
                            )
                        } else {
                            binding.tvGenerateStatus.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.retryLog.collect { entries ->
                        binding.layoutRetryLog.removeAllViews()
                        if (entries.isEmpty()) {
                            binding.layoutRetryLog.visibility = View.GONE
                            return@collect
                        }
                        binding.layoutRetryLog.visibility = View.VISIBLE
                        val ctx = requireContext()
                        val dp = ctx.resources.displayMetrics.density
                        val errorColor = ctx.getColor(com.google.android.material.R.color.design_default_color_error)
                        val warnColor = 0xFFFFB347.toInt()
                        entries.forEach { entry ->
                            val accentColor = if (entry.failed) errorColor else warnColor
                            val row = LinearLayout(ctx).apply {
                                orientation = LinearLayout.HORIZONTAL
                                val vPad = (8 * dp).toInt()
                                setPadding(0, vPad, 0, vPad)
                            }
                            val bar = View(ctx).apply {
                                layoutParams = LinearLayout.LayoutParams((3 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT).also {
                                    it.marginEnd = (10 * dp).toInt()
                                }
                                setBackgroundColor(accentColor)
                            }
                            row.addView(bar)
                            val col = LinearLayout(ctx).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }
                            val label = TextView(ctx).apply {
                                text = "Attempt ${entry.attempt}${if (entry.failed) " — FAILED" else " — rejected"}"
                                setTextColor(accentColor)
                                textSize = 11f
                                setTypeface(null, Typeface.BOLD)
                            }
                            val body = TextView(ctx).apply {
                                text = entry.reason
                                setTextColor(ctx.getColor(R.color.on_surface_variant))
                                textSize = 12f
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                                    it.topMargin = (3 * dp).toInt()
                                }
                            }
                            col.addView(label)
                            col.addView(body)
                            row.addView(col)
                            binding.layoutRetryLog.addView(row)
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
