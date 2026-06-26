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
import com.migul.treningsprogram.domain.DataScreenEmptyState
import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.OneRmTrend
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

        // B07 whole-screen empty state: when this exercise has no recorded history at all, show the
        // single top-level line and hide every card. Cards below appear individually as data exists.
        val screenEmpty = DataScreenEmptyState.isTrendsEmpty(allHistory.size)
        binding.tvTrendEmpty.isVisible = screenEmpty
        binding.cardTrendThisSession.isVisible = !screenEmpty
        if (screenEmpty) {
            binding.cardTrendMaxWeight.isVisible = false
            binding.cardTrendE1rm.isVisible = false
            binding.cardTrendPrHistory.isVisible = false
            return
        }

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

        // B07: hide the max-weight chart card when it can't draw (StrengthChartView needs >= 2
        // points) rather than show its internal "Not enough data yet" message.
        binding.cardTrendMaxWeight.isVisible = windowed.size >= 2
        binding.chartTrend.setData(
            windowed.map { StrengthChartView.Entry(it.dateMs, it.maxWeight) },
            "kg",
            sessionDateMs
        )

        // Estimated-1RM trend (Epley, per session) — its own chart on the windowed history.
        // B07: hide the whole card below 2 points instead of showing per-field empty copy.
        val e1rmTrend = OneRmTrend.trendPoints(windowed)
        binding.cardTrendE1rm.isVisible = e1rmTrend.size >= 2
        binding.chartTrendE1rm.setData(
            e1rmTrend.map { StrengthChartView.Entry(it.dateMs, it.e1rm.toFloat()) },
            "kg",
            sessionDateMs
        )

        // e1RM estimate (Epley) from the best set in the window.
        val best = windowed.filter { it.bestReps in 1 until 20 }
            .maxByOrNull { Epley.estimate(it.maxWeight, it.bestReps) }
        if (best != null) {
            val e1rm = Epley.estimate(best.maxWeight, best.bestReps)
            binding.tvTrendE1rm.text = "Estimated 1RM: ~${e1rm.toInt()} kg (estimate, Epley formula)"
            binding.tvTrendE1rm.isVisible = true
        } else {
            binding.tvTrendE1rm.isVisible = false
        }

        renderPrHistory()
    }

    private fun renderPrHistory() {
        binding.layoutTrendPrHistory.removeAllViews()
        // PRs tracked by estimated 1RM (Epley) across all sessions — a weight PR OR a rep-PR
        // at equal weight both register. Warm-ups are already excluded upstream.
        // B07: hide the whole PR card when there is no PR history rather than show empty copy.
        val prs = OneRmTrend.prTimeline(allHistory)
        binding.cardTrendPrHistory.isVisible = prs.isNotEmpty()
        if (prs.isEmpty()) return
        // Most recent PR first.
        prs.asReversed().forEach { pr ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val pad = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, pad, 0, pad)
            }
            row.addView(TextView(requireContext()).apply {
                text = dateFmt.format(Date(pr.dateMs))
                textSize = 13f
                setTextColor(Color.parseColor("#9A9AB0"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(requireContext()).apply {
                text = "${fmt(pr.weightKg)} kg × ${pr.reps}"
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
