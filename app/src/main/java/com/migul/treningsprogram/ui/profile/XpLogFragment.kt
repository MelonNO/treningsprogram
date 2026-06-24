package com.migul.treningsprogram.ui.profile

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
import com.migul.treningsprogram.databinding.FragmentXpLogBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * U2: XP log — a scrollable history of XP-earning events (newest first). Reached by tapping the
 * XP bar. Shows a sensible empty state when no event has been recorded yet (fresh install, or
 * before the first workout completes after this feature shipped).
 */
@AndroidEntryPoint
class XpLogFragment : Fragment() {

    private var _binding: FragmentXpLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: XpLogViewModel by viewModels()

    private val adapter = XpLogAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentXpLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvXpEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvXpEvents.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { list ->
                    adapter.submitList(list)
                    val empty = list.isEmpty()
                    binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.rvXpEvents.visibility = if (empty) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvXpEvents.adapter = null
        _binding = null
    }
}
