package com.migul.treningsprogram.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.migul.treningsprogram.R
import com.migul.treningsprogram.databinding.FragmentSetupWizardBinding
import com.migul.treningsprogram.domain.model.OnboardingQuestion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SetupWizardFragment : Fragment() {

    private var _binding: FragmentSetupWizardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SetupWizardViewModel by viewModels()

    private var currentStep = 0
    private val LAST_INPUT_STEP = 4  // step 5 is auto-generating
    private val TOTAL_STEPS = 5      // steps 0-4 shown in progress bar

    // Collected user values
    private var selectedGoal = "Hypertrophy"
    private var selectedExperience = "Intermediate"
    private var selectedDays = 4
    private var selectedDuration = 60

    // Tracks question answer views for step 4
    private val questionViews = mutableListOf<Pair<OnboardingQuestion, View>>()

    private val stepTitles = listOf(
        "Fitness Goal",
        "Training Schedule",
        "Preferences",
        "Connect Claude",
        "Personalisation"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSetupWizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateStepUI()

        // Goal chips
        binding.chipGroupGoal.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedGoal = when (checkedIds.firstOrNull()) {
                R.id.chip_goal_strength    -> "Strength"
                R.id.chip_goal_endurance   -> "Endurance"
                R.id.chip_goal_weightloss  -> "Weight Loss"
                else                       -> "Hypertrophy"
            }
        }

        // Experience chips
        binding.chipGroupExperience.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedExperience = when (checkedIds.firstOrNull()) {
                R.id.chip_exp_beginner  -> "Beginner"
                R.id.chip_exp_advanced  -> "Advanced"
                else                    -> "Intermediate"
            }
        }

        // Days chips
        binding.chipGroupDays.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedDays = when (checkedIds.firstOrNull()) {
                R.id.chip_days_2 -> 2
                R.id.chip_days_3 -> 3
                R.id.chip_days_5 -> 5
                R.id.chip_days_6 -> 6
                else             -> 4
            }
        }

        // Duration chips
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

        binding.btnWizardBack.setOnClickListener { goBack() }

        binding.btnWizardNext.setOnClickListener { advance() }

        binding.tvWizardSkip.setOnClickListener {
            skipToGenerate()
        }

        binding.btnGoHome.setOnClickListener {
            findNavController().navigate(R.id.homeFragment)
        }

        binding.btnRetryGenerate.setOnClickListener {
            binding.layoutGenError.visibility = View.GONE
            binding.layoutGenerating.visibility = View.VISIBLE
            triggerGeneration()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.isLoadingQuestions.collect { loading ->
                        if (currentStep != 4) return@collect
                        binding.layoutQuestionsLoading.visibility = if (loading) View.VISIBLE else View.GONE
                        if (!loading) binding.btnWizardNext.isEnabled = viewModel.questions.value.isNotEmpty()
                    }
                }

                launch {
                    viewModel.questions.collect { questions ->
                        if (questions.isNotEmpty() && currentStep == 4) {
                            binding.layoutQuestionsLoading.visibility = View.GONE
                            buildQuestionViews(questions)
                            binding.btnWizardNext.isEnabled = true
                            binding.btnWizardNext.text = "Generate My Program"
                        }
                    }
                }

                launch {
                    viewModel.questionsError.collect { err ->
                        if (err != null && currentStep == 4) {
                            binding.layoutQuestionsLoading.visibility = View.GONE
                            binding.containerWizardQuestions.visibility = View.GONE
                            binding.btnWizardNext.isEnabled = true
                            binding.btnWizardNext.text = "Generate Without Extra Context"
                        }
                    }
                }

                launch {
                    viewModel.generationDone.collect { done ->
                        if (done) {
                            binding.layoutGenerating.visibility = View.GONE
                            binding.layoutSuccess.visibility = View.VISIBLE
                            binding.layoutWizardActions.visibility = View.GONE
                            val attempts = viewModel.attemptCount.value
                            binding.tvSuccessDetail.text = if (attempts > 1)
                                "Your plan was generated in $attempts attempts.\nCheck the Program tab to see your week."
                            else
                                "Check the Program tab to see your week."
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
            2 -> nextStep()
            3 -> {
                val apiKey = binding.etWizardApiKey.text?.toString()?.trim() ?: ""
                if (apiKey.isBlank()) {
                    Snackbar.make(binding.root, "Please enter your Claude API key.", Snackbar.LENGTH_SHORT).show()
                    return
                }
                nextStep()
                // Auto-load questions when entering step 4
                viewModel.loadQuestions(selectedGoal, selectedExperience)
                binding.btnWizardNext.isEnabled = false
            }
            4 -> {
                // Questions answered → move to generating step and trigger generation
                nextStep()
                triggerGeneration()
            }
        }
    }

    private fun nextStep() {
        currentStep++
        binding.stepFlipper.displayedChild = currentStep
        updateStepUI()
    }

    private fun goBack() {
        if (currentStep == 0) {
            findNavController().popBackStack()
            return
        }
        currentStep--
        binding.stepFlipper.displayedChild = currentStep
        updateStepUI()
    }

    private fun skipToGenerate() {
        // Skip straight to generating with whatever we have so far
        currentStep = 5
        binding.stepFlipper.displayedChild = 5
        updateStepUI()
        triggerGeneration()
    }

    private fun triggerGeneration() {
        val apiKey = binding.etWizardApiKey.text?.toString()?.trim() ?: viewModel.prefs.apiKey
        viewModel.generateProgram(
            onboardingContext = collectAnswers(),
            goal = selectedGoal,
            experience = selectedExperience,
            daysPerWeek = selectedDays,
            sessionDurationMinutes = selectedDuration,
            separateCardioDays = binding.switchCardioWizard.isChecked,
            apiKey = apiKey
        )
    }

    private fun collectAnswers(): String {
        if (questionViews.isEmpty()) return ""
        val sb = StringBuilder()
        questionViews.forEach { (q, answerView) ->
            val answer = when (q.type) {
                "choice" -> {
                    val rg = answerView as RadioGroup
                    rg.findViewById<RadioButton>(rg.checkedRadioButtonId)?.text?.toString() ?: ""
                }
                else -> {
                    val til = answerView as TextInputLayout
                    til.editText?.text?.toString()?.trim() ?: ""
                }
            }
            if (answer.isNotBlank()) sb.appendLine("Q: ${q.question}\nA: $answer")
        }
        return sb.toString().trim()
    }

    private fun buildQuestionViews(questions: List<OnboardingQuestion>) {
        val container = binding.containerWizardQuestions
        container.removeAllViews()
        questionViews.clear()

        val ctx = requireContext()
        val dp8  = (8  * resources.displayMetrics.density).toInt()
        val dp16 = (16 * resources.displayMetrics.density).toInt()

        questions.forEachIndexed { index, q ->
            val section = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp16 }
            }

            val label = android.widget.TextView(ctx).apply {
                text = "${index + 1}. ${q.question}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp8 }
            }
            section.addView(label)

            val answerView: View = when (q.type) {
                "choice" -> RadioGroup(ctx).apply {
                    orientation = RadioGroup.VERTICAL
                    q.options.forEach { opt -> addView(RadioButton(ctx).apply { text = opt }) }
                }
                else -> com.google.android.material.textfield.TextInputLayout(
                    ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle
                ).apply {
                    hint = "Your answer"
                    addView(com.google.android.material.textfield.TextInputEditText(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    })
                }
            }
            section.addView(answerView)
            container.addView(section)
            questionViews.add(q to answerView)
        }

        container.visibility = View.VISIBLE
    }

    private fun updateStepUI() {
        val isGeneratingStep = currentStep >= TOTAL_STEPS

        // Header visibility
        binding.progressWizard.visibility = if (isGeneratingStep) View.GONE else View.VISIBLE
        binding.tvWizardSkip.visibility   = if (isGeneratingStep || currentStep == LAST_INPUT_STEP) View.GONE else View.VISIBLE
        binding.layoutWizardActions.visibility = if (isGeneratingStep) View.GONE else View.VISIBLE

        if (!isGeneratingStep) {
            // Step label and title
            binding.tvWizardStepLabel.text = "STEP ${currentStep + 1} OF $TOTAL_STEPS"
            binding.tvWizardStepTitle.text = stepTitles.getOrElse(currentStep) { "" }

            // Progress bar
            binding.progressWizard.max = TOTAL_STEPS
            binding.progressWizard.progress = currentStep + 1

            // Back button
            binding.btnWizardBack.visibility = if (currentStep == 0) View.INVISIBLE else View.VISIBLE

            // Next button label
            binding.btnWizardNext.text = when (currentStep) {
                LAST_INPUT_STEP - 1 -> "Next"   // step 3 → load questions
                LAST_INPUT_STEP     -> if (viewModel.questions.value.isEmpty() && viewModel.isLoadingQuestions.value.not()) "Generate My Program" else "Generate My Program"
                else                -> "Next"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
