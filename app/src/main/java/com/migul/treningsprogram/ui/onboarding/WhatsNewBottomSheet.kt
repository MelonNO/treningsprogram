package com.migul.treningsprogram.ui.onboarding

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.migul.treningsprogram.databinding.BottomSheetWhatsNewBinding
import com.migul.treningsprogram.ui.common.Changelog

/**
 * F6 — post-update "What's new" sheet. Shows the changelog entries newer than
 * the version the user last saw (passed as an argument so the sheet itself
 * stays free of prefs logic — MainActivity owns the gate + the version stamp).
 */
class WhatsNewBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetWhatsNewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetWhatsNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val since = requireArguments().getInt(ARG_SINCE)
        val entries = Changelog.entriesSince(since)
        val density = resources.displayMetrics.density

        entries.forEach { entry ->
            binding.layoutWhatsNewEntries.addView(TextView(requireContext()).apply {
                text = "v${entry.versionName}"
                textSize = 15f
                setTextColor(Color.parseColor("#7FE9E1"))
                letterSpacing = -0.01f
                setPadding(0, 0, 0, (6 * density).toInt())
            })
            entry.highlights.forEach { line ->
                binding.layoutWhatsNewEntries.addView(TextView(requireContext()).apply {
                    text = "•  $line"
                    textSize = 14f
                    setTextColor(Color.parseColor("#BBC7C6"))
                    setLineSpacing(2 * density, 1f)
                    setPadding(0, 0, 0, (6 * density).toInt())
                })
            }
            binding.layoutWhatsNewEntries.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (10 * density).toInt()
                )
            })
        }

        binding.btnWhatsNewDone.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "whats_new"
        private const val ARG_SINCE = "sinceVersionCode"

        fun newInstance(sinceVersionCode: Int) = WhatsNewBottomSheet().apply {
            arguments = Bundle().apply { putInt(ARG_SINCE, sinceVersionCode) }
        }
    }
}
