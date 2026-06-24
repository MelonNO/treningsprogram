package com.migul.treningsprogram.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.migul.treningsprogram.R
import com.migul.treningsprogram.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowTrainingProfile.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_training)
        }
        binding.rowExerciseLibrary.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_library)
        }
        binding.rowCoachSummary.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_weekly_summary)
        }
        binding.rowAiProgram.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_ai)
        }
        binding.rowDebug.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_debug)
        }
        binding.rowBackup.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_backup)
        }
        binding.rowAbout.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_about)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
