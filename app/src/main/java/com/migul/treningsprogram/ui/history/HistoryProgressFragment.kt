package com.migul.treningsprogram.ui.history

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.migul.treningsprogram.R
import com.migul.treningsprogram.databinding.FragmentHistoryProgressBinding
import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.OneRmTrend
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class HistoryProgressFragment : Fragment() {

    private var _binding: FragmentHistoryProgressBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels({ requireParentFragment() })

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

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

        // IA (UX1): open the full per-exercise Trends screen — the same destination the Recap
        // deltas open — so there is one consistent place to drill into an exercise. Uses the same
        // currentDestination guard as HistoryRecap's openTrends to avoid a double-tap double-nav.
        binding.btnViewTrends.setOnClickListener {
            val name = viewModel.selectedExercise.value
            if (name.isNotBlank() &&
                findNavController().currentDestination?.id == R.id.historyFragment
            ) {
                findNavController().navigate(
                    R.id.recapTrendsFragment,
                    bundleOf("exerciseName" to name, "sessionDateMs" to System.currentTimeMillis())
                )
            }
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

        // Observe strength history — drives both the max-weight chart and the C1 PR timeline.
        // The history already excludes warm-up sets (WorkoutSetDao.getStrengthHistory filters
        // isWarmup=0), so OneRmTrend.prTimeline receives working-set summaries only.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.strengthHistory.collect { history ->
                    // First-run / no-selection: tell the user to pick an exercise rather than
                    // leaving the chart's generic "Not enough data yet" looking broken (UX1).
                    val hasSelection = viewModel.selectedExercise.value.isNotBlank()
                    binding.tvStrengthHint.isVisible = !hasSelection
                    binding.btnViewTrends.isVisible = hasSelection
                    binding.chartStrength.setData(history.map {
                        StrengthChartView.Entry(it.dateMs, it.maxWeight)
                    }, "kg")
                    // Compute e1RM from the set with the highest estimated 1RM (reps in 1..19).
                    // maxByOrNull on Epley correctly handles double-progression: a lighter weight
                    // done for more reps can yield a higher e1RM than a heavier single-rep attempt.
                    val best = history.filter { it.bestReps in 1 until 20 }
                        .maxByOrNull { Epley.estimate(it.maxWeight, it.bestReps) }
                    if (best != null) {
                        val e1rm = Epley.estimate(best.maxWeight, best.bestReps)
                        binding.tvE1rm.text = "Estimated 1RM: ~${e1rm.toInt()} kg"
                        binding.tvE1rm.isVisible = true
                        binding.tvE1rmDisclaimer.isVisible = true
                    } else {
                        binding.tvE1rm.isVisible = false
                        binding.tvE1rmDisclaimer.isVisible = false
                    }

                    // C1 PR timeline — replaces the legacy max-weight PR widget.
                    // Warm-ups are already excluded upstream; OneRmTrend does not re-filter.
                    renderPrTimeline(history, viewModel.selectedExercise.value)
                }
            }
        }

        // Observe stalled lifts (B3) — show a named plateau alert with a concrete suggestion
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stalledLifts.collect { stalled -> renderStalled(stalled) }
            }
        }
    }

    private fun renderStalled(stalled: List<Pair<String, String>>) {
        binding.cardStalled.isVisible = stalled.isNotEmpty()
        binding.layoutStalled.removeAllViews()
        stalled.forEach { (name, suggestion) ->
            val column = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val p = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, p, 0, p)
            }
            val tvName = TextView(requireContext()).apply {
                text = name
                textSize = 14f
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            }
            val tvSuggestion = TextView(requireContext()).apply {
                text = suggestion
                textSize = 13f
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            }
            column.addView(tvName)
            column.addView(tvSuggestion)
            binding.layoutStalled.addView(column)
        }
    }

    /**
     * Renders the C1 estimated-1RM PR timeline for the currently selected exercise.
     * This is the SINGLE source of PR truth in this area (F2); the old max-weight PR widget
     * that counted warm-ups has been retired.
     *
     * When no exercise is selected the card shows a prompt. When an exercise is selected but
     * has no qualifying history (e.g. only warm-ups or 0-rep sets), the empty state is shown.
     * Most-recent PR is shown first.
     */
    private fun renderPrTimeline(
        history: List<com.migul.treningsprogram.data.db.dao.StrengthPoint>,
        exerciseName: String
    ) {
        if (_binding == null) return
        binding.layoutPrs.removeAllViews()

        if (exerciseName.isBlank()) {
            binding.layoutPrs.addView(TextView(requireContext()).apply {
                text = "Select an exercise above to see its PR history."
                textSize = 13f
                setTextColor(Color.parseColor("#9A9AB0"))
            })
            return
        }

        val prs = OneRmTrend.prTimeline(history)
        if (prs.isEmpty()) {
            binding.layoutPrs.addView(TextView(requireContext()).apply {
                text = "No PR history yet for $exerciseName — log a few working sets to build it."
                textSize = 13f
                setTextColor(Color.parseColor("#9A9AB0"))
            })
            return
        }

        // Most recent PR first.
        prs.asReversed().forEach { pr ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val p = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, p, 0, p)
            }
            row.addView(TextView(requireContext()).apply {
                text = dateFmt.format(Date(pr.dateMs))
                textSize = 13f
                setTextColor(Color.parseColor("#9A9AB0"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(requireContext()).apply {
                text = "${formatWeight(pr.weightKg)} kg × ${pr.reps}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#7C67F5"))
            })
            binding.layoutPrs.addView(row)
        }
    }

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
