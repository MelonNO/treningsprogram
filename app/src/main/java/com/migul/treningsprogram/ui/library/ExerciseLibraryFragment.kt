package com.migul.treningsprogram.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.migul.treningsprogram.R
import com.migul.treningsprogram.databinding.FragmentExerciseLibraryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * BROWSE-ONLY exercise library (E3). Lists the FULL bundled catalog with search + muscle +
 * equipment filters. Tapping a row opens [ExerciseDetailFragment]. No "add to plan" actions.
 */
@AndroidEntryPoint
class ExerciseLibraryFragment : Fragment() {

    private var _binding: FragmentExerciseLibraryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExerciseLibraryViewModel by viewModels()

    private lateinit var adapter: ExerciseLibraryAdapter

    private val anyMuscle = "All muscles"
    private val anyEquipment = "All equipment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExerciseLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ExerciseLibraryAdapter { entry ->
            val args = Bundle().apply { putString("exerciseId", entry.id) }
            findNavController().navigate(R.id.action_library_to_detail, args)
        }
        binding.rvExercises.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExercises.adapter = adapter

        setupSearch()
        setupFilters()
        observeResults()
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.setQuery(text?.toString().orEmpty())
        }
    }

    private fun setupFilters() {
        val muscleItems = listOf(anyMuscle) + viewModel.muscleGroups
        binding.ddMuscle.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, muscleItems)
        )
        binding.ddMuscle.setText(anyMuscle, false)
        binding.ddMuscle.setOnItemClickListener { _, _, position, _ ->
            viewModel.setMuscle(if (position == 0) null else muscleItems[position])
        }

        val equipItems = listOf(anyEquipment) + viewModel.equipmentOptions
        binding.ddEquipment.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, equipItems)
        )
        binding.ddEquipment.setText(anyEquipment, false)
        binding.ddEquipment.setOnItemClickListener { _, _, position, _ ->
            viewModel.setEquipment(if (position == 0) null else equipItems[position])
        }
    }

    private fun observeResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.results.collect { results ->
                    adapter.submitList(results)
                    binding.tvEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvCount.text = "${results.size} exercises"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvExercises.adapter = null
        _binding = null
    }
}
