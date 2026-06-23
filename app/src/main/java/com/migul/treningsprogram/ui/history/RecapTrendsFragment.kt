package com.migul.treningsprogram.ui.history

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.databinding.FragmentRecapTrendsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class RecapTrendsFragment : Fragment() {

    private var _binding: FragmentRecapTrendsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecapTrendsViewModel by viewModels()

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private var exerciseName: String = ""
    private var sessionDateMs: Long = 0L
    private var allHistory: List<StrengthPoint> = emptyList()
    private var windowMonths: Int = 0  // 0 = all

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecapTrendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        exerciseName = arguments?.getString("exerciseName") ?: ""
        sessionDateMs = arguments?.getLong("sessionDateMs") ?: 0L

        binding.tvTrendTitle.text = exerciseName
        binding.tvTrendSubtitle.text = "Your progress over time"

        binding.cgTrendTime.setOnCheckedStateChangeListener { _, checkedIds ->
            windowMonths = when {
                checkedIds.contains(com.migul.treningsprogram.R.id.chip_trend_1m) -> 1
                checkedIds.contains(com.migul.treningsprogram.R.id.chip_trend_3m) -> 3
                checkedIds.contains(com.migul.treningsprogram.R.id.chip_trend_6m) -> 6
                else -> 0
            }
            render()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            allHistory = viewModel.getStrengthHistory(exerciseName)
            if (_binding == null) return@launch
            render()
        }
    }

    private fun render() {
        if (_binding == null) return

        // This-session callout — the recorded point on the session's day.
        val sessionPoint = allHistory.minByOrNull { kotlin.math.abs(it.dateMs - sessionDateMs) }
        binding.tvTrendThisSession.text = when {
            sessionPoint == null -> "📍 First time logging this exercise — this is your baseline."
            else -> "📍 This session: ${fmt(sessionPoint.maxWeight)} kg × ${sessionPoint.bestReps}  ·  ${dateFmt.format(Date(sessionPoint.dateMs))}"
        }

        val windowed = if (windowMonths == 0) allHistory else {
            val cutoff = System.currentTimeMillis() - windowMonths * 30L * 86_400_000L
            allHistory.filter { it.dateMs >= cutoff }
        }

        binding.chartTrend.setData(
            windowed.map { StrengthChartView.Entry(it.dateMs, it.maxWeight) },
            "kg",
            sessionDateMs
        )

        // e1RM estimate (Epley) from the best low-rep set.
        val best = windowed.filter { it.bestReps in 1 until 20 }.maxByOrNull { it.maxWeight }
        if (best != null) {
            val e1rm = best.maxWeight * (1 + best.bestReps / 30.0)
            binding.tvTrendE1rm.text = "Estimated 1RM: ~${e1rm.toInt()} kg (estimate, Epley formula)"
            binding.tvTrendE1rm.isVisible = true
        } else {
            binding.tvTrendE1rm.isVisible = false
        }

        renderPrHistory()
    }

    private fun renderPrHistory() {
        binding.layoutTrendPrHistory.removeAllViews()
        // Walk chronologically; each new running-max is a PR.
        val chrono = allHistory.sortedBy { it.dateMs }
        var running = 0f
        val prs = mutableListOf<StrengthPoint>()
        for (p in chrono) {
            if (p.maxWeight > running) {
                running = p.maxWeight
                prs.add(p)
            }
        }
        if (prs.isEmpty()) {
            binding.layoutTrendPrHistory.addView(TextView(requireContext()).apply {
                text = "No PR history yet — log a few sessions to build it."
                textSize = 13f
                setTextColor(Color.parseColor("#9A9AB0"))
            })
            return
        }
        // Most recent PR first.
        prs.asReversed().forEach { p ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val pad = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, pad, 0, pad)
            }
            row.addView(TextView(requireContext()).apply {
                text = dateFmt.format(Date(p.dateMs))
                textSize = 13f
                setTextColor(Color.parseColor("#9A9AB0"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(requireContext()).apply {
                text = "${fmt(p.maxWeight)} kg"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#7C67F5"))
            })
            binding.layoutTrendPrHistory.addView(row)
        }
    }

    private fun fmt(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
