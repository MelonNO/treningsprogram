package com.migul.treningsprogram.ui.history

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.ExerciseCatalog
import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.databinding.FragmentHistoryLogBinding
import com.migul.treningsprogram.domain.DateRangeFilter
import com.migul.treningsprogram.domain.DayBoundary
import com.migul.treningsprogram.domain.HistoryBrowser
import com.migul.treningsprogram.domain.HistoryPrFlags
import com.migul.treningsprogram.ui.log.ExerciseInfoBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * QoL item 04 — the History sub-tab as a monthly week-browser mirroring the Program tab:
 * months → tappable week rows → a week view with a 7-day chip strip whose selected day lists
 * the exercises ACTUALLY performed. Tapping an exercise opens the performed sets + exercise
 * info in one sheet. Strictly read-only; search and the calendar range filter survive from
 * the old list. All grouping/filter/PR math lives in the pure HistoryBrowser/HistoryPrFlags
 * domain helpers (unit-tested) — this fragment only renders their model.
 */
@AndroidEntryPoint
class HistoryLogFragment : Fragment() {

    private var _binding: FragmentHistoryLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels({ requireParentFragment() })

    private val dayAbbreviations = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val dayNames =
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val dayDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    /** System back closes an open week view (only while this page is the active one). */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.closeBrowserWeek()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.searchQuery.value = s?.toString() ?: ""
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnDateRange.setOnClickListener {
            val picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Filter history by date")
                .build()
            picker.addOnPositiveButtonClickListener { sel ->
                val start = sel.first
                val end = sel.second
                if (start != null && end != null) {
                    viewModel.logDateRange.value = DateRangeFilter.fromPickerUtc(start, end)
                }
            }
            picker.show(parentFragmentManager, "history_range_picker")
        }
        binding.btnDateRangeClear.setOnClickListener { viewModel.logDateRange.value = null }

        binding.btnBackToMonths.setOnClickListener { viewModel.closeBrowserWeek() }

        // Week-view swipe (Program-tab gesture language: right = previous/older week,
        // left = next/newer week). All week/day resolution lives in the ViewModel + the
        // pure HistoryBrowser helpers; the fragment only forwards the gesture.
        binding.layoutWeekView.onSwipeRight = { swipeWeek(-1) }
        binding.layoutWeekView.onSwipeLeft = { swipeWeek(+1) }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.logDateRange.collect { range ->
                        binding.btnDateRange.text = DateRangeFilter.label(range)
                        binding.btnDateRangeClear.isVisible = range != null
                    }
                }
                launch { viewModel.historyBrowser.collect { render() } }
                launch { viewModel.browserWeekStart.collect { render() } }
                launch { viewModel.browserSelectedDay.collect { render() } }
                launch { viewModel.browserEdgeNudge.collect { nudgeWeekView(it) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        backCallback.isEnabled = viewModel.browserWeekStart.value != null
    }

    override fun onPause() {
        super.onPause()
        backCallback.isEnabled = false
    }

    /** Forward a completed swipe; if the week actually changed, reorient to the top like
     *  opening a week from the month list does. */
    private fun swipeWeek(direction: Int) {
        val before = viewModel.browserWeekStart.value
        viewModel.swipeBrowserWeek(direction)
        if (viewModel.browserWeekStart.value != before) {
            _binding?.root?.smoothScrollTo(0, 0)
        }
    }

    /** Subtle end-of-range nudge: the week view shifts a few dp against the swipe and
     *  springs back — the overscroll hint that there is no further week that way. */
    private fun nudgeWeekView(direction: Int) {
        val v = _binding?.layoutWeekView ?: return
        val shift = 10f * resources.displayMetrics.density * -direction
        v.animate().cancel()
        v.animate().translationX(shift).setDuration(90L).withEndAction {
            v.animate().translationX(0f).setDuration(180L)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
        }.start()
    }

    // ── Render dispatch ────────────────────────────────────────────────────────────────────

    private fun render() {
        val b = _binding ?: return
        val model = viewModel.historyBrowser.value
        if (model == null) {
            // Still loading — show nothing; content simply appears when ready (the skeleton
            // placeholder was removed by user decision: it kept overlaying loaded content).
            b.tvEmpty.isVisible = false
            b.layoutBrowser.isVisible = false
            b.layoutWeekView.isVisible = false
            backCallback.isEnabled = false
            return
        }

        val week = viewModel.browserWeekStart.value?.let { model.weeksByStart[it] }
        backCallback.isEnabled = week != null && isResumed
        if (week != null) renderWeekView(week) else renderBrowser(model)
    }

    // ── Month browser ──────────────────────────────────────────────────────────────────────

    private fun renderBrowser(model: HistoryBrowser.Model) {
        binding.layoutWeekView.isVisible = false
        binding.layoutBrowser.isVisible = true

        binding.tvEmpty.isVisible = model.months.isEmpty()
        binding.tvEmpty.text = when {
            !model.hasAnyHistory -> "Your training story starts with your first workout."
            viewModel.logDateRange.value != null && viewModel.searchQuery.value.isNotBlank() ->
                "No days match your search in the selected range."
            viewModel.logDateRange.value != null -> "No days in the selected date range."
            else -> "No days match your search."
        }

        binding.layoutBrowser.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (month in model.months) {
            binding.layoutBrowser.addView(buildMonthHeader(month))
            for (week in month.weeks) {
                binding.layoutBrowser.addView(buildWeekRow(inflater, week))
            }
        }
    }

    private fun buildMonthHeader(month: HistoryBrowser.Month): View {
        val density = resources.displayMetrics.density
        return TextView(requireContext()).apply {
            text = HistoryBrowser.monthLabel(month)
            textSize = 11f
            isAllCaps = true
            letterSpacing = 0.2f
            setTextColor(requireContext().getColor(R.color.auros_fog))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * density).toInt()
                bottomMargin = (10 * density).toInt()
            }
        }
    }

    private fun buildWeekRow(inflater: LayoutInflater, week: HistoryBrowser.Week): View {
        val row = inflater.inflate(R.layout.item_history_week_row, binding.layoutBrowser, false)
        row.findViewById<TextView>(R.id.tv_week_row_title).text = HistoryBrowser.weekTitle(week)
        row.findViewById<TextView>(R.id.tv_week_row_summary).text = HistoryBrowser.weekSummary(week)

        val dots = row.findViewById<LinearLayout>(R.id.layout_week_row_dots)
        dots.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (8 * density).toInt()
        val gap = (3 * density).toInt()
        for (day in week.days) {
            dots.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = gap
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    when (day.state) {
                        HistoryBrowser.DayState.WORKOUT ->
                            setColor(requireContext().getColor(R.color.game_green))
                        HistoryBrowser.DayState.REST -> setColor(Color.parseColor("#5590A4AE"))
                        HistoryBrowser.DayState.MISSED -> setColor(Color.parseColor("#FF9800"))
                        HistoryBrowser.DayState.EMPTY -> {
                            setColor(Color.TRANSPARENT)
                            setStroke((1 * density).toInt(), Color.parseColor("#33FFFFFF"))
                        }
                        HistoryBrowser.DayState.FUTURE -> {
                            setColor(Color.TRANSPARENT)
                            setStroke((1 * density).toInt(), Color.parseColor("#1AFFFFFF"))
                        }
                    }
                }
            })
        }

        row.setOnClickListener {
            viewModel.openBrowserWeek(
                week.weekStartEpochDay,
                HistoryBrowser.defaultDay(week, DayBoundary.todayEpochDay())
            )
            _binding?.root?.smoothScrollTo(0, 0)
        }
        return row
    }

    // ── Week view (mirrors the Program tab's week card, read-only) ─────────────────────────

    private fun renderWeekView(week: HistoryBrowser.Week) {
        binding.layoutBrowser.isVisible = false
        binding.tvEmpty.isVisible = false
        binding.layoutWeekView.isVisible = true

        binding.tvHistWeekTitle.text = HistoryBrowser.weekTitle(week)
        binding.tvHistWeekSummary.text = HistoryBrowser.weekSummary(week)

        val selected = viewModel.browserSelectedDay.value
            ?.takeIf { week.days.any { d -> d.epochDay == it } }
            ?: HistoryBrowser.defaultDay(week, DayBoundary.todayEpochDay())

        binding.layoutHistWeekDays.removeAllViews()
        week.days.forEachIndexed { i, day ->
            binding.layoutHistWeekDays.addView(
                buildDayChip(day, dayAbbreviations[i], day.epochDay == selected)
            )
        }

        val day = week.days.firstOrNull { it.epochDay == selected } ?: week.days.first()
        renderDaySection(day)
    }

    private fun buildDayChip(day: HistoryBrowser.Day, abbrText: String, isSelected: Boolean): View {
        val chip = layoutInflater.inflate(R.layout.item_day_chip, binding.layoutHistWeekDays, false)
        val abbr = chip.findViewById<TextView>(R.id.tv_day_abbr)
        val type = chip.findViewById<TextView>(R.id.tv_day_type)
        val progress = chip.findViewById<TextView>(R.id.tv_day_progress)

        abbr.text = abbrText
        progress.text = LocalDate.ofEpochDay(day.epochDay).dayOfMonth.toString()

        when {
            isSelected -> {
                abbr.background = requireContext().getDrawable(R.drawable.bg_day_selected)
                abbr.setTextColor(Color.parseColor("#011D1C"))
                abbr.textSize = 11.5f
            }
            day.state == HistoryBrowser.DayState.WORKOUT -> {
                abbr.background = requireContext().getDrawable(R.drawable.bg_day_done)
                abbr.setTextColor(Color.parseColor("#011D1C"))
                abbr.textSize = 11f
            }
            else -> {
                abbr.background = null
                abbr.setTextColor(Color.parseColor("#7E908E"))
                abbr.textSize = 11f
            }
        }

        val (label, color) = when (day.state) {
            HistoryBrowser.DayState.WORKOUT -> (day.type ?: "STR") to when (day.type) {
                "RUN" -> "#9B8CFF"
                "MIX" -> "#FFB347"
                else -> "#7FE9E1"
            }
            HistoryBrowser.DayState.REST -> "REST" to "#90A4AE"
            HistoryBrowser.DayState.MISSED -> "MISS" to "#FF9800"
            else -> "" to "#7E908E"
        }
        if (label.isNotEmpty()) {
            type.text = label
            type.setTextColor(Color.parseColor(color))
            type.visibility = View.VISIBLE
        } else {
            type.text = ""
            type.visibility = View.GONE
        }

        chip.alpha = if (day.state == HistoryBrowser.DayState.FUTURE) 0.45f else 1f
        chip.setOnClickListener { viewModel.selectBrowserDay(day.epochDay) }
        return chip
    }

    private fun renderDaySection(day: HistoryBrowser.Day) {
        val date = LocalDate.ofEpochDay(day.epochDay)
        binding.tvHistDayName.text = dayNames[Math.floorMod(day.epochDay + 3, 7L).toInt()]
        binding.tvHistDayMeta.text = date.format(dayDateFmt)

        binding.layoutHistDaySessions.removeAllViews()
        if (day.state != HistoryBrowser.DayState.WORKOUT) {
            binding.cardHistDayPlaceholder.isVisible = true
            val (title, body) = when (day.state) {
                HistoryBrowser.DayState.REST ->
                    "Rest day" to "Recovery is part of the plan — this day was logged as rest."
                HistoryBrowser.DayState.MISSED ->
                    "Missed workout" to "A training day that passed without a log."
                HistoryBrowser.DayState.FUTURE ->
                    "Not yet" to "This day hasn't happened yet."
                else ->
                    "Nothing logged" to "No entries were recorded on this day."
            }
            binding.tvHistPlaceholderTitle.text = title
            binding.tvHistPlaceholderBody.text = body
            binding.tvHistPlaceholderTitle.setTextColor(
                if (day.state == HistoryBrowser.DayState.MISSED) Color.parseColor("#FF9800")
                else requireContext().getColor(R.color.auros_fog)
            )
            return
        }

        binding.cardHistDayPlaceholder.isVisible = false
        val inflater = LayoutInflater.from(requireContext())
        day.sessions.forEachIndexed { index, session ->
            binding.layoutHistDaySessions.addView(
                buildSessionHeader(session, index, day.sessions.size)
            )
            for (exercise in session.exercises) {
                binding.layoutHistDaySessions.addView(
                    buildExerciseCard(inflater, session, exercise, date)
                )
            }
        }
    }

    /** "Workout · 45 min" (numbered when a day holds several) + a View-Recap link. */
    private fun buildSessionHeader(
        session: HistoryBrowser.SessionSummary, index: Int, count: Int
    ): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) topMargin = (8 * density).toInt()
                bottomMargin = (4 * density).toInt()
            }
        }

        row.addView(TextView(requireContext()).apply {
            val name = if (count > 1) "Workout ${index + 1}" else "Workout"
            text = if (session.durationMinutes > 0) "$name  ·  ${session.durationMinutes} min" else name
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(requireContext().getColor(R.color.auros_fog))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(MaterialButton(
            requireContext(), null,
            com.google.android.material.R.attr.borderlessButtonStyle
        ).apply {
            text = "View Recap"
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setTextColor(requireContext().getColor(R.color.auros_cyan))
            setOnClickListener {
                (requireParentFragment() as? HistoryFragment)?.openRecap(session.sessionId)
            }
        })
        return row
    }

    private fun buildExerciseCard(
        inflater: LayoutInflater,
        session: HistoryBrowser.SessionSummary,
        exercise: HistoryBrowser.ExerciseSummary,
        date: LocalDate
    ): View {
        val card = inflater.inflate(R.layout.item_history_exercise, binding.layoutHistDaySessions, false)

        val badge = card.findViewById<TextView>(R.id.tv_muscle_badge)
        val group = MuscleClassifier.displayName(exercise.name)
        badge.text = group
        try {
            badge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor(MuscleClassifier.colorFor(group, fallbackColor = "#7E908E"))
            )
        } catch (_: Exception) {}

        card.findViewById<TextView>(R.id.tv_exercise_name).text = exercise.name
        card.findViewById<TextView>(R.id.tv_pr_pill).isVisible = exercise.isPr

        val setsLabel = "${exercise.workingSets} " + if (exercise.workingSets == 1) "set" else "sets"
        card.findViewById<TextView>(R.id.chip_sets).text = setsLabel
        card.findViewById<TextView>(R.id.chip_top).text =
            if (exercise.topWeightKg > 0f)
                "top ${exercise.topReps} × ${formatWeight(exercise.topWeightKg)} kg"
            else
                "top ${exercise.topReps} reps"
        card.findViewById<TextView>(R.id.chip_warmups).apply {
            isVisible = exercise.warmupSets > 0
            text = "+${exercise.warmupSets} warm-up"
        }

        // One tap → performed sets + exercise info, in one sheet.
        card.setOnClickListener { openExerciseDetail(session, exercise, date) }
        return card
    }

    private fun openExerciseDetail(
        session: HistoryBrowser.SessionSummary,
        exercise: HistoryBrowser.ExerciseSummary,
        date: LocalDate
    ) {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            val sets = viewModel.getSetsForSession(session.sessionId)
                .filter { it.exerciseName == exercise.name }
                .sortedWith(compareBy({ !it.isWarmup }, { it.setNumber }))
            if (sets.isEmpty() || !isAdded) return@launch

            val weights = FloatArray(sets.size) { sets[it].weightKg }
            val warmups = BooleanArray(sets.size) { sets[it].isWarmup }
            val dbId = ExerciseCatalog
                .findByNormalizedName(ExerciseCatalog.normalizeName(exercise.name))?.id

            ExerciseInfoBottomSheet.newInstanceForHistory(
                exerciseName = exercise.name,
                dbId = dbId,
                performedLabel = "Performed  ·  " +
                    date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                setNumbers = IntArray(sets.size) { sets[it].setNumber },
                reps = IntArray(sets.size) { sets[it].reps },
                weights = weights,
                warmups = warmups,
                prIndex = HistoryPrFlags.prSetIndex(weights, warmups, exercise.priorMaxKg)
            ).show(childFragmentManager, "history_exercise_info")
        }
    }

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
