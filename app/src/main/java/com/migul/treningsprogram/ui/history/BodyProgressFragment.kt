package com.migul.treningsprogram.ui.history

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.databinding.FragmentHistoryBodyBinding
import com.migul.treningsprogram.domain.BodyComposition
import com.migul.treningsprogram.domain.BodyProgressCharts
import com.migul.treningsprogram.domain.BodyProgressRange
import com.migul.treningsprogram.domain.DateRangeFilter
import com.migul.treningsprogram.domain.DayBoundary
import com.migul.treningsprogram.domain.WeightTrend
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Body-progress batch 2026-08-04 (brief 02) — the "Body" tab on the Stats screen.
 *
 * Decision 10 shapes the whole screen: the charts are the point, and the logging UI is one
 * collapsed header row until the user asks for it (A7).
 */
@AndroidEntryPoint
class BodyProgressFragment : Fragment() {

    private var _binding: FragmentHistoryBodyBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BodyProgressViewModel by viewModels()

    private val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBodyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRangeControl()
        setupLogging()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.series.collect { renderCharts(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    viewModel.recentEntries, viewModel.totalEntryCount
                ) { rows, total -> rows to total }
                    .collect { (rows, total) -> renderRecentEntries(rows, total) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Height/sex are editable in Settings while this tab is alive, and body fat is DERIVED from
        // them — so re-apply the sex-dependent visibility and nudge the flows to recompute.
        applyProfileVisibility()
        viewModel.onProfileMayHaveChanged()
    }

    // ── Time scale ────────────────────────────────────────────────────────────────────────────

    private fun setupRangeControl() {
        val chipFor = mapOf(
            BodyProgressRange.Preset.M1 to binding.chipBody1m,
            BodyProgressRange.Preset.M3 to binding.chipBody3m,
            BodyProgressRange.Preset.M6 to binding.chipBody6m,
            BodyProgressRange.Preset.Y1 to binding.chipBody1y,
            BodyProgressRange.Preset.ALL to binding.chipBodyAll
        )
        chipFor.forEach { (preset, chip) ->
            chip.setOnClickListener {
                viewModel.selectPreset(preset)
                syncRangeControl()
            }
        }

        // A6: an exact start + end calendar range, using the same picker the History and Progress
        // tabs use so the interaction is identical across the app.
        binding.btnBodyRange.setOnClickListener {
            val picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Filter body progress by date")
                .build()
            picker.addOnPositiveButtonClickListener { sel ->
                val start = sel.first
                val end = sel.second
                if (start != null && end != null) {
                    viewModel.selectCustomRange(DateRangeFilter.fromPickerUtc(start, end))
                    syncRangeControl()
                }
            }
            picker.show(parentFragmentManager, "body_range_picker")
        }

        // Clearing a custom range returns to the 3-month default rather than "All" — the default is
        // what the user asked the tab to open on (decision 7).
        binding.btnBodyRangeClear.setOnClickListener {
            viewModel.selectPreset(BodyProgressRange.DEFAULT)
            syncRangeControl()
        }

        syncRangeControl()
    }

    /** Keeps the chips, the custom-range button label and the clear button consistent. */
    private fun syncRangeControl() {
        if (_binding == null) return
        val preset = viewModel.rangePreset.value
        val custom = viewModel.customRange.value
        binding.chipBody1m.isChecked = preset == BodyProgressRange.Preset.M1
        binding.chipBody3m.isChecked = preset == BodyProgressRange.Preset.M3
        binding.chipBody6m.isChecked = preset == BodyProgressRange.Preset.M6
        binding.chipBody1y.isChecked = preset == BodyProgressRange.Preset.Y1
        binding.chipBodyAll.isChecked = preset == BodyProgressRange.Preset.ALL
        binding.btnBodyRange.text =
            if (custom != null) DateRangeFilter.label(custom) else "Custom dates"
        binding.btnBodyRangeClear.isVisible = custom != null
    }

    // ── Charts ────────────────────────────────────────────────────────────────────────────────

    private fun renderCharts(series: BodyProgressCharts.Series) {
        if (_binding == null) return

        binding.tvBodyEmpty.isVisible = series.isEmpty

        // Body weight — raw weigh-ins plus the same smoothed overlay/readout the Home card uses.
        val weightRows = series.weight.map { BodyMeasurement(dateMs = it.dateMs, weightKg = it.value) }
        val showWeight = series.weight.size >= 2
        binding.cardBodyWeightChart.isVisible = showWeight
        if (showWeight) {
            binding.chartBodyWeightBody.setData(
                series.weight.map { BodyWeightChartView.Entry(it.dateMs, it.value) },
                WeightTrend.smoothedSeries(weightRows)
                    .map { (ms, kg) -> BodyWeightChartView.Entry(ms, kg) }
            )
            val line = WeightTrend.homeLine(weightRows)
            binding.tvBodyBwTrend.isVisible = line != null
            binding.tvBodyBwTrend.text = line ?: ""
        }

        // Body fat — a single point is still worth showing as a number, but the chart needs two.
        val fat = series.bodyFat
        binding.cardBodyFat.isVisible = fat.isNotEmpty()
        binding.chartBodyFat.isVisible = fat.size >= 2
        if (fat.isNotEmpty()) {
            binding.chartBodyFat.setData(
                fat.map { StrengthChartView.Entry(it.dateMs, it.value) },
                label = "%",
                decimalPlaces = 1
            )
            binding.tvBodyFatCurrent.isVisible = true
            binding.tvBodyFatCurrent.text = "%.1f %%".format(fat.last().value)
        }

        renderGirthChart(binding.cardWaist, binding.chartWaist, series.waist)
        renderGirthChart(binding.cardNeck, binding.chartNeck, series.neck)
        // Decision 2: the hip card can only ever appear for a female profile — the series is
        // already forced empty for men upstream, so this is belt-and-braces.
        renderGirthChart(
            binding.cardHip, binding.chartHip,
            if (viewModel.showHip) series.hip else emptyList()
        )

        // A5: explain the missing body-fat chart rather than leaving a silent gap — but only once
        // there are girths that WOULD produce one.
        val hasGirths = series.waist.isNotEmpty() || series.neck.isNotEmpty()
        binding.tvBodyProfileHint.isVisible = hasGirths && !viewModel.profileComplete
    }

    private fun renderGirthChart(
        card: View,
        chart: StrengthChartView,
        points: List<BodyProgressCharts.Point>
    ) {
        card.isVisible = points.size >= 2
        if (points.size >= 2) {
            chart.setData(
                points.map { StrengthChartView.Entry(it.dateMs, it.value) },
                label = "cm",
                decimalPlaces = 1
            )
        }
    }

    // ── Logging (secondary) ───────────────────────────────────────────────────────────────────

    private fun setupLogging() {
        applyProfileVisibility()

        binding.btnBodyLogToggle.setOnClickListener {
            val opening = !binding.layoutBodyLog.isVisible
            binding.layoutBodyLog.isVisible = opening
            binding.btnBodyLogToggle.text = if (opening) "Close" else "Log entry"
        }

        binding.btnBodySave.setOnClickListener { saveEntry() }
    }

    /** Decision 2: a male profile never sees a hip field anywhere in the app. */
    private fun applyProfileVisibility() {
        if (_binding == null) return
        val showHip = viewModel.showHip
        binding.tilBodyHip.isVisible = showHip
        if (!showHip) binding.etBodyHip.setText("")
    }

    /**
     * Reads the four optional fields, rejects obviously mistyped values, and saves. Validation is
     * deliberately at the INPUT boundary: [BodyComposition] never clamps, so one absurd row would
     * otherwise wreck the chart scale for every honest point around it.
     */
    private fun saveEntry() {
        val weight = binding.etBodyWeight.text?.toString()?.trim()?.toFloatOrNull()
        val waist = binding.etBodyWaist.text?.toString()?.trim()?.toFloatOrNull()
        val neck = binding.etBodyNeck.text?.toString()?.trim()?.toFloatOrNull()
        val hip = if (viewModel.showHip) binding.etBodyHip.text?.toString()?.trim()?.toFloatOrNull() else null

        val problem = when {
            weight != null && !BodyComposition.isPlausibleWeight(weight) ->
                "Weight should be between ${fmt(BodyComposition.MIN_WEIGHT_KG)} and ${fmt(BodyComposition.MAX_WEIGHT_KG)} kg."
            waist != null && !BodyComposition.isPlausibleWaist(waist) ->
                "Waist should be between ${fmt(BodyComposition.MIN_WAIST_CM)} and ${fmt(BodyComposition.MAX_WAIST_CM)} cm."
            neck != null && !BodyComposition.isPlausibleNeck(neck) ->
                "Neck should be between ${fmt(BodyComposition.MIN_NECK_CM)} and ${fmt(BodyComposition.MAX_NECK_CM)} cm."
            hip != null && !BodyComposition.isPlausibleHip(hip) ->
                "Hip should be between ${fmt(BodyComposition.MIN_HIP_CM)} and ${fmt(BodyComposition.MAX_HIP_CM)} cm."
            else -> null
        }
        if (problem != null) {
            Snackbar.make(binding.root, problem, Snackbar.LENGTH_LONG).show()
            return
        }

        if (!viewModel.logEntry(weight, waist, neck, hip)) {
            Snackbar.make(binding.root, "Enter at least one measurement.", Snackbar.LENGTH_SHORT).show()
            return
        }

        binding.etBodyWeight.setText("")
        binding.etBodyWaist.setText("")
        binding.etBodyNeck.setText("")
        binding.etBodyHip.setText("")
        Snackbar.make(binding.root, "Entry saved.", Snackbar.LENGTH_SHORT).show()
    }

    /**
     * The entry list inside the logging section — the ONLY way to remove a mistyped measurement,
     * without which a single fat-fingered value would distort the charts permanently.
     *
     * It carries BOTH kinds of entry: body weight (including weights added from the Home screen)
     * and girths, paired into one line when they were saved together. [total] drives the "Show all"
     * affordance, so entries from months back stay reachable instead of being cut off at ten.
     */
    private fun renderRecentEntries(rows: List<BodyProgressCharts.EntryRow>, total: Int) {
        if (_binding == null) return
        val any = rows.isNotEmpty()
        binding.tvBodyRecentLabel.isVisible = any
        binding.tvBodyRecentHint.isVisible = any
        binding.layoutBodyEntries.removeAllViews()

        val showingAll = viewModel.showAllEntries.value
        binding.btnBodyShowAll.isVisible =
            any && (total > BodyProgressCharts.DEFAULT_ENTRY_LIMIT)
        binding.btnBodyShowAll.text =
            if (showingAll) "Show fewer" else "Show all $total"
        binding.btnBodyShowAll.setOnClickListener { viewModel.toggleShowAllEntries() }

        val density = resources.displayMetrics.density
        rows.forEach { row ->
            val line = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val p = (6 * density).toInt()
                setPadding(0, p, 0, p)
            }
            line.addView(TextView(requireContext()).apply {
                text = dateFmt.format(Date(DayBoundary.toLogicalMillis(row.dateMs)))
                textSize = 13f
                setTextColor(Color.parseColor("#7E908E"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            line.addView(TextView(requireContext()).apply {
                text = summarize(row)
                textSize = 13f
                setTextColor(Color.parseColor("#7FE9E1"))
            })
            line.setOnClickListener { confirmDelete(row) }
            line.isClickable = true
            binding.layoutBodyEntries.addView(line)
        }
    }

    /**
     * A combined save spans two rows (weight in `body_measurements`, girths in `body_metrics`), so
     * "delete" is ambiguous for it: mistyping the waist must not also cost the weight logged in the
     * same breath. Those entries get a three-way choice; a single-kind entry gets a plain confirm.
     */
    private fun confirmDelete(row: BodyProgressCharts.EntryRow) {
        val date = dateFmt.format(Date(DayBoundary.toLogicalMillis(row.dateMs)))
        val ctx = requireContext()

        if (!row.isCombined) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("Delete this entry?")
                .setMessage("$date — ${summarize(row)}")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteEntry(row, BodyProgressCharts.DeletePart.ALL)
                    Snackbar.make(binding.root, "Entry deleted.", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val options = arrayOf<CharSequence>(
            "Delete the whole entry",
            "Delete only the weight (${weightPart(row)})",
            "Delete only the measurements (${metricsPart(row)})"
        )
        val parts = arrayOf(
            BodyProgressCharts.DeletePart.ALL,
            BodyProgressCharts.DeletePart.WEIGHT_ONLY,
            BodyProgressCharts.DeletePart.METRICS_ONLY
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("Delete — $date")
            .setItems(options) { _, which ->
                viewModel.deleteEntry(row, parts[which])
                Snackbar.make(binding.root, "Deleted.", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun weightPart(row: BodyProgressCharts.EntryRow): String =
        row.weightKg?.let { "${fmt(it)} kg" } ?: "—"

    private fun metricsPart(row: BodyProgressCharts.EntryRow): String {
        val parts = mutableListOf<String>()
        row.waistCm?.let { parts.add("waist ${fmt(it)}") }
        row.neckCm?.let { parts.add("neck ${fmt(it)}") }
        row.hipCm?.let { parts.add("hip ${fmt(it)}") }
        return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
    }

    private fun summarize(row: BodyProgressCharts.EntryRow): String {
        val parts = mutableListOf<String>()
        row.weightKg?.let { parts.add("${fmt(it)} kg") }
        row.waistCm?.let { parts.add("waist ${fmt(it)}") }
        row.neckCm?.let { parts.add("neck ${fmt(it)}") }
        row.hipCm?.let { parts.add("hip ${fmt(it)}") }
        row.bodyFatPercent?.let { parts.add("%.1f%% fat".format(it)) }
        return parts.joinToString(" · ")
    }

    private fun fmt(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
