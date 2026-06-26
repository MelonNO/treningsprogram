package com.migul.treningsprogram.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.R
import com.migul.treningsprogram.databinding.FragmentSettingsTrainingBinding
import com.migul.treningsprogram.domain.TrainingDaySelection
import com.migul.treningsprogram.ui.onboarding.OnboardingBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsTrainingFragment : Fragment() {

    private var _binding: FragmentSettingsTrainingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    private val gymPresetsViewModel: GymPresetsViewModel by activityViewModels()

    private val goals = listOf("Strength", "Hypertrophy", "Endurance", "Weight Loss")
    private val experiences = listOf("Beginner", "Intermediate", "Advanced")

    private var initialDays = 0
    private var initialDuration = 0
    private var initialGoalIdx = 0
    private var initialExpIdx = 0
    private var initialCardio = false
    private var initialInjuries = ""
    private var initialSeverity = ""
    private var initialDisliked = ""
    private var initialMuscles = ""
    private var initialPresetId = -1L
    private var initialLetAiChoose = false
    private var initialRestCsv = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsTrainingBinding.inflate(inflater, container, false)
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

        // --- populate fields from saved prefs (no listeners attached yet) ---
        val prefs = viewModel.prefs
        binding.etDaysPerWeek.setText(prefs.daysPerWeek.toString())
        binding.etSessionDuration.setText(prefs.sessionDurationMinutes.toString())
        binding.spinnerGoal.setSelection(goals.indexOf(prefs.fitnessGoal).coerceAtLeast(0))
        binding.spinnerExperience.setSelection(experiences.indexOf(prefs.experienceLevel).coerceAtLeast(0))
        binding.switchSeparateCardio.isChecked = prefs.separateCardioDays
        binding.etInjuries.setText(prefs.injuries)
        binding.etDislikedExercises.setText(prefs.dislikedExercises)

        // Severity selector: reveal + pre-check only when injuries non-blank
        if (prefs.injuries.isNotBlank()) {
            setSeverityVisible(true)
            when (prefs.injurySeverity) {
                "Mild"     -> binding.chipSeverityMildSettings.isChecked = true
                "Moderate" -> binding.chipSeverityModerateSettings.isChecked = true
                "Severe"   -> binding.chipSeveritySevereSettings.isChecked = true
            }
        } else {
            setSeverityVisible(false)
        }

        // B08: initialise day-selection mode from the saved rest-day CSV. Non-blank ⇒ rest-day mode
        // (switch off, chips pre-checked); blank ⇒ count mode (switch on). Existing users (who never
        // set rest days) have a blank CSV and so land in count mode — preserving their behaviour.
        val savedRest = TrainingDaySelection.parseRestDays(prefs.restDaysCsv)
        binding.switchLetAiChooseDays.isChecked = savedRest.isEmpty()
        binding.chipRest1.isChecked = 1 in savedRest
        binding.chipRest2.isChecked = 2 in savedRest
        binding.chipRest3.isChecked = 3 in savedRest
        binding.chipRest4.isChecked = 4 in savedRest
        binding.chipRest5.isChecked = 5 in savedRest
        binding.chipRest6.isChecked = 6 in savedRest
        binding.chipRest7.isChecked = 7 in savedRest
        updateDayModeUi()

        val savedMuscles = prefs.priorityMuscles.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        binding.chipSettingsMuscleChest.isChecked     = "Chest" in savedMuscles
        binding.chipSettingsMuscleBack.isChecked      = "Back" in savedMuscles
        binding.chipSettingsMuscleShoulders.isChecked = "Shoulders" in savedMuscles
        binding.chipSettingsMuscleArms.isChecked      = "Arms" in savedMuscles
        binding.chipSettingsMuscleLegs.isChecked      = "Legs" in savedMuscles
        binding.chipSettingsMuscleGlutes.isChecked    = "Glutes" in savedMuscles
        binding.chipSettingsMuscleCore.isChecked      = "Core" in savedMuscles

        // snapshot initial state BEFORE attaching listeners
        snapshotState()

        // --- attach change listeners ---
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateSaveButton() }
        }
        binding.etDaysPerWeek.addTextChangedListener(textWatcher)
        binding.etSessionDuration.addTextChangedListener(textWatcher)
        binding.etInjuries.addTextChangedListener(textWatcher)
        binding.etDislikedExercises.addTextChangedListener(textWatcher)

        // Reveal the severity selector only while the injuries field is non-blank
        binding.etInjuries.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasInjury = s?.toString()?.trim()?.isNotBlank() == true
                setSeverityVisible(hasInjury)
                if (!hasInjury) binding.chipGroupSeveritySettings.clearCheck()
            }
        })

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSaveButton()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerGoal.onItemSelectedListener = spinnerListener
        binding.spinnerExperience.onItemSelectedListener = spinnerListener

        binding.switchSeparateCardio.setOnCheckedChangeListener { _, _ -> updateSaveButton() }
        binding.chipGroupPriorityMusclesSettings.setOnCheckedStateChangeListener { _, _ -> updateSaveButton() }
        binding.chipGroupSeveritySettings.setOnCheckedStateChangeListener { _, _ -> updateSaveButton() }
        // B08: day-selection mode toggle + rest-day chip changes.
        binding.switchLetAiChooseDays.setOnCheckedChangeListener { _, _ -> updateDayModeUi(); updateSaveButton() }
        binding.chipGroupRestDays.setOnCheckedStateChangeListener { _, _ -> updateTrainingDaysHint(); updateSaveButton() }

        // --- static actions ---
        binding.btnManagePresets.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsTrainingFragment)
                findNavController().navigate(R.id.action_settings_training_to_gym_presets)
        }

        setFragmentResultListener(OnboardingBottomSheet.RESULT_KEY) { _, bundle ->
            val onboardingContext = bundle.getString(OnboardingBottomSheet.RESULT_CONTEXT, "")
            viewModel.generateProgramWithOnboarding(
                onboardingContext = onboardingContext,
                daysPerWeek = prefs.daysPerWeek,
                goal = prefs.fitnessGoal,
                experience = prefs.experienceLevel,
                sessionDurationMinutes = prefs.sessionDurationMinutes
            )
        }

        binding.btnSave.setOnClickListener { saveAndPrompt() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                gymPresetsViewModel.presets.collect { presets ->
                    val selectedId = gymPresetsViewModel.selectedPresetId
                    binding.tvActivePreset.text = when {
                        selectedId == -1L -> "Active: No gym equipment (bodyweight only)"
                        else -> presets.find { it.id == selectedId }
                            ?.let { "Active: ${it.name}" }
                            ?: "No preset selected"
                    }
                }
            }
        }
    }

    private fun snapshotState() {
        initialDays      = binding.etDaysPerWeek.text.toString().toIntOrNull() ?: 0
        initialDuration  = binding.etSessionDuration.text.toString().toIntOrNull() ?: 0
        initialGoalIdx   = binding.spinnerGoal.selectedItemPosition
        initialExpIdx    = binding.spinnerExperience.selectedItemPosition
        initialCardio    = binding.switchSeparateCardio.isChecked
        initialInjuries  = binding.etInjuries.text.toString()
        initialSeverity  = currentSeverity()
        initialDisliked  = binding.etDislikedExercises.text.toString()
        initialMuscles   = currentMuscleString()
        initialPresetId  = gymPresetsViewModel.selectedPresetId
        initialLetAiChoose = binding.switchLetAiChooseDays.isChecked
        initialRestCsv   = currentRestCsv()
    }

    // ── B08: day-selection mode ──────────────────────────────────────────────────────────────────

    private fun isRestDayMode(): Boolean = !binding.switchLetAiChooseDays.isChecked

    private fun selectedRestDays(): Set<Int> = buildSet {
        if (binding.chipRest1.isChecked) add(1)
        if (binding.chipRest2.isChecked) add(2)
        if (binding.chipRest3.isChecked) add(3)
        if (binding.chipRest4.isChecked) add(4)
        if (binding.chipRest5.isChecked) add(5)
        if (binding.chipRest6.isChecked) add(6)
        if (binding.chipRest7.isChecked) add(7)
    }

    private fun currentRestCsv(): String =
        if (isRestDayMode()) TrainingDaySelection.formatRestDays(selectedRestDays()) else ""

    private fun updateDayModeUi() {
        val restMode = isRestDayMode()
        binding.layoutRestMode.visibility = if (restMode) View.VISIBLE else View.GONE
        binding.layoutCountMode.visibility = if (restMode) View.GONE else View.VISIBLE
        if (restMode) updateTrainingDaysHint()
    }

    private fun updateTrainingDaysHint() {
        val training = TrainingDaySelection.trainingDaysFrom(selectedRestDays())
        binding.tvTrainingDaysHint.text = if (training.isEmpty())
            "Pick at least one training day (leave a day un-selected)."
        else
            "Training ${training.size} day${if (training.size == 1) "" else "s"}/week: ${TrainingDaySelection.dayNames(training)}"
    }

    private fun setSeverityVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        binding.tvSeverityLabelSettings.visibility = v
        binding.chipGroupSeveritySettings.visibility = v
        binding.tvSeverityHintSettings.visibility = v
    }

    private fun currentSeverity(): String = when {
        binding.chipSeveritySevereSettings.isChecked   -> "Severe"
        binding.chipSeverityModerateSettings.isChecked -> "Moderate"
        binding.chipSeverityMildSettings.isChecked     -> "Mild"
        else -> ""
    }

    private fun currentMuscleString(): String {
        val list = mutableListOf<String>()
        if (binding.chipSettingsMuscleChest.isChecked)     list.add("Chest")
        if (binding.chipSettingsMuscleBack.isChecked)      list.add("Back")
        if (binding.chipSettingsMuscleShoulders.isChecked) list.add("Shoulders")
        if (binding.chipSettingsMuscleArms.isChecked)      list.add("Arms")
        if (binding.chipSettingsMuscleLegs.isChecked)      list.add("Legs")
        if (binding.chipSettingsMuscleGlutes.isChecked)    list.add("Glutes")
        if (binding.chipSettingsMuscleCore.isChecked)      list.add("Core")
        return list.joinToString(",")
    }

    private fun hasChanges(): Boolean {
        return binding.etDaysPerWeek.text.toString().toIntOrNull() != initialDays
            || binding.etSessionDuration.text.toString().toIntOrNull() != initialDuration
            || binding.spinnerGoal.selectedItemPosition != initialGoalIdx
            || binding.spinnerExperience.selectedItemPosition != initialExpIdx
            || binding.switchSeparateCardio.isChecked != initialCardio
            || binding.etInjuries.text.toString() != initialInjuries
            || currentSeverity() != initialSeverity
            || binding.etDislikedExercises.text.toString() != initialDisliked
            || currentMuscleString() != initialMuscles
            || gymPresetsViewModel.selectedPresetId != initialPresetId
            || binding.switchLetAiChooseDays.isChecked != initialLetAiChoose
            || currentRestCsv() != initialRestCsv
    }

    private fun updateSaveButton() {
        binding.btnSave.visibility = if (hasChanges()) View.VISIBLE else View.GONE
    }

    private fun saveAndPrompt() {
        val prefs = viewModel.prefs
        // B08: resolve days/week from the active mode. Rest-day mode stores the rest days and derives
        // the count; count mode clears the rest days and uses the entered number.
        val days: Int
        if (isRestDayMode()) {
            val restDays = selectedRestDays()
            if (restDays.size >= 7) {
                Snackbar.make(binding.root, "Pick at least one training day (leave one day un-selected).", Snackbar.LENGTH_SHORT).show()
                return
            }
            prefs.restDaysCsv = TrainingDaySelection.formatRestDays(restDays)
            days = TrainingDaySelection.daysPerWeekFrom(restDays)
        } else {
            prefs.restDaysCsv = ""
            days = binding.etDaysPerWeek.text.toString().toIntOrNull()?.coerceIn(1, 7) ?: 4
        }
        val duration = binding.etSessionDuration.text.toString().toIntOrNull()?.coerceIn(15, 180) ?: 60
        val goal = goals[binding.spinnerGoal.selectedItemPosition]
        val experience = experiences[binding.spinnerExperience.selectedItemPosition]
        val separateCardio = binding.switchSeparateCardio.isChecked
        val injuries = binding.etInjuries.text.toString().trim()
        // Severity only meaningful with an injury described; clear it otherwise.
        val injurySeverity = if (injuries.isNotBlank()) currentSeverity() else ""
        val disliked = binding.etDislikedExercises.text.toString().trim()
        val priorityMuscles = currentMuscleString()

        val changed = hasChanges()

        viewModel.save(
            apiKey = prefs.apiKey,
            daysPerWeek = days,
            goal = goal,
            experience = experience,
            sessionDurationMinutes = duration,
            separateCardioDays = separateCardio,
            injuries = injuries,
            injurySeverity = injurySeverity,
            priorityMuscles = priorityMuscles,
            dislikedExercises = disliked
        )

        snapshotState()
        updateSaveButton()

        if (!changed) {
            Snackbar.make(binding.root, "Saved", Snackbar.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Settings saved")
            .setMessage("Your training profile has changed. A new program must be generated for these settings to take effect.\n\nGenerate a new program now?")
            .setPositiveButton("Generate now") { _, _ -> triggerGenerate(days, goal, experience, duration) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun triggerGenerate(days: Int, goal: String, experience: String, duration: Int) {
        val prefs = viewModel.prefs
        if (prefs.apiKey.isBlank()) {
            Snackbar.make(binding.root, "Set your API key in AI & Program first.", Snackbar.LENGTH_LONG).show()
            return
        }
        if (!prefs.hasCompletedOnboarding) {
            OnboardingBottomSheet.newInstance(goal = goal, experience = experience)
                .show(childFragmentManager, OnboardingBottomSheet.RESULT_KEY)
        } else {
            viewModel.generateProgram(
                daysPerWeek = days,
                goal = goal,
                experience = experience,
                sessionDurationMinutes = duration
            )
            Snackbar.make(binding.root, "Generating new program…", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check in case the gym preset changed while on GymPresetsFragment
        if (_binding != null) updateSaveButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
