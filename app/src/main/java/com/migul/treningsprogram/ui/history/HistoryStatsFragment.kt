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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class HistoryStatsFragment : Fragment() {

    private var _binding: FragmentHistoryStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadStats()

        binding.btnExport.setOnClickListener {
            val sessions = viewModel.allSessions.value
            viewModel.exportCsv(sessions) { csv ->
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, csv)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Workout History Export")
                }
                startActivity(android.content.Intent.createChooser(intent, "Export CSV"))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    private fun loadStats() {
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
        }
    }

    private fun computeBestStreak(dateMsValues: List<Long>): Int {
        if (dateMsValues.isEmpty()) return 0
        val days = dateMsValues.map { it / 86_400_000L }.toSortedSet()
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
            val barColor = MuscleClassifier.colorFor(muscle, "#607D8B")
            val bar = View(requireContext()).apply {
                val maxW = (200 * resources.displayMetrics.density).toInt()
                val w = (maxW * sets / max.toFloat()).toInt().coerceAtLeast(4)
                layoutParams = LinearLayout.LayoutParams(w, (14 * resources.displayMetrics.density).toInt()).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(barColor))
                    cornerRadius = 6 * resources.displayMetrics.density
                }
            }
            val tvCount = TextView(requireContext()).apply {
                text = sets.toString()
                textSize = 12f
                setTextColor(Color.parseColor(barColor))
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

        val todayEpoch = System.currentTimeMillis() / 86_400_000L
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
                        setColor(if (isTraining) Color.parseColor("#9977F5") else Color.parseColor("#2A2A3C"))
                    }
                }
                binding.gridCalendar.addView(cell)
            }
        }
    }

    private fun renderRepRanges(data: List<Pair<String, Int>>) {
        binding.layoutRepRanges.removeAllViews()
        if (data.isEmpty()) return
        val max = data.maxOf { it.second }
        val colors = listOf("#E91E63", "#7C67F5", "#4CAF50")
        data.forEachIndexed { idx, (label, count) ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val p = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, p, 0, p)
            }
            val tvLabel = TextView(requireContext()).apply {
                text = label
                textSize = 12f
                val w = (120 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val barColor = colors.getOrElse(idx) { "#607D8B" }
            val bar = View(requireContext()).apply {
                val maxW = (160 * resources.displayMetrics.density).toInt()
                val w = (maxW * count / max.toFloat()).toInt().coerceAtLeast(4)
                layoutParams = LinearLayout.LayoutParams(w, (14 * resources.displayMetrics.density).toInt()).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(barColor))
                    cornerRadius = 6 * resources.displayMetrics.density
                }
            }
            val tvCount = TextView(requireContext()).apply {
                text = count.toString()
                textSize = 12f
                setTextColor(Color.parseColor(barColor))
            }
            row.addView(tvLabel)
            row.addView(bar)
            row.addView(tvCount)
            binding.layoutRepRanges.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
