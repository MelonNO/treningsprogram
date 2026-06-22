package com.migul.treningsprogram.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.databinding.FragmentSettingsAiBinding
import com.migul.treningsprogram.ui.onboarding.OnboardingBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsAiFragment : Fragment() {

    private var _binding: FragmentSettingsAiBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    private val goals = listOf("Strength", "Hypertrophy", "Endurance", "Weight Loss")
    private val experiences = listOf("Beginner", "Intermediate", "Advanced")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = viewModel.prefs
        binding.etApiKey.setText(prefs.apiKey)

        binding.btnSaveApiKey.setOnClickListener {
            prefs.apiKey = binding.etApiKey.text.toString().trim()
            Snackbar.make(binding.root, "Saved", Snackbar.LENGTH_SHORT).show()
        }

        setFragmentResultListener(OnboardingBottomSheet.RESULT_KEY) { _, bundle ->
            val context = bundle.getString(OnboardingBottomSheet.RESULT_CONTEXT, "")
            val days = prefs.daysPerWeek
            val duration = prefs.sessionDurationMinutes
            viewModel.generateProgramWithOnboarding(
                onboardingContext = context,
                daysPerWeek = days,
                goal = prefs.fitnessGoal,
                experience = prefs.experienceLevel,
                sessionDurationMinutes = duration
            )
        }

        binding.btnGenerateNow.setOnClickListener {
            val apiKey = binding.etApiKey.text.toString().trim()
            if (apiKey.isBlank()) {
                Snackbar.make(binding.root, "Enter your Claude API key first.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.apiKey = apiKey
            if (!prefs.hasCompletedOnboarding) {
                OnboardingBottomSheet.newInstance(
                    goal = prefs.fitnessGoal,
                    experience = prefs.experienceLevel
                ).show(childFragmentManager, OnboardingBottomSheet.RESULT_KEY)
            } else {
                viewModel.generateProgram(
                    daysPerWeek = prefs.daysPerWeek,
                    goal = prefs.fitnessGoal,
                    experience = prefs.experienceLevel,
                    sessionDurationMinutes = prefs.sessionDurationMinutes
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                            binding.tvGenerateStatus.setTextColor(when {
                                status.startsWith("Program rejected") -> errorColor
                                status.startsWith("New program") || status.startsWith("Program generated") -> successColor
                                else -> defaultColor
                            })
                        } else {
                            binding.tvGenerateStatus.visibility = View.GONE
                        }
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
