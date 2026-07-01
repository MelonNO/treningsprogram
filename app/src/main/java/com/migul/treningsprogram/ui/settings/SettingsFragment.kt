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

        // S8 fix: guard every navigate() call against rapid double-taps (two taps before the
        // back-stack updates throw IllegalArgumentException "action not found from destination").
        // The guard checks that we are still on settingsFragment before navigating away.
        binding.rowTrainingProfile.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment)
                findNavController().navigate(R.id.action_settings_to_training)
        }
        binding.rowExerciseLibrary.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment)
                findNavController().navigate(R.id.action_settings_to_library)
        }
        binding.rowAiProgram.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment)
                findNavController().navigate(R.id.action_settings_to_ai)
        }
        binding.rowBackup.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment)
                findNavController().navigate(R.id.action_settings_to_backup)
        }
        // Item 4: new App Settings row (day boundary + auto-rebalance).
        binding.rowAppSettings.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment)
                findNavController().navigate(R.id.action_settings_to_app_settings)
        }
        binding.rowAbout.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment)
                findNavController().navigate(R.id.action_settings_to_about)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
