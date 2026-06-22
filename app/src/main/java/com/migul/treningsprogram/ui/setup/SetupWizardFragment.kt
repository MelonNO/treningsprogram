package com.migul.treningsprogram.ui.setup

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.databinding.FragmentSetupWizardBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.widget.LinearLayout

@AndroidEntryPoint
class SetupWizardFragment : Fragment() {

    private var _binding: FragmentSetupWizardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SetupWizardViewModel by viewModels()

    private var currentStep = 0
    private val LAST_INPUT_STEP = 4
    private val TOTAL_STEPS = 5

    private var selectedGoal = "Hypertrophy"
    private var selectedExperience = "Intermediate"
    private var selectedDays = 4
    private var selectedDuration = 60

    private val stepTitles = listOf(
        "Fitness Goal",
        "Training Schedule",
        "Equipment",
        "Training Profile",
        "Connect Claude"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSetupWizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateStepUI()

        binding.chipGroupGoal.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedGoal = when (checkedIds.firstOrNull()) {
                R.id.chip_goal_strength   -> "Strength"
                R.id.chip_goal_endurance  -> "Endurance"
                R.id.chip_goal_weightloss -> "Weight Loss"
                else                      -> "Hypertrophy"
            }
        }

        binding.chipGroupExperience.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedExperience = when (checkedIds.firstOrNull()) {
                R.id.chip_exp_beginner -> "Beginner"
                R.id.chip_exp_advanced -> "Advanced"
                else                   -> "Intermediate"
            }
        }

        binding.chipGroupDays.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedDays = when (checkedIds.firstOrNull()) {
                R.id.chip_days_2 -> 2
                R.id.chip_days_3 -> 3
                R.id.chip_days_5 -> 5
                R.id.chip_days_6 -> 6
                else             -> 4
            }
        }

        binding.chipGroupDuration.setOnCheckedStateChangeListener { _, checkedIds ->
            val isCustom = checkedIds.firstOrNull() == R.id.chip_dur_custom
            binding.tilCustomDuration.visibility = if (isCustom) View.VISIBLE else View.GONE
            if (!isCustom) {
                selectedDuration = when (checkedIds.firstOrNull()) {
                    R.id.chip_dur_30 -> 30
                    R.id.chip_dur_45 -> 45
                    R.id.chip_dur_75 -> 75
                    R.id.chip_dur_90 -> 90
                    else             -> 60
                }
            }
        }

        binding.btnAddPreset.setOnClickListener { showCreatePresetDialog() }

        binding.btnWizardBack.setOnClickListener { pulseButton(binding.btnWizardBack); goBack() }
        binding.btnWizardNext.setOnClickListener { pulseButton(binding.btnWizardNext); advance() }
        binding.tvWizardSkip.setOnClickListener { skipToGenerate() }

        binding.btnGoHome.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        binding.btnRetryGenerate.setOnClickListener {
            binding.layoutGenError.visibility = View.GONE
            binding.layoutGenerating.visibility = View.VISIBLE
            triggerGeneration()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.presets.collect { presets ->
                        renderEquipmentStep(presets)
                    }
                }

                launch {
                    viewModel.generationStatus.collect { status ->
                        if (status.isNotBlank()) binding.tvGeneratingDetail.text = status
                    }
                }

                launch {
                    viewModel.generationDone.collect { done ->
                        if (done) {
                            binding.layoutGenerating.visibility = View.GONE
                            binding.layoutWizardActions.visibility = View.GONE
                            binding.layoutSuccess.apply {
                                alpha = 0f
                                translationY = resources.displayMetrics.density * 28
                                visibility = View.VISIBLE
                                animate()
                                    .alpha(1f)
                                    .translationY(0f)
                                    .setDuration(400)
                                    .setInterpolator(DecelerateInterpolator())
                                    .start()
                            }
                            val attempts = viewModel.attemptCount.value
                            val reasons = viewModel.rejectionReasons.value
                            binding.tvSuccessDetail.text = if (reasons.isNotEmpty()) {
                                val reasonLines = reasons
                                    .mapIndexed { i, r -> "• Attempt ${i + 1} rejected: $r" }
                                    .joinToString("\n")
                                "Your plan was generated in $attempts attempts.\n$reasonLines\n\nCheck the Program tab to see your week."
                            } else {
                                "Check the Program tab to see your week."
                            }
                        }
                    }
                }

                launch {
                    viewModel.generationError.collect { err ->
                        if (err != null) {
                            binding.layoutGenerating.visibility = View.GONE
                            binding.layoutGenError.visibility = View.VISIBLE
                            binding.tvGenError.text = err
                        }
                    }
                }
            }
        }
    }

    private fun renderEquipmentStep(presets: List<GymPreset>) {
        val container = binding.layoutEquipPresets
        container.removeAllViews()
        val selectedId = viewModel.prefs.selectedGymPresetId
        val dp = resources.displayMetrics.density

        addPresetOptionCard(container, -1L, "Bodyweight only", "No equipment — bodyweight exercises only", selectedId == -1L, dp)

        presets.forEach { preset ->
            val equip = viewModel.getEquipmentList(preset)
            val summary = if (equip.isEmpty()) "No equipment"
                          else equip.take(5).joinToString("  ·  ") + if (equip.size > 5) "  +${equip.size - 5}" else ""
            addPresetOptionCard(container, preset.id, preset.name, summary, selectedId == preset.id, dp)
        }
    }

    private fun addPresetOptionCard(
        container: LinearLayout,
        id: Long,
        name: String,
        summary: String,
        isSelected: Boolean,
        dp: Float
    ) {
        val ctx = requireContext()

        val card = com.google.android.material.card.MaterialCardView(ctx).apply {
            radius = 12f * dp
            cardElevation = 0f
            strokeWidth = ((if (isSelected) 2f else 1f) * dp).toInt()
            strokeColor = if (isSelected) Color.parseColor("#7C67F5") else Color.parseColor("#3A3A4A")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * dp).toInt() }
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
        }

        val radioBtn = android.widget.RadioButton(ctx).apply {
            this.isChecked = isSelected
            isClickable = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (12 * dp).toInt() }
        }

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = android.widget.TextView(ctx).apply {
            text = name
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            if (isSelected) setTextColor(Color.parseColor("#7C67F5"))
        }

        val summaryView = android.widget.TextView(ctx).apply {
            text = summary
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(ctx.getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium))
        }

        textCol.addView(nameView)
        textCol.addView(summaryView)
        row.addView(radioBtn)
        row.addView(textCol)
        card.addView(row)

        card.setOnClickListener {
            card.animate()
                .scaleX(0.96f).scaleY(0.96f)
                .setDuration(80)
                .withEndAction {
                    card.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    viewModel.selectPreset(id)
                    renderEquipmentStep(viewModel.presets.value)
                }
                .start()
        }

        container.addView(card)
    }

    private fun showCreatePresetDialog() {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), 0)
        }

        fun field(hint: String, multiline: Boolean = false): com.google.android.material.textfield.TextInputLayout {
            val til = com.google.android.material.textfield.TextInputLayout(
                ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle
            ).apply {
                this.hint = hint
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (12 * dp).toInt() }
            }
            til.addView(com.google.android.material.textfield.TextInputEditText(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (multiline) {
                    minLines = 3
                    gravity = android.view.Gravity.TOP
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
            })
            return til
        }

        val tilName  = field("Preset name")
        val tilEquip = field("Equipment (one per line)", multiline = true)
        val tilNotes = field("Notes / limitations (optional)", multiline = true)
        layout.addView(tilName)
        layout.addView(tilEquip)
        layout.addView(tilNotes)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("New gym preset")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name  = tilName.editText?.text?.toString()?.trim() ?: ""
                val equip = tilEquip.editText?.text?.toString()
                    ?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val notes = tilNotes.editText?.text?.toString()?.trim() ?: ""
                if (name.isNotBlank()) viewModel.addPresetAndSelect(name, equip, notes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun advance() {
        when (currentStep) {
            0 -> nextStep()
            1 -> {
                if (binding.chipDurCustom.isChecked) {
                    val custom = binding.etCustomDuration.text?.toString()?.trim()?.toIntOrNull()
                    if (custom == null || custom < 10 || custom > 300) {
                        Snackbar.make(binding.root, "Enter a duration between 10 and 300 minutes.", Snackbar.LENGTH_SHORT).show()
                        return
                    }
                    selectedDuration = custom
                }
                nextStep()
            }
            2 -> nextStep() // Equipment — just proceed
            3 -> {
                // Training Profile — save and proceed
                val injuries = binding.etWizardInjuries.text?.toString()?.trim() ?: ""
                val dislikes = binding.etWizardDislikes.text?.toString()?.trim() ?: ""
                val priorityList = mutableListOf<String>()
                if (binding.chipMuscleChest.isChecked)     priorityList.add("Chest")
                if (binding.chipMuscleBack.isChecked)      priorityList.add("Back")
                if (binding.chipMuscleShoulders.isChecked) priorityList.add("Shoulders")
                if (binding.chipMuscleArms.isChecked)      priorityList.add("Arms")
                if (binding.chipMuscleLegs.isChecked)      priorityList.add("Legs")
                if (binding.chipMuscleGlutes.isChecked)    priorityList.add("Glutes")
                if (binding.chipMuscleCore.isChecked)      priorityList.add("Core")
                viewModel.prefs.injuries = injuries
                viewModel.prefs.priorityMuscles = priorityList.joinToString(",")
                viewModel.prefs.dislikedExercises = dislikes
                nextStep()
            }
            4 -> {
                // Connect Claude — validate key then generate
                val apiKey = binding.etWizardApiKey.text?.toString()?.trim() ?: ""
                if (apiKey.isBlank()) {
                    Snackbar.make(binding.root, "Please enter your Claude API key.", Snackbar.LENGTH_SHORT).show()
                    return
                }
                viewModel.prefs.apiKey = apiKey
                nextStep()
                triggerGeneration()
            }
        }
    }

    private fun nextStep() {
        currentStep++
        binding.stepFlipper.setInAnimation(requireContext(), R.anim.slide_in_right)
        binding.stepFlipper.setOutAnimation(requireContext(), R.anim.slide_out_left)
        binding.stepFlipper.displayedChild = currentStep
        updateStepUI()
    }

    private fun goBack() {
        if (currentStep == 0) {
            findNavController().popBackStack()
            return
        }
        currentStep--
        binding.stepFlipper.setInAnimation(requireContext(), R.anim.slide_in_left)
        binding.stepFlipper.setOutAnimation(requireContext(), R.anim.slide_out_right)
        binding.stepFlipper.displayedChild = currentStep
        updateStepUI()
    }

    private fun skipToGenerate() {
        currentStep = TOTAL_STEPS
        binding.stepFlipper.setInAnimation(requireContext(), R.anim.slide_in_right)
        binding.stepFlipper.setOutAnimation(requireContext(), R.anim.slide_out_left)
        binding.stepFlipper.displayedChild = TOTAL_STEPS
        updateStepUI()
        triggerGeneration()
    }

    private fun pulseButton(btn: View) {
        btn.animate()
            .scaleX(0.93f).scaleY(0.93f)
            .setDuration(80)
            .withEndAction {
                btn.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            .start()
    }

    private fun triggerGeneration() {
        val apiKey = binding.etWizardApiKey.text?.toString()?.trim()
            ?.takeIf { it.isNotBlank() } ?: viewModel.prefs.apiKey
        viewModel.generateProgram(
            goal = selectedGoal,
            experience = selectedExperience,
            daysPerWeek = selectedDays,
            sessionDurationMinutes = selectedDuration,
            separateCardioDays = binding.switchCardioWizard.isChecked,
            apiKey = apiKey
        )
    }

    private fun updateStepUI() {
        val isGeneratingStep = currentStep >= TOTAL_STEPS

        binding.progressWizard.visibility = if (isGeneratingStep) View.GONE else View.VISIBLE
        binding.tvWizardSkip.visibility   = if (isGeneratingStep || currentStep == LAST_INPUT_STEP) View.GONE else View.VISIBLE
        binding.layoutWizardActions.visibility = if (isGeneratingStep) View.GONE else View.VISIBLE

        if (!isGeneratingStep) {
            binding.tvWizardStepLabel.text = "STEP ${currentStep + 1} OF $TOTAL_STEPS"
            binding.tvWizardStepTitle.text = stepTitles.getOrElse(currentStep) { "" }
            binding.progressWizard.max = TOTAL_STEPS
            binding.progressWizard.progress = currentStep + 1
            binding.btnWizardBack.visibility = if (currentStep == 0) View.INVISIBLE else View.VISIBLE
            binding.btnWizardNext.text = if (currentStep == LAST_INPUT_STEP) "Generate My Program" else "Next"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
