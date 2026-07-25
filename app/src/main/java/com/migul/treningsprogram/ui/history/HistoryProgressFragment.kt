package com.migul.treningsprogram.ui.history

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.datepicker.MaterialDatePicker
import com.migul.treningsprogram.databinding.FragmentHistoryProgressBinding
import com.migul.treningsprogram.domain.DataScreenEmptyState
import com.migul.treningsprogram.domain.DateRangeFilter
import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.OneRmTrend
import com.migul.treningsprogram.domain.RepsProgress
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

        // Load exercise names into autocomplete, ordered most-trained-first (B03). The custom
        // adapter preserves that order while the user types to filter (it does not re-alphabetize).
        viewLifecycleOwner.lifecycleScope.launch {
            val names = viewModel.getExerciseNames()
            if (_binding == null) return@launch
            val adapter = OrderedExerciseAdapter(requireContext(), names)
            binding.acExercise.setAdapter(adapter)
            binding.acExercise.threshold = 1

            // B07 whole-screen empty state: when nothing has ever been logged, show the single
            // top-level line and hide the picker/charts. Once any exercise exists, show the picker
            // (data cards still appear per-field as an exercise is selected).
            val screenEmpty = DataScreenEmptyState.isProgressEmpty(names.size)
            binding.tvProgressEmpty.isVisible = screenEmpty
            binding.cardExerciseSelector.isVisible = !screenEmpty
            // Stage-3 item 2: with data but no exercise picked yet, hint at the chart/cards to
            // come with a placeholder skeleton (the true first-run state keeps its copy above).
            if (!screenEmpty && viewModel.selectedExercise.value.isBlank()) {
                com.migul.treningsprogram.ui.common.Skeleton.show(binding.skeletonProgress)
            }
        }

        binding.acExercise.setOnItemClickListener { _, _, _, _ ->
            com.migul.treningsprogram.ui.common.Skeleton.hide(binding.skeletonProgress)
            viewModel.selectedExercise.value = binding.acExercise.text.toString()
        }

        // Also trigger on text commit
        binding.acExercise.setOnEditorActionListener { _, _, _ ->
            com.migul.treningsprogram.ui.common.Skeleton.hide(binding.skeletonProgress)
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

        // Stage-3 item 13: calendar start/end range picker (replaces the 1M/3M/6M/All chips) —
        // identical look/behavior to the History sub-tab's control (item 12).
        binding.btnProgressRange.setOnClickListener {
            val picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Filter progress by date")
                .build()
            picker.addOnPositiveButtonClickListener { sel ->
                val start = sel.first
                val end = sel.second
                if (start != null && end != null) {
                    viewModel.progressDateRange.value = DateRangeFilter.fromPickerUtc(start, end)
                }
            }
            picker.show(parentFragmentManager, "progress_range_picker")
        }
        binding.btnProgressRangeClear.setOnClickListener { viewModel.progressDateRange.value = null }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.progressDateRange.collect { range ->
                    binding.btnProgressRange.text = DateRangeFilter.label(range)
                    binding.btnProgressRangeClear.isVisible = range != null
                }
            }
        }

        // R3: body-weight chart — independent of the exercise selection; hidden until >= 2
        // weigh-ins exist in the window. Raw weigh-ins + the smoothed WeightTrend overlay + the
        // trend readout. QoL item 09: the chart (and its trend line/readout) now follows the same
        // progressDateRange as the strength/reps charts (null = all-time, clearing restores it).
        // The relative-strength card below DELIBERATELY keeps the unfiltered measurement stream —
        // changing it was explicitly ruled out of scope by the brief.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    viewModel.bodyMeasurements, viewModel.progressDateRange
                ) { measurements, range ->
                    DateRangeFilter.filter(measurements, range) { it.dateMs }
                }.collect { windowed ->
                    val sorted = windowed.sortedBy { it.dateMs }
                    val show = sorted.size >= 2
                    binding.cardBodyWeight.isVisible = show
                    if (!show) return@collect
                    binding.chartBodyWeight.setData(
                        sorted.map { BodyWeightChartView.Entry(it.dateMs, it.weightKg) },
                        com.migul.treningsprogram.domain.WeightTrend.smoothedSeries(sorted)
                            .map { (ms, kg) -> BodyWeightChartView.Entry(ms, kg) }
                    )
                    val line = com.migul.treningsprogram.domain.WeightTrend.homeLine(sorted)
                    binding.tvBwTrend.isVisible = line != null
                    binding.tvBwTrend.text = line ?: ""
                }
            }
        }

        // Observe strength history — drives both the max-weight chart and the C1 PR timeline.
        // The history already excludes warm-up sets (WorkoutSetDao.getStrengthHistory filters
        // isWarmup=0), so OneRmTrend.prTimeline receives working-set summaries only.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    viewModel.strengthHistory, viewModel.goals
                ) { history, goals -> history to goals }.collect { (history, goals) ->
                    // B07: no per-field "select an exercise" copy. The strength card is hidden until
                    // the selected exercise actually has data; the chart inside needs >= 2 points to
                    // draw, so hide the chart view (not the whole card) at a single point — the e1RM
                    // line and "view trends" entry still make sense with one logged session.
                    val selected = viewModel.selectedExercise.value
                    val hasSelection = selected.isNotBlank()
                    val hasHistory = hasSelection && history.isNotEmpty()
                    binding.cardStrength.isVisible = hasHistory
                    binding.btnViewTrends.isVisible = hasHistory
                    binding.chartStrength.isVisible = history.size >= 2

                    // N5: this exercise's ACTIVE goal → gold target line on the weight chart
                    // (weight goals; e1RM goals draw on the Trends e1RM chart instead, where the
                    // scale matches) + progress readout + the manage entry point (A-G4).
                    val activeGoal = goals.firstOrNull {
                        it.status == com.migul.treningsprogram.data.db.entity.LiftGoal.STATUS_ACTIVE &&
                            it.exerciseName.equals(selected.trim(), ignoreCase = true)
                    }
                    val goalGuides = if (activeGoal != null && !activeGoal.isE1rm) listOf(
                        StrengthChartView.Guide(
                            activeGoal.targetWeightKg,
                            "Goal ${formatWeight(activeGoal.targetWeightKg)} kg",
                            extendRange = true, isGoal = true
                        )
                    ) else emptyList()

                    binding.chartStrength.setData(history.map {
                        StrengthChartView.Entry(it.dateMs, it.maxWeight)
                    }, "kg", guides = goalGuides)

                    binding.btnSetGoal.isVisible = hasHistory
                    binding.btnSetGoal.text = if (activeGoal == null) "Set goal" else "Edit goal"
                    binding.btnSetGoal.setOnClickListener {
                        if (hasSelection) showGoalDialog(selected.trim(), activeGoal)
                    }
                    if (activeGoal != null) {
                        val best = viewModel.goalCurrentBest(activeGoal)
                        val progress = com.migul.treningsprogram.domain.GoalProgress
                            .progressLine(best, activeGoal.targetWeightKg)
                        val flavor = com.migul.treningsprogram.domain.GoalProgress
                            .dateFlavor(activeGoal.targetDateMs)
                        val kind = if (activeGoal.isE1rm) " (est. 1RM)" else ""
                        binding.tvGoalProgress.isVisible = hasHistory
                        binding.tvGoalProgress.text =
                            "🎯 $progress$kind" + (flavor?.let { "  ·  $it" } ?: "")
                    } else {
                        binding.tvGoalProgress.isVisible = false
                    }
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

        // Stage-3 item 1: reps chart for bodyweight exercises. Shown when the selected exercise
        // has at least one all-bodyweight session (RepsProgress); for mixed histories the weight
        // chart above stays too, each labelled. Loaded lifts: hidden — exactly as today.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.repsHistory.collect { history ->
                    val hasSelection = viewModel.selectedExercise.value.isNotBlank()
                    val show = hasSelection && history.isNotEmpty() &&
                        RepsProgress.showRepsChart(history)
                    binding.cardReps.isVisible = show
                    if (show) {
                        binding.chartReps.setData(
                            history.map { StrengthChartView.Entry(it.dateMs, it.bestReps.toFloat()) },
                            "reps",
                            integerValues = true
                        )
                    }
                }
            }
        }

        // Observe stalled lifts (B3) — show a named plateau alert with a concrete suggestion
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stalledLifts.collect { stalled -> renderStalled(stalled) }
            }
        }

        // N3: relative strength — e1RM ÷ nearest weigh-in for the selected (weighted) lift.
        // Follows the SAME strengthHistory stream as the charts above, so the date-range filter
        // applies identically; weigh-ins come from the same store as the BW chart.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    viewModel.strengthHistory, viewModel.bodyMeasurements
                ) { history, measurements -> history to measurements }
                    .collect { (history, measurements) ->
                        renderRelativeStrength(history, measurements)
                    }
            }
        }
    }

    /**
     * N3 card states:
     *  - hidden: no selection, or the selection has no weighted history (A-R2), or no weigh-ins
     *    exist at all;
     *  - partial: weighted history + weigh-ins exist but < 2 matchable points → honest hint,
     *    no fabricated chart;
     *  - full: trend chart with chart-only milestone lines (A-R1) + the current-ratio readout.
     */
    private fun renderRelativeStrength(
        history: List<com.migul.treningsprogram.data.db.dao.StrengthPoint>,
        measurements: List<com.migul.treningsprogram.data.db.entity.BodyMeasurement>
    ) {
        if (_binding == null) return
        val hasSelection = viewModel.selectedExercise.value.isNotBlank()
        val weighted = history.any { it.maxWeight > 0f }
        if (!hasSelection || !weighted || measurements.isEmpty()) {
            binding.cardRelative.isVisible = false
            return
        }
        val points = com.migul.treningsprogram.domain.RelativeStrength.series(
            history,
            measurements.map {
                com.migul.treningsprogram.domain.RelativeStrength.WeighIn(it.dateMs, it.weightKg)
            }
        )
        binding.cardRelative.isVisible = true
        when {
            points.size >= 2 -> {
                binding.chartRelative.isVisible = true
                binding.tvRelativeHint.isVisible = false
                binding.chartRelative.setData(
                    points.map { StrengthChartView.Entry(it.dateMs, it.ratio) },
                    "×BW",
                    guides = com.migul.treningsprogram.domain.RelativeStrength
                        .milestonesInRange(points)
                        .map { StrengthChartView.Guide(it, "${formatWeight(it)}× BW") },
                    decimalPlaces = 2
                )
                binding.tvRelativeCurrent.isVisible = true
                binding.tvRelativeCurrent.text =
                    com.migul.treningsprogram.domain.RelativeStrength.currentLine(points)
            }
            points.size == 1 -> {
                binding.chartRelative.isVisible = false
                binding.tvRelativeCurrent.isVisible = true
                binding.tvRelativeCurrent.text =
                    com.migul.treningsprogram.domain.RelativeStrength.currentLine(points)
                binding.tvRelativeHint.isVisible = true
                binding.tvRelativeHint.text =
                    "More sessions with a weigh-in nearby will draw the trend."
            }
            else -> {
                binding.chartRelative.isVisible = false
                binding.tvRelativeCurrent.isVisible = false
                binding.tvRelativeHint.isVisible = true
                binding.tvRelativeHint.text =
                    "No weigh-ins near these sessions — log body weight on Home to unlock this view."
            }
        }
    }

    // ── N5: goal create / edit dialog (A-G4 — managed from the exercise's Progress context) ──

    private fun showGoalDialog(
        exerciseName: String,
        existing: com.migul.treningsprogram.data.db.entity.LiftGoal?
    ) {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val weightInput = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            hint = "Target weight (kg)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (existing != null) setText(
                if (existing.targetWeightKg == existing.targetWeightKg.toInt().toFloat())
                    existing.targetWeightKg.toInt().toString()
                else existing.targetWeightKg.toString()
            )
        }
        val e1rmCheck = android.widget.CheckBox(ctx).apply {
            text = "Target is estimated 1RM (not a single working set)"
            isChecked = existing?.isE1rm ?: false
        }
        var targetDateMs = existing?.targetDateMs ?: 0L
        val dateButton = com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = com.migul.treningsprogram.domain.GoalProgress.dateFlavor(targetDateMs)
                ?: "Add a target date (optional)"
            setOnClickListener {
                val picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Target date — flavor, never a deadline")
                    .build()
                picker.addOnPositiveButtonClickListener { sel ->
                    targetDateMs = sel ?: 0L
                    text = com.migul.treningsprogram.domain.GoalProgress.dateFlavor(targetDateMs)
                        ?: "Add a target date (optional)"
                }
                picker.show(parentFragmentManager, "goal_date_picker")
            }
        }
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(weightInput)
            addView(e1rmCheck)
            addView(dateButton)
        }

        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existing == null) "Set goal — $exerciseName" else "Edit goal — $exerciseName")
            .setView(column)
            .setPositiveButton("Save") { _, _ ->
                val target = weightInput.text?.toString()?.toFloatOrNull() ?: 0f
                if (target > 0f) {
                    viewModel.saveGoal(exerciseName, target, e1rmCheck.isChecked, targetDateMs)
                }
            }
            .setNegativeButton("Cancel", null)
        if (existing != null) {
            // Abandoning is unceremonious — one tap, the goal just leaves the lists.
            builder.setNeutralButton("Abandon goal") { _, _ -> viewModel.abandonGoal(existing) }
        }
        builder.show()
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
     * B07: there is no per-field guidance copy. The whole PR card is hidden when there is no
     * selection or no qualifying PR history; it appears only once there are PRs to show.
     * Most-recent PR is shown first.
     */
    private fun renderPrTimeline(
        history: List<com.migul.treningsprogram.data.db.dao.StrengthPoint>,
        exerciseName: String
    ) {
        if (_binding == null) return
        binding.layoutPrs.removeAllViews()

        val prs = if (exerciseName.isBlank()) emptyList() else OneRmTrend.prTimeline(history)
        binding.cardPrs.isVisible = prs.isNotEmpty()
        if (prs.isEmpty()) return

        // Most recent PR first.
        prs.asReversed().forEach { pr ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val p = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, p, 0, p)
            }
            row.addView(TextView(requireContext()).apply {
                text = dateFmt.format(Date(com.migul.treningsprogram.domain.DayBoundary.toLogicalMillis(pr.dateMs)))
                textSize = 13f
                setTextColor(Color.parseColor("#7E908E"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(requireContext()).apply {
                text = "${formatWeight(pr.weightKg)} kg × ${pr.reps}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#7FE9E1"))
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
