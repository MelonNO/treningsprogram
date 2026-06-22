package com.migul.treningsprogram.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.migul.treningsprogram.R
import com.migul.treningsprogram.databinding.FragmentSettingsDebugBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsDebugFragment : Fragment() {

    private var _binding: FragmentSettingsDebugBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsDebugBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rowPromptLog.setOnClickListener {
            findNavController().navigate(R.id.action_settings_debug_to_prompt_log)
        }
        binding.rowRejectionLog.setOnClickListener {
            findNavController().navigate(R.id.action_settings_debug_to_rejection_log)
        }
        binding.rowUnrecognized.setOnClickListener {
            findNavController().navigate(R.id.action_settings_debug_to_unrecognized)
        }
        binding.rowCrashLog.setOnClickListener {
            findNavController().navigate(R.id.action_settings_debug_to_crash_log)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
