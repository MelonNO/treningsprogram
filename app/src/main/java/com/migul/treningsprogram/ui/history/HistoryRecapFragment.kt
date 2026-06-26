package com.migul.treningsprogram.ui.history

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.databinding.FragmentHistoryRecapBinding
import com.migul.treningsprogram.domain.RecapGraphs
import com.migul.treningsprogram.domain.model.ExerciseRecap
import com.migul.treningsprogram.domain.model.SessionRecap
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class HistoryRecapFragment : Fragment() {

    private var _binding: FragmentHistoryRecapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels({ requireParentFragment() })
    private val recapTarget: RecapTargetViewModel by activityViewModels()

    private val dateFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    private val prDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private var sessions: List<WorkoutSession> = emptyList()

    private val accent = Color.parseColor("#7C67F5")
    private val up = Color.parseColor("#5CCB7E")     // green — improvement
    private val neutral = Color.parseColor("#9A9AB0") // muted — never red, even for a down day
    // B06: translucent accent fill for a highlighted "culprit" exercise row (recovery-muscle tap).
    private val highlightFill = Color.parseColor("#332C5BE6")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryRecapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSessions()
    }

    override fun onResume() {
        super.onResume()
        // An external entry point may have requested a specific session while we were away.
        val target = recapTarget.pendingSessionId
        if (target != null && sessions.any { it.id == target }) {
            recapTarget.pendingSessionId = null
            // B06: one-shot — only highlight when arriving via a recovery-muscle tap.
            selectSession(target, highlightMuscle = recapTarget.consumeHighlightMuscle())
        }
    }

    private fun loadSessions() {
        viewLifecycleOwner.lifecycleScope.launch {
            sessions = viewModel.getRecentSessions(30)
            if (_binding == null) return@launch
            binding.tvRecapEmpty.isVisible = sessions.isEmpty()
            binding.tilSession.isVisible = sessions.isNotEmpty()
            binding.layoutOverview.isVisible = sessions.isNotEmpty()
            if (sessions.isEmpty()) {
                binding.layoutRecap.removeAllViews()
                binding.layoutOverview.removeAllViews()
                return@launch
            }
            renderOverview()
            val labels = sessions.map { sessionLabel(it) }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
            binding.acSession.setAdapter(adapter)
            binding.acSession.setOnItemClickListener { _, _, pos, _ ->
                renderRecap(sessions[pos].id)
                binding.acSession.setText(labels[pos], false)
            }
            // Pick the requested session (from an entry point) or default to the latest.
            val requested = recapTarget.pendingSessionId?.also { recapTarget.pendingSessionId = null }
            val matched = requested?.takeIf { id -> sessions.any { it.id == id } }
            val initial = matched ?: sessions.first().id
            // B06: one-shot highlight only when this open came from a recovery-muscle tap
            // (i.e. an explicit requested session was matched), never for the default latest.
            val highlight = if (matched != null) recapTarget.consumeHighlightMuscle() else null
            selectSession(initial, highlightMuscle = highlight)
        }
    }

    // ── Overview graphs (UX1) ──────────────────────────────────────────────────
    // Aggregate trends across all sessions, rendered once (not per-session). Built from
    // warm-up-excluded data already exposed on the ViewModel; shaped by RecapGraphs (unit-tested).

    private fun renderOverview() {
        viewLifecycleOwner.lifecycleScope.launch {
            val volume = viewModel.getWeeklyVolumePoints()
            val frequency = viewModel.getWeeklyFrequencyPoints()
            val muscles = viewModel.getMuscleRows()
            if (_binding == null) return@launch
            binding.layoutOverview.removeAllViews()
            // B07: build only the cards that have data; empty ones are skipped (hidden) rather than
            // showing per-field "log more to see X" copy. The Overview header (and the whole layout)
            // appears only if at least one overview card is present.
            val cards = listOfNotNull(
                buildVolumeOverTimeCard(volume),
                buildFrequencyCard(frequency),
                buildMuscleDistributionCard(muscles)
            )
            binding.layoutOverview.isVisible = cards.isNotEmpty()
            if (cards.isEmpty()) return@launch
            binding.layoutOverview.addView(overviewHeader())
            cards.forEach { binding.layoutOverview.addView(it) }
        }
    }

    private fun overviewHeader(): View {
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        col.addView(TextView(requireContext()).apply {
            text = "Overview"
            setTypeface(null, Typeface.BOLD)
            textSize = 18f
            setTextColor(onSurface())
        })
        col.addView(mutedText("Trends across your recent training · working sets only").apply {
            textSize = 12f
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(2)
        })
        return col
    }

    /** B07: returns null (card hidden) when the chart can't draw (< 2 weekly points). */
    private fun buildVolumeOverTimeCard(points: List<RecapGraphs.WeekPoint>): View? {
        if (points.size < 2) return null
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Volume over time"))
        col.addView(mutedText("Total working sets per week").apply {
            textSize = 12f
            (layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(8)
        })
        addChart(col, points, label = "sets")
        return card
    }

    /** B07: returns null (card hidden) when the chart can't draw (< 2 weekly points). */
    private fun buildFrequencyCard(points: List<RecapGraphs.WeekPoint>): View? {
        if (points.size < 2) return null
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Training frequency"))
        col.addView(mutedText("Workout days per week").apply {
            textSize = 12f
            (layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(8)
        })
        addChart(col, points, label = "sessions")
        return card
    }

    /** B07: returns null (card hidden) when there is no muscle-group data yet. */
    private fun buildMuscleDistributionCard(rows: List<RecapGraphs.MuscleRow>): View? {
        if (rows.isEmpty()) return null
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Muscle group distribution"))
        col.addView(mutedText("All-time working sets per muscle group (sets)").apply {
            textSize = 12f
            (layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(8)
        })
        // Categorical data → labelled horizontal bars (clearer than a line chart for categories).
        val maxSets = rows.maxOf { it.sets }.coerceAtLeast(1)
        rows.forEach { (muscle, sets) -> col.addView(muscleBarRow(muscle, sets, maxSets)) }
        return card
    }

    /**
     * One coloured horizontal bar row for a muscle group, shared by the Overview "Muscle group
     * distribution" and the per-session "Muscles hit" sections so both read the same. Bars are
     * coloured by muscle group via the app-wide [MuscleClassifier.colorFor] mapping (UX1 restyle);
     * the count text reuses that colour so the legend is implicit.
     */
    private fun muscleBarRow(muscle: String, sets: Int, maxSets: Int): View {
        val barColor = Color.parseColor(MuscleClassifier.colorFor(muscle, "#607D8B"))
        val rowLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        rowLayout.addView(TextView(requireContext()).apply {
            text = muscle
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        rowLayout.addView(View(requireContext()).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(barColor)
                cornerRadius = dp(3).toFloat()
            }
            val w = (dp(120) * sets / maxSets).coerceAtLeast(dp(6))
            layoutParams = LinearLayout.LayoutParams(w, dp(10)).apply { marginEnd = dp(8) }
        })
        rowLayout.addView(TextView(requireContext()).apply {
            text = if (sets == 1) "1 set" else "$sets sets"
            textSize = 12f
            setTextColor(barColor)
        })
        return rowLayout
    }

    /**
     * Adds a [StrengthChartView] for [points]. B07: callers only invoke this when the card has
     * enough data to draw (>= 2 points), so there is no per-field empty branch here — an empty
     * card is simply not built (the whole card is hidden instead of showing guidance copy).
     */
    private fun addChart(
        col: LinearLayout,
        points: List<RecapGraphs.WeekPoint>,
        label: String
    ) {
        val chart = StrengthChartView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(168)
            )
            // These overview series are whole counts (sets / sessions) — render integer labels.
            setData(
                points.map { StrengthChartView.Entry(it.weekStartMs, it.value) },
                label,
                integerValues = true
            )
        }
        col.addView(chart)
    }

    private fun selectSession(sessionId: Long, highlightMuscle: String? = null) {
        val idx = sessions.indexOfFirst { it.id == sessionId }.takeIf { it >= 0 } ?: 0
        binding.acSession.setText(sessionLabel(sessions[idx]), false)
        renderRecap(sessions[idx].id, highlightMuscle)
    }

    private fun sessionLabel(s: WorkoutSession): String {
        val dur = if (s.durationMinutes > 0) " · ${s.durationMinutes} min" else ""
        return "${dateFmt.format(Date(s.dateMs))}$dur"
    }

    private fun renderRecap(sessionId: Long, highlightMuscle: String? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            val recap = viewModel.buildRecap(sessionId)
            if (_binding == null) return@launch
            binding.layoutRecap.removeAllViews()
            if (recap == null) {
                binding.layoutRecap.addView(mutedText("This session has no logged sets."))
                return@launch
            }
            // B06: when arriving via a recovery-muscle tap, work out which exercises in this
            // session hit the tapped muscle so the deltas section can highlight them.
            val highlightNames = if (highlightMuscle.isNullOrBlank()) emptySet()
                else exercisesHittingMuscle(recap.exercises.map { it.exerciseName }, highlightMuscle)
            buildHeader(recap)
            val firstHighlightedRow = buildDeltas(recap, highlightNames)
            buildPrs(recap)
            buildMuscleVolume(recap)
            buildEffort(recap)
            buildAdherence(recap)
            buildDuration(recap)
            buildPacing(recap)
            buildTotals(recap)
            // Scroll the first highlighted exercise into view once layout is complete.
            firstHighlightedRow?.let { scrollRowIntoView(it) }
        }
    }

    /** Scrolls the recap's NestedScrollView so [row] is comfortably visible. */
    private fun scrollRowIntoView(row: View) {
        row.post {
            if (_binding == null) return@post
            val scroll = binding.root as? androidx.core.widget.NestedScrollView ?: return@post
            // Sum offsets up the view tree to get the row's top within the scroll content.
            var y = 0
            var v: View? = row
            while (v != null && v !== scroll) {
                y += v.top
                v = v.parent as? View
            }
            scroll.smoothScrollTo(0, (y - dp(12)).coerceAtLeast(0))
        }
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private fun buildHeader(r: SessionRecap) {
        val card = card()
        val col = cardColumn(card)
        col.addView(TextView(requireContext()).apply {
            text = dateFmt.format(Date(r.session.dateMs))
            setTypeface(null, Typeface.BOLD)
            textSize = 18f
            setTextColor(onSurface())
        })
        val bits = buildList {
            if (r.focusMuscle.isNotBlank()) add(r.focusMuscle)
            if (r.durationMinutes > 0) add("${r.durationMinutes} min")
        }
        if (bits.isNotEmpty()) {
            col.addView(mutedText(bits.joinToString("  ·  ")).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(2)
            })
        }
        binding.layoutRecap.addView(card)
    }

    /**
     * Builds the "Vs. last time" card. When [highlightNames] is non-empty (B06: arriving from a
     * recovery-muscle tap), every matching exercise row is visually highlighted. Returns the first
     * highlighted row (or null) so the caller can scroll it into view.
     */
    private fun buildDeltas(r: SessionRecap, highlightNames: Set<String> = emptySet()): View? {
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Vs. last time"))
        var firstHighlighted: View? = null
        r.exercises.forEach { ex ->
            val highlighted = ex.exerciseName in highlightNames
            val row = deltaRow(ex, r, highlighted)
            if (highlighted && firstHighlighted == null) firstHighlighted = row
            col.addView(row)
        }
        binding.layoutRecap.addView(card)
        return firstHighlighted
    }

    private fun deltaRow(ex: ExerciseRecap, r: SessionRecap, highlighted: Boolean = false): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { openTrends(ex.exerciseName, r.session.dateMs) }
        }
        if (highlighted) {
            // B06: tint + left accent bar so the muscle's "culprit" exercises stand out.
            row.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(highlightFill)
                cornerRadius = dp(8).toFloat()
            }
            row.setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val top = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(requireContext()).apply {
            text = ex.exerciseName
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(requireContext()).apply {
            text = if (ex.isCardio) "${ex.totalReps} reps · ${ex.sets} sets"
                   else "${fmt(ex.topWeightKg)} kg × ${ex.topReps}"
            textSize = 13f
            setTextColor(neutral)
        })
        row.addView(top)
        row.addView(TextView(requireContext()).apply {
            val (label, color) = deltaText(ex)
            text = label
            setTextColor(color)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        })
        return row
    }

    private fun deltaText(ex: ExerciseRecap): Pair<String, Int> {
        if (ex.prevTopWeightKg == null && ex.prevTopReps == null) {
            return "First time — baseline" to neutral
        }
        if (ex.isCardio) {
            val prev = ex.prevTopReps ?: return "First time — baseline" to neutral
            val d = ex.topReps - prev
            return when {
                d > 0 -> "▲ +$d reps vs last" to up
                d < 0 -> "▼ $d reps vs last" to neutral
                else  -> "Same as last time" to neutral
            }
        }
        val prevW = ex.prevTopWeightKg ?: return "First time — baseline" to neutral
        val dw = ex.topWeightKg - prevW
        return when {
            dw > 0f -> "▲ +${fmt(dw)} kg vs last" to up
            dw < 0f -> "▼ ${fmt(dw)} kg vs last" to neutral
            else -> {
                val dr = ex.topReps - (ex.prevTopReps ?: ex.topReps)
                when {
                    dr > 0 -> "▲ +$dr reps at same weight" to up
                    dr < 0 -> "▼ $dr reps at same weight" to neutral
                    else   -> "Same as last time" to neutral
                }
            }
        }
    }

    private fun buildPrs(r: SessionRecap) {
        val newPrs = r.exercises.filter { it.isPrThisSession && it.topWeightKg > 0f }
        val existing = r.exercises.filter { !it.isPrThisSession && it.existingPrWeightKg != null && it.existingPrWeightKg > 0f }
        if (newPrs.isEmpty() && existing.isEmpty()) return
        val card = card()
        val col = cardColumn(card)
        // Terminology (UX1/F2): this section is the in-session HEAVIEST-WEIGHT milestone, kept
        // distinct from the Progress tab's "PR history (estimated 1RM)" so the two don't contradict.
        col.addView(sectionTitle("Heaviest weight"))
        newPrs.forEach { ex ->
            col.addView(TextView(requireContext()).apply {
                text = "🎉 ${ex.exerciseName} — new heaviest weight ${fmt(ex.topWeightKg)} kg!"
                textSize = 13f
                setTextColor(accent)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        existing.forEach { ex ->
            val whenStr = ex.existingPrDateMs?.let { "  ·  ${relativeTime(it)}" } ?: ""
            col.addView(mutedText("${ex.exerciseName} — heaviest ${fmt(ex.existingPrWeightKg!!)} kg$whenStr").apply {
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        col.addView(mutedText("Full PR history (estimated 1RM) is on the Progress tab.").apply {
            textSize = 11f
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
        })
        binding.layoutRecap.addView(card)
    }

    private fun buildMuscleVolume(r: SessionRecap) {
        if (r.muscleVolume.isEmpty()) return
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Muscles hit this session"))
        val maxSets = r.muscleVolume.maxOf { it.second }.coerceAtLeast(1)
        r.muscleVolume.forEach { (muscle, sets) -> col.addView(muscleBarRow(muscle, sets, maxSets)) }
        binding.layoutRecap.addView(card)
    }

    private fun buildEffort(r: SessionRecap) {
        if (r.effort.isEmpty()) return
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Effort"))
        col.addView(mutedText(r.effort.joinToString("    ") { "${it.first}: ${it.second}" }))
        binding.layoutRecap.addView(card)
    }

    private fun buildAdherence(r: SessionRecap) {
        if (r.plannedSets == null) return
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Adherence"))
        col.addView(TextView(requireContext()).apply {
            text = "Completed ${r.totalSets} of ${r.plannedSets} planned sets"
            textSize = 13f
            setTextColor(onSurface())
        })
        if (r.skippedExercises.isNotEmpty()) {
            col.addView(mutedText("Skipped: ${r.skippedExercises.joinToString(", ")}").apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(4)
            })
        }
        binding.layoutRecap.addView(card)
    }

    private fun buildDuration(r: SessionRecap) {
        if (r.estimatedMinutes == null || r.durationMinutes <= 0) return
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Duration"))
        col.addView(mutedText("Planned ~${r.estimatedMinutes} min  ·  Actual ${r.durationMinutes} min"))
        binding.layoutRecap.addView(card)
    }

    private fun buildPacing(r: SessionRecap) {
        val p = r.pacing ?: return
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Rest & pacing"))

        // Rest adherence — neutral framing, never red.
        val restLine = if (p.targetRestSeconds != null) {
            "Avg rest ${fmtDur(p.avgRestSeconds)}  ·  target ~${fmtDur(p.targetRestSeconds)}"
        } else {
            "Avg rest ${fmtDur(p.avgRestSeconds)} between sets"
        }
        col.addView(TextView(requireContext()).apply {
            text = restLine
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(onSurface())
        })
        if (p.targetRestSeconds != null) {
            val diff = p.avgRestSeconds - p.targetRestSeconds
            val tol = 20
            val (verdict, color) = when {
                kotlin.math.abs(diff) <= tol -> "On target" to up
                diff > 0 -> "Resting ${fmtDur(diff)} longer than prescribed" to neutral
                else -> "Resting ${fmtDur(-diff)} shorter than prescribed" to neutral
            }
            col.addView(TextView(requireContext()).apply {
                text = verdict
                textSize = 12f
                setTextColor(color)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(2) }
            })
        }

        // Measured between-set time split into rest vs long pauses (idle).
        val totalGap = p.restSeconds + p.idleSeconds
        if (totalGap > 0 && p.idleSeconds > 0) {
            val bar = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(10)
                ).apply { topMargin = dp(10) }
            }
            bar.addView(View(requireContext()).apply {
                setBackgroundColor(accent)
                layoutParams = LinearLayout.LayoutParams(0, dp(10), p.restSeconds.toFloat())
            })
            bar.addView(View(requireContext()).apply {
                setBackgroundColor(neutral)
                layoutParams = LinearLayout.LayoutParams(0, dp(10), p.idleSeconds.toFloat())
            })
            col.addView(bar)
            col.addView(mutedText("Rest ${fmtDur(p.restSeconds)}  ·  idle ${fmtDur(p.idleSeconds)} in ${p.longPauseCount} long pause${if (p.longPauseCount == 1) "" else "s"}").apply {
                textSize = 12f
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(4)
            })
        }

        col.addView(mutedText("Between-set time includes the next set's work, so this reflects pacing — not effort.").apply {
            textSize = 11f
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
        })
        binding.layoutRecap.addView(card)
    }

    private fun buildTotals(r: SessionRecap) {
        val card = card()
        val col = cardColumn(card)
        val bits = buildList {
            add("${r.totalVolumeKg.toInt()} kg volume")
            add("${r.totalSets} sets")
            if (r.durationMinutes > 0) add("${r.durationMinutes} min")
        }
        col.addView(mutedText(bits.joinToString("  ·  ")).apply { textSize = 12f })
        col.addView(mutedText("Tip: tap an exercise above to see its trend over time.").apply {
            textSize = 11f
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
        })
        binding.layoutRecap.addView(card)
    }

    private fun openTrends(exerciseName: String, sessionDateMs: Long) {
        if (findNavController().currentDestination?.id == R.id.historyFragment)
            findNavController().navigate(
                R.id.recapTrendsFragment,
                bundleOf("exerciseName" to exerciseName, "sessionDateMs" to sessionDateMs)
            )
    }

    // ── View helpers ───────────────────────────────────────────────────────────

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun onSurface(): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnSurface, tv, true
        )
        return tv.data
    }

    private fun card(): MaterialCardView = MaterialCardView(requireContext()).apply {
        radius = dp(16).toFloat()
        strokeWidth = dp(1)
        strokeColor = requireContext().getColor(com.google.android.material.R.color.material_on_background_emphasis_medium)
        cardElevation = 0f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
    }

    private fun cardColumn(card: MaterialCardView): LinearLayout {
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(col)
        return col
    }

    private fun sectionTitle(text: String) = TextView(requireContext()).apply {
        this.text = text
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setTextColor(onSurface())
        (this.layoutParams as? LinearLayout.LayoutParams)
        setPadding(0, 0, 0, dp(8))
    }

    private fun mutedText(text: String) = TextView(requireContext()).apply {
        this.text = text
        textSize = 13f
        setTextColor(neutral)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fmt(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    /** Seconds → "M:SS" (under an hour) or "Hh Mm". */
    private fun fmtDur(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return if (s < 3600) "%d:%02d".format(s / 60, s % 60)
        else "%dh %dm".format(s / 3600, (s % 3600) / 60)
    }

    private fun relativeTime(ms: Long): String {
        val days = ((System.currentTimeMillis() - ms) / 86_400_000L).toInt()
        return when {
            days <= 0 -> "today"
            days == 1 -> "yesterday"
            days < 7 -> "$days days ago"
            days < 14 -> "1 week ago"
            days < 30 -> "${days / 7} weeks ago"
            days < 60 -> "1 month ago"
            days < 365 -> "${days / 30} months ago"
            else -> prDateFmt.format(Date(ms))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /**
         * B06 (pure, unit-tested): given the exercise names logged in a session and a tapped
         * fine-grain [muscle] label, returns the subset of names whose [MuscleClassifier.finerMusclesFor]
         * attribution includes that muscle — i.e. the exercises that drove that muscle's fatigue and
         * should be highlighted in the session view. Match is case-insensitive on the muscle label;
         * a blank muscle yields an empty set.
         */
        fun exercisesHittingMuscle(exerciseNames: List<String>, muscle: String?): Set<String> {
            val target = muscle?.trim().orEmpty()
            if (target.isEmpty()) return emptySet()
            return exerciseNames.filter { name ->
                MuscleClassifier.finerMusclesFor(name).any { (label, _) -> label.equals(target, ignoreCase = true) }
            }.toSet()
        }
    }
}
