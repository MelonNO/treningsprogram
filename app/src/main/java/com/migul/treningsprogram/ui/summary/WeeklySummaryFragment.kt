package com.migul.treningsprogram.ui.summary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.migul.treningsprogram.databinding.FragmentWeeklySummaryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * B1: scrollable history of automatic weekly coach summaries (newest first). Shows a sensible
 * empty state when no summary has been generated yet.
 */
@AndroidEntryPoint
class WeeklySummaryFragment : Fragment() {

    private var _binding: FragmentWeeklySummaryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WeeklySummaryViewModel by viewModels()

    private val adapter = WeeklySummaryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeeklySummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvSummaries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSummaries.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.summaries.collect { list ->
                    adapter.submitList(list)
                    val empty = list.isEmpty()
                    binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.rvSummaries.visibility = if (empty) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvSummaries.adapter = null
        _binding = null
    }
}
