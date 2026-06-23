package com.migul.treningsprogram.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.migul.treningsprogram.databinding.FragmentSettingsAboutBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsAboutFragment : Fragment() {

    private var _binding: FragmentSettingsAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val version = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: "—"
        } catch (_: Exception) { "—" }
        binding.tvVersion.text = "v$version"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
