package com.migul.treningsprogram.ui.setup

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.migul.treningsprogram.domain.TrainingDaySelection
import com.migul.treningsprogram.domain.model.OnboardingQuestion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

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

    // S8 fix: restore step position + selections across rotation / process death
    companion object {
        private const val KEY_STEP = "wizard_current_step"
        private const val KEY_GOAL = "wizard_selected_goal"
        private const val KEY_EXPERIENCE = "wizard_selected_experience"
        private const val KEY_DAYS = "wizard_selected_days"
        private const val KEY_DURATION = "wizard_selected_duration"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, currentStep)
        outState.putString(KEY_GOAL, selectedGoal)
        outState.putString(KEY_EXPERIENCE, selectedExperience)
        outState.putInt(KEY_DAYS, selectedDays)
        outState.putInt(KEY_DURATION, selectedDuration)
    }

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

        // S8 fix: restore step and selections on rotation / process-death restore.
        if (savedInstanceState != null) {
            currentStep       = savedInstanceState.getInt(KEY_STEP, 0)
            selectedGoal      = savedInstanceState.getString(KEY_GOAL, "Hypertrophy") ?: "Hypertrophy"
            selectedExperience = savedInstanceState.getString(KEY_EXPERIENCE, "Intermediate") ?: "Intermediate"
            selectedDays      = savedInstanceState.getInt(KEY_DAYS, 4)
            selectedDuration  = savedInstanceState.getInt(KEY_DURATION, 60)
            binding.stepFlipper.displayedChild = currentStep
        }

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

        // B08: day-selection mode. Default (switch OFF) = pick rest days; ON = pick a number of days
        // and let the AI choose which are rest. Toggle which control is shown + keep the hint live.
        binding.switchLetAiChooseDays.setOnCheckedChangeListener { _, _ -> updateDayModeUi() }
        binding.chipGroupRestDays.setOnCheckedStateChangeListener { _, _ -> updateTrainingDaysHint() }
        updateDayModeUi()

        // Severity selector: reveal only while injuries field is non-blank
        binding.etWizardInjuries.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasInjury = s?.toString()?.trim()?.isNotBlank() == true
                setWizardSeverityVisible(hasInjury)
                if (!hasInjury) binding.chipGroupWizardSeverity.clearCheck()
            }
        })
        // Restore previously chosen injuries/severity if the wizard is re-entered
        if (viewModel.prefs.injuries.isNotBlank()) {
            binding.etWizardInjuries.setText(viewModel.prefs.injuries)
            setWizardSeverityVisible(true)
            when (viewModel.prefs.injurySeverity) {
                "Mild"     -> binding.chipWizardSeverityMild.isChecked = true
                "Moderate" -> binding.chipWizardSeverityModerate.isChecked = true
                "Severe"   -> binding.chipWizardSeveritySevere.isChecked = true
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

                // P5: rotate friendly/informative wait copy below the real status line while the
                // wizard generates the first program. The real status (tvGeneratingDetail) stays above.
                launch {
                    viewModel.isGenerating.collectLatest { generating ->
                        binding.tvGeneratingTip.visibility = if (generating) View.VISIBLE else View.GONE
                        if (!generating) return@collectLatest
                        var i = 0
                        while (true) {
                            binding.tvGeneratingTip.text =
                                com.migul.treningsprogram.ui.common.GenerationTips.tip(i++)
                            kotlinx.coroutines.delay(4500)
                        }
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

    // ── B08: day-selection mode ─────────────────────────────────────────────────────────────────

    /** True when the user is choosing specific rest days (switch OFF, the default mode). */
    private fun isRestDayMode(): Boolean = !binding.switchLetAiChooseDays.isChecked

    /** Rest weekdays currently selected (1=Mon … 7=Sun). */
    private fun selectedRestDays(): Set<Int> = buildSet {
        if (binding.chipRest1.isChecked) add(1)
        if (binding.chipRest2.isChecked) add(2)
        if (binding.chipRest3.isChecked) add(3)
        if (binding.chipRest4.isChecked) add(4)
        if (binding.chipRest5.isChecked) add(5)
        if (binding.chipRest6.isChecked) add(6)
        if (binding.chipRest7.isChecked) add(7)
    }

    private fun updateDayModeUi() {
        val restMode = isRestDayMode()
        binding.layoutRestMode.visibility = if (restMode) View.VISIBLE else View.GONE
        binding.layoutCountMode.visibility = if (restMode) View.GONE else View.VISIBLE
        if (restMode) updateTrainingDaysHint()
    }

    private fun updateTrainingDaysHint() {
        val rest = selectedRestDays()
        val training = TrainingDaySelection.trainingDaysFrom(rest)
        binding.tvTrainingDaysHint.text = if (training.isEmpty())
            "Pick at least one training day (leave a day un-selected)."
        else
            "Training ${training.size} day${if (training.size == 1) "" else "s"}/week: ${TrainingDaySelection.dayNames(training)}"
    }

    private fun setWizardSeverityVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        binding.tvWizardSeverityLabel.visibility = v
        binding.chipGroupWizardSeverity.visibility = v
        binding.tvWizardSeverityHint.visibility = v
    }

    private fun currentWizardSeverity(): String = when {
        binding.chipWizardSeveritySevere.isChecked   -> "Severe"
        binding.chipWizardSeverityModerate.isChecked -> "Moderate"
        binding.chipWizardSeverityMild.isChecked     -> "Mild"
        else -> ""
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
            strokeColor = if (isSelected) Color.parseColor("#7FE9E1") else Color.parseColor("#2C544F")
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
            if (isSelected) setTextColor(Color.parseColor("#7FE9E1"))
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
                // B08: persist the day-selection mode. Rest-day mode ⇒ store the chosen rest days and
                // derive the training-day count; count mode ⇒ clear rest days (selectedDays already
                // tracked by the count chip group).
                if (isRestDayMode()) {
                    val rest = selectedRestDays()
                    if (rest.size >= 7) {
                        Snackbar.make(binding.root, "Pick at least one training day (leave one day un-selected).", Snackbar.LENGTH_SHORT).show()
                        return
                    }
                    viewModel.prefs.restDaysCsv = TrainingDaySelection.formatRestDays(rest)
                    selectedDays = TrainingDaySelection.daysPerWeekFrom(rest)
                } else {
                    viewModel.prefs.restDaysCsv = ""
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
                viewModel.prefs.injurySeverity = if (injuries.isNotBlank()) currentWizardSeverity() else ""
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
                // Item 11: run the AI injury-sufficiency check (setup wizard only) now that the API
                // key is available, BEFORE the description is finalised for generation.
                runInjuryCheckThenGenerate()
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

    // ── Item 11: setup-wizard injury sufficiency check ────────────────────────────────────────────

    private fun advanceToGeneration() {
        if (_binding == null) return
        nextStep()
        triggerGeneration()
    }

    private fun runInjuryCheckThenGenerate() {
        val injuries = viewModel.prefs.injuries.trim()
        if (injuries.isEmpty()) { advanceToGeneration(); return }   // empty injury = skip the check (no-op)
        val busy = showBusyDialog("Reviewing your injury details…")
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.checkInjurySufficiency(injuries)
            runCatching { busy.dismiss() }
            if (_binding == null) return@launch
            result.onSuccess { s ->
                if (s.sufficient || s.questions.isEmpty()) advanceToGeneration()
                else showInjuryFollowupDialog(injuries, s.questions)
            }.onFailure {
                // No key / offline / timeout / parse error → never block finishing the wizard.
                advanceToGeneration()
            }
        }
    }

    private fun showInjuryFollowupDialog(original: String, questions: List<OnboardingQuestion>) {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * d).toInt()
            setPadding(pad, (8 * d).toInt(), pad, 0)
        }
        container.addView(TextView(ctx).apply {
            text = "A few quick questions so your program works around your injury:"
            setPadding(0, 0, 0, (8 * d).toInt())
        })
        val inputs = questions.map { q ->
            container.addView(TextView(ctx).apply {
                text = if (q.type == "choice" && q.options.isNotEmpty())
                    "${q.question}  (${q.options.joinToString(" / ")})" else q.question
                setPadding(0, (10 * d).toInt(), 0, 0)
            })
            val edit = EditText(ctx).apply { hint = "Your answer"; setSingleLine(false) }
            container.addView(edit)
            q to edit
        }
        val scroll = ScrollView(ctx).apply { addView(container) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Tell me a bit more")
            .setView(scroll)
            .setNegativeButton("Skip") { _, _ -> advanceToGeneration() }
            .setPositiveButton("Update") { _, _ ->
                val answers = inputs.map { (q, e) -> q.question to (e.text?.toString()?.trim() ?: "") }
                rewriteInjuriesThenGenerate(original, answers)
            }
            .setOnCancelListener { advanceToGeneration() }
            .show()
    }

    private fun rewriteInjuriesThenGenerate(original: String, answers: List<Pair<String, String>>) {
        if (answers.all { it.second.isBlank() }) { advanceToGeneration(); return }
        val busy = showBusyDialog("Updating your injury description…")
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.rewriteInjury(original, answers)
            runCatching { busy.dismiss() }
            if (_binding == null) return@launch
            result.onSuccess { rewritten ->
                if (rewritten.isNotBlank() && rewritten != original) {
                    // Rewrite the whole box (not append), and show the user what it became.
                    viewModel.prefs.injuries = rewritten
                    binding.etWizardInjuries.setText(rewritten)
                    Snackbar.make(binding.root, "Updated your injury description", Snackbar.LENGTH_SHORT).show()
                }
                advanceToGeneration()
            }.onFailure { advanceToGeneration() }
        }
    }

    private fun showBusyDialog(message: String): androidx.appcompat.app.AlertDialog {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = (22 * d).toInt()
            setPadding(pad, pad, pad, pad)
        }
        row.addView(ProgressBar(ctx).apply {
            val sz = (28 * d).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz)
        })
        row.addView(TextView(ctx).apply {
            text = message
            setPadding((16 * d).toInt(), 0, 0, 0)
        })
        return MaterialAlertDialogBuilder(ctx).setView(row).setCancelable(false).show()
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
