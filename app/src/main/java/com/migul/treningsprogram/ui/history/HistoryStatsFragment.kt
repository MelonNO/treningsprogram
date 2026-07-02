package com.migul.treningsprogram.ui.history

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.isVisible
import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.databinding.FragmentHistoryStatsBinding
import com.migul.treningsprogram.domain.DayBoundary
import com.migul.treningsprogram.domain.WeekDelta
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class HistoryStatsFragment : Fragment() {

    private var _binding: FragmentHistoryStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels({ requireParentFragment() })

    // Stage-3 item 2: skeleton only until the FIRST render — onResume refreshes over live content.
    private var statsRendered = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadStats()
        // Stage-3 item 15: the CSV export button is gone — Settings → Backup & Data covers export.

        // Stage-3 item 11: tapping a non-empty heatmap cell opens the Recap tab with the most
        // recent session that week which trained that muscle, and that muscle highlighted
        // (existing openRecap/highlight mechanism). Empty cells never invoke this callback.
        binding.viewVolumeHeatmap.onCellTap = { muscle, weekStartMs ->
            viewLifecycleOwner.lifecycleScope.launch {
                val sessionId = viewModel.resolveHeatmapSession(muscle, weekStartMs)
                if (sessionId != null && isAdded) {
                    (requireParentFragment() as? HistoryFragment)?.openRecap(sessionId, muscle)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    private fun loadStats() {
        if (!statsRendered && _binding != null) {
            com.migul.treningsprogram.ui.common.Skeleton.showDelayed(binding.skeletonStats)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val totalSets = viewModel.getTotalSets()
            val totalVolume = viewModel.getTotalVolume()
            val sessions = viewModel.allSessions.value
            val totalWorkouts = sessions.size
            val bestStreak = computeBestStreak(sessions.map { it.dateMs })
            val muscleVolume = viewModel.getMuscleVolume()
            val trainingDays = viewModel.getTrainingDays()
            val repRanges = viewModel.getRepRanges()

            if (_binding == null) return@launch
            com.migul.treningsprogram.ui.common.Skeleton.hide(binding.skeletonStats)
            statsRendered = true

            // First-run / no-data state (UX1): show a single friendly prompt instead of a wall of
            // zero-cards and empty charts. Switches to the full dashboard once any set is logged.
            val hasData = totalWorkouts > 0 || totalSets > 0
            binding.tvStatsEmpty.isVisible = !hasData
            binding.contentStats.isVisible = hasData
            if (!hasData) return@launch

            binding.tvTotalWorkouts.text = totalWorkouts.toString()
            binding.tvTotalSets.text = totalSets.toString()
            binding.tvTotalVolume.text = if (totalVolume >= 1000f) {
                "${"%.1f".format(totalVolume / 1000f)} t"
            } else {
                "${totalVolume.roundToInt()} kg"
            }
            binding.tvBestStreak.text = bestStreak.toString()

            renderMuscleBars(muscleVolume.map { it.muscleGroup to it.totalSets })
            renderCalendar(trainingDays.toSet())
            renderRepRanges(repRanges.map { it.label to it.setCount })

            // F5: weekly per-muscle volume heatmap — hidden until there is at least one cell.
            val heatmap = viewModel.getVolumeHeatmap()
            if (_binding == null) return@launch
            binding.cardVolumeHeatmap.isVisible = heatmap.maxSets > 0
            if (heatmap.maxSets > 0) binding.viewVolumeHeatmap.setGrid(heatmap)

            // This-week pulse: current sets/sessions with a vs-last-week delta.
            val delta = viewModel.getWeekDelta()
            if (_binding == null) return@launch
            val hasPulse = delta.sets > 0 || delta.setsPrev > 0
            binding.cardWeekPulse.isVisible = hasPulse
            if (hasPulse) {
                binding.tvPulseSets.text = delta.sets.toString()
                binding.tvPulseSetsDelta.text =
                    "sets  ·  ${WeekDelta.deltaLabel(delta.sets, delta.setsPrev)} vs last week"
                binding.tvPulseSessions.text = delta.sessions.toString()
                binding.tvPulseSessionsDelta.text =
                    "sessions  ·  ${WeekDelta.deltaLabel(delta.sessions, delta.sessionsPrev)}"
            }
        }
    }

    private fun computeBestStreak(dateMsValues: List<Long>): Int {
        if (dateMsValues.isEmpty()) return 0
        // Item 7 day boundary: LOGICAL local days, matching the gamification streak — the old raw
        // UTC-day division could split or join streaks differently from the streak shown on Home.
        val days = dateMsValues.map { DayBoundary.logicalEpochDay(it) }.toSortedSet()
        var best = 1
        var current = 1
        val sorted = days.toList()
        for (i in 1 until sorted.size) {
            if (sorted[i] - sorted[i - 1] == 1L) {
                current++
                if (current > best) best = current
            } else {
                current = 1
            }
        }
        return best
    }

    private fun renderMuscleBars(data: List<Pair<String, Int>>) {
        binding.layoutMuscleBars.removeAllViews()
        if (data.isEmpty()) return
        val max = data.maxOf { it.second }
        data.forEach { (muscle, sets) ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val p = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, p, 0, p)
            }
            val tvLabel = TextView(requireContext()).apply {
                text = muscle
                textSize = 12f
                val w = (80 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val barColor = Color.parseColor(MuscleClassifier.colorFor(muscle, "#7E908E")) // fallback = auros_fog_dim
            // Luminous gradient bar: the muscle color sweeps into a lighter tip, like a light trail.
            val tip = androidx.core.graphics.ColorUtils.blendARGB(barColor, 0xFFFFFFFF.toInt(), 0.35f)
            val bar = View(requireContext()).apply {
                val maxW = (200 * resources.displayMetrics.density).toInt()
                val w = (maxW * sets / max.toFloat()).toInt().coerceAtLeast(6)
                layoutParams = LinearLayout.LayoutParams(w, (14 * resources.displayMetrics.density).toInt()).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(barColor, tip)
                ).apply { cornerRadius = 7 * resources.displayMetrics.density }
            }
            val tvCount = TextView(requireContext()).apply {
                text = sets.toString()
                textSize = 12f
                setTextColor(barColor)
            }
            row.addView(tvLabel)
            row.addView(bar)
            row.addView(tvCount)
            binding.layoutMuscleBars.addView(row)
        }
    }

    private fun renderCalendar(trainingDayEpochs: Set<Long>) {
        binding.gridCalendar.removeAllViews()
        binding.gridCalendar.columnCount = 13

        // Item 7 day boundary: the cells' data (getTrainingDayEpochs) is LOGICAL epoch-days, so the
        // "today" anchor must be too — the old UTC division shifted the whole calendar by one cell
        // for part of the day in non-UTC timezones.
        val todayEpoch = DayBoundary.todayEpochDay()
        val startEpoch = todayEpoch - 90

        val dp = resources.displayMetrics.density
        val cellSize = (14 * dp).toInt()
        val cellMargin = (2 * dp).toInt()

        for (row in 0..6) {
            for (col in 0..12) {
                val dayOffset = col * 7 + row
                val epoch = startEpoch + dayOffset
                if (epoch > todayEpoch) {
                    // placeholder cell
                    val placeholder = View(requireContext()).apply {
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = cellSize
                            height = cellSize
                            setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                        }
                    }
                    binding.gridCalendar.addView(placeholder)
                    continue
                }

                val isTraining = epoch in trainingDayEpochs
                val cell = View(requireContext()).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = cellSize
                        height = cellSize
                        setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = 3 * dp
                        setColor(if (isTraining) Color.parseColor("#7FE9E1") else Color.parseColor("#0C2E2C"))
                        // "You are here": outline today's cell in bright cyan.
                        if (epoch == todayEpoch) setStroke((1.5f * dp).toInt().coerceAtLeast(2), Color.parseColor("#CBFFFC"))
                    }
                }
                binding.gridCalendar.addView(cell)
            }
        }
    }

    /**
     * One stacked, proportionally-segmented bar (heavy → moderate → light) with a legend under
     * it — replaces the old three separate bars (whose pink/green also sat off-palette).
     */
    private fun renderRepRanges(data: List<Pair<String, Int>>) {
        binding.layoutRepRanges.removeAllViews()
        val total = data.sumOf { it.second }
        if (total <= 0) return
        val dp = resources.displayMetrics.density
        val order = listOf("Heavy (1-5)", "Moderate (6-12)", "Light (13+)")
        val colors = mapOf(
            order[0] to Color.parseColor("#7FE9E1"),   // cyan — heavy
            order[1] to Color.parseColor("#9B8CFF"),   // violet — moderate
            order[2] to Color.parseColor("#37D67A"),   // green — light
        )
        val counts = order.map { label -> label to (data.firstOrNull { it.first == label }?.second ?: 0) }

        // The segmented bar, clipped to a capsule by a zero-padding card.
        val segments = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (18 * dp).toInt()
            )
        }
        counts.filter { it.second > 0 }.forEach { (label, count) ->
            segments.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, count.toFloat())
                setBackgroundColor(colors.getValue(label))
            })
        }
        val capsule = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            radius = 9 * dp
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#0C2E2C"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
            addView(segments)
        }
        binding.layoutRepRanges.addView(capsule)

        // Legend: dot · label · count (share%)
        counts.forEach { (label, count) ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
            }
            row.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((8 * dp).toInt(), (8 * dp).toInt()).apply {
                    marginEnd = (8 * dp).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colors.getValue(label))
                }
            })
            row.addView(TextView(requireContext()).apply {
                text = label
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(requireContext()).apply {
                text = "$count  ·  ${(count * 100f / total).roundToInt()}%"
                textSize = 12f
                setTextColor(colors.getValue(label))
            })
            binding.layoutRepRanges.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
