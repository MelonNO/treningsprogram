package com.migul.treningsprogram.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.tabs.TabLayoutMediator
import com.migul.treningsprogram.databinding.FragmentHistoryBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val recapTarget: RecapTargetViewModel by activityViewModels()

    companion object { const val RECAP_TAB = 0 }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewPager.adapter = HistoryPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Recap"
                1 -> "Stats"
                2 -> "Progress"
                else -> "History"
            }
        }.attach()
    }

    override fun onResume() {
        super.onResume()
        // An entry point (Home button, completion modal, History row) may have asked
        // us to open the Recap tab for a specific session.
        if (recapTarget.consumeOpenRequest()) {
            binding.viewPager.currentItem = RECAP_TAB
        }
    }

    /** Open the Recap tab for [sessionId] (null = latest). */
    fun openRecap(sessionId: Long?) {
        recapTarget.request(sessionId)
        _binding?.viewPager?.currentItem = RECAP_TAB
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
