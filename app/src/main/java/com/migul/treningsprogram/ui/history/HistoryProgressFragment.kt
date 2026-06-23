package com.migul.treningsprogram.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.migul.treningsprogram.data.db.dao.ExercisePrWithDate
import com.migul.treningsprogram.databinding.FragmentHistoryProgressBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
@AndroidEntryPoint
class HistoryProgressFragment : Fragment() {

    private var _binding: FragmentHistoryProgressBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load exercise names into autocomplete
        viewLifecycleOwner.lifecycleScope.launch {
            val names = viewModel.getExerciseNames()
            if (_binding == null) return@launch
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
            binding.acExercise.setAdapter(adapter)
            binding.acExercise.threshold = 1
        }

        binding.acExercise.setOnItemClickListener { _, _, _, _ ->
            viewModel.selectedExercise.value = binding.acExercise.text.toString()
        }

        // Also trigger on text commit
        binding.acExercise.setOnEditorActionListener { _, _, _ ->
            viewModel.selectedExercise.value = binding.acExercise.text.toString()
            false
        }

        // Time window chips
        binding.cgTime.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.timeWindowMonths.value = when {
                checkedIds.contains(com.migul.treningsprogram.R.id.chip_1m) -> 1
                checkedIds.contains(com.migul.treningsprogram.R.id.chip_3m) -> 3
                checkedIds.contains(com.migul.treningsprogram.R.id.chip_6m) -> 6
                else -> 0
            }
        }

        // Observe strength history
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.strengthHistory.collect { history ->
                    binding.chartStrength.setData(history.map {
                        StrengthChartView.Entry(it.dateMs, it.maxWeight)
                    })
                    // Compute e1RM from best set with reps < 20
                    val best = history.filter { it.bestReps < 20 }.maxByOrNull { it.maxWeight }
                    if (best != null) {
                        val e1rm = best.maxWeight * (1 + best.bestReps / 30.0)
                        binding.tvE1rm.text = "Estimated 1RM: ~${e1rm.toInt()} kg"
                        binding.tvE1rm.isVisible = true
                        binding.tvE1rmDisclaimer.isVisible = true
                    } else {
                        binding.tvE1rm.isVisible = false
                        binding.tvE1rmDisclaimer.isVisible = false
                    }
                }
            }
        }

        // Observe PRs reactively so deletions in the Log tab reflect immediately
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.prs.collect { prs -> renderPRs(prs.take(10)) }
            }
        }

    }

    private fun renderPRs(prs: List<ExercisePrWithDate>) {
        binding.layoutPrs.removeAllViews()
        if (prs.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "No records yet. Complete workouts to see PRs!"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            }
            binding.layoutPrs.addView(tv)
        } else {
            prs.forEach { pr ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val p = (4 * resources.displayMetrics.density).toInt()
                    setPadding(0, p, 0, p)
                }
                val tvName = TextView(requireContext()).apply {
                    text = pr.exerciseName
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tvWeight = TextView(requireContext()).apply {
                    text = "${formatWeight(pr.maxWeight)} kg"
                    textSize = 13f
                    setTextColor(android.graphics.Color.parseColor("#7C67F5"))
                }
                row.addView(tvName)
                row.addView(tvWeight)
                binding.layoutPrs.addView(row)
            }
        }
    }

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
