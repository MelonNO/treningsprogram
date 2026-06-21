package com.migul.treningsprogram.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.os.bundleOf
import androidx.core.view.setMargins
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.migul.treningsprogram.databinding.BottomSheetOnboardingBinding
import com.migul.treningsprogram.domain.model.OnboardingQuestion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OnboardingBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by viewModels()

    // Parallel list tracking the answer view for each question
    private val questionViews = mutableListOf<Pair<OnboardingQuestion, View>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expand fully so all questions are visible
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)
            ?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        val goal = arguments?.getString(ARG_GOAL) ?: "Hypertrophy"
        val experience = arguments?.getString(ARG_EXPERIENCE) ?: "Intermediate"

        viewModel.loadQuestions(goal, experience)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isLoading.collect { loading -> updateLoadingState(loading) } }
                launch { viewModel.questions.collect { qs -> if (qs.isNotEmpty()) buildQuestionViews(qs) } }
                launch { viewModel.error.collect { err ->
                    if (err != null) {
                        binding.layoutLoading.visibility = View.GONE
                        binding.tvError.visibility = View.VISIBLE
                        binding.tvError.text = "Could not load questions: $err"
                        binding.btnSkip.visibility = View.VISIBLE
                    }
                }}
            }
        }

        binding.btnGenerate.setOnClickListener { submitAnswers() }
        binding.btnSkip.setOnClickListener { deliverResult("") }
    }

    private fun updateLoadingState(loading: Boolean) {
        binding.layoutLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun buildQuestionViews(questions: List<OnboardingQuestion>) {
        binding.layoutLoading.visibility = View.GONE
        binding.containerQuestions.visibility = View.VISIBLE
        binding.btnGenerate.visibility = View.VISIBLE
        binding.btnSkip.visibility = View.VISIBLE
        binding.containerQuestions.removeAllViews()
        questionViews.clear()

        val ctx = requireContext()
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp16 = dp8 * 2

        questions.forEachIndexed { index, q ->
            val sectionLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp16 }
            }

            val questionLabel = android.widget.TextView(ctx).apply {
                text = "${index + 1}. ${q.question}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp8 }
            }
            sectionLayout.addView(questionLabel)

            val answerView: View = when (q.type) {
                "choice" -> {
                    RadioGroup(ctx).apply {
                        orientation = RadioGroup.VERTICAL
                        q.options.forEach { option ->
                            addView(RadioButton(ctx).apply { text = option })
                        }
                    }
                }
                else -> {
                    TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                        hint = "Your answer"
                        addView(TextInputEditText(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        })
                    }
                }
            }
            sectionLayout.addView(answerView)
            binding.containerQuestions.addView(sectionLayout)
            questionViews.add(q to answerView)
        }
    }

    private fun submitAnswers() {
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
        deliverResult(sb.toString().trim())
    }

    private fun deliverResult(context: String) {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_CONTEXT to context))
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "onboarding_result"
        const val RESULT_CONTEXT = "onboarding_context"
        private const val ARG_GOAL = "goal"
        private const val ARG_EXPERIENCE = "experience"

        fun newInstance(goal: String, experience: String) = OnboardingBottomSheet().apply {
            arguments = bundleOf(ARG_GOAL to goal, ARG_EXPERIENCE to experience)
        }
    }
}
