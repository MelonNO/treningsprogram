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
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.databinding.FragmentHistoryRecapBinding
import com.migul.treningsprogram.domain.AchievementCatalog
import com.migul.treningsprogram.domain.CalorieEstimator
import com.migul.treningsprogram.domain.model.ExerciseRecap
import com.migul.treningsprogram.domain.model.SessionRecap
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Per-session recap — stage-3 H1 overhaul:
 *  - item 3: the aggregate overview graphs are gone; Recap is purely "pick a session, see it"
 *    (the Stats tab keeps all aggregates).
 *  - item 9: the muscles card lists FINE labels (Triceps, not Arms) via SessionRecap.
 *  - item 14: an "Earned this session" highlights card — achievements unlocked + new PRs.
 *  - item 10: Auros restyle — hero session header with stat chips, eyebrow section headers,
 *    vivid accents (celebratory PRs, colored muscle bars, visualized effort).
 */
@AndroidEntryPoint
class HistoryRecapFragment : Fragment() {

    private var _binding: FragmentHistoryRecapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels({ requireParentFragment() })
    private val recapTarget: RecapTargetViewModel by activityViewModels()

    private val dateFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    private val prDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private var sessions: List<WorkoutSession> = emptyList()

    private val accent = Color.parseColor("#7FE9E1")
    private val up = Color.parseColor("#37D67A")     // green — improvement
    private val neutral = Color.parseColor("#7E908E") // muted — never red, even for a down day
    private val gold = Color.parseColor("#FFD54A")   // celebration gold (tier_legendary)
    // B06: translucent accent fill for a highlighted "culprit" exercise row (recovery-muscle tap).
    private val highlightFill = Color.parseColor("#3300827C")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryRecapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSessions()
        setupWrappedEntry()
    }

    /**
     * B7 — "Monthly Wrapped": lists the ended months that have training data (newest first);
     * picking one opens the full-screen Wrapped story. Hidden while no ended month has data.
     */
    private fun setupWrappedEntry() {
        viewLifecycleOwner.lifecycleScope.launch {
            // getRecentSessions caps at recency; month availability needs ALL history.
            val months = com.migul.treningsprogram.domain.MonthlyWrapped
                .availableMonths(viewModel.allSessionsOnce())
            if (_binding == null) return@launch
            binding.btnMonthlyWrapped.isVisible = months.isNotEmpty()
            if (months.isEmpty()) return@launch
            binding.btnMonthlyWrapped.setOnClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Monthly Wrapped")
                    .setItems(months.map { it.label }.toTypedArray()) { _, which ->
                        WrappedDialogFragment.newInstance(months[which])
                            .show(parentFragmentManager, "wrapped")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
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
        // QoL 2026-08 item 02: no skeleton loader — content simply appears when ready (same
        // treatment the History list and Program screens received in v1.28.0).
        viewLifecycleOwner.lifecycleScope.launch {
            sessions = viewModel.getRecentSessions(30)
            if (_binding == null) return@launch
            binding.tvRecapEmpty.isVisible = sessions.isEmpty()
            binding.tilSession.isVisible = sessions.isNotEmpty()
            if (sessions.isEmpty()) {
                binding.layoutRecap.removeAllViews()
                return@launch
            }
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

    private fun selectSession(sessionId: Long, highlightMuscle: String? = null) {
        val idx = sessions.indexOfFirst { it.id == sessionId }.takeIf { it >= 0 } ?: 0
        binding.acSession.setText(sessionLabel(sessions[idx]), false)
        renderRecap(sessions[idx].id, highlightMuscle)
    }

    private fun sessionLabel(s: WorkoutSession): String {
        val dur = if (s.durationMinutes > 0) " · ${s.durationMinutes} min" else ""
        return "${dateFmt.format(Date(com.migul.treningsprogram.domain.DayBoundary.toLogicalMillis(s.dateMs)))}$dur"
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
            buildHero(recap)
            buildEarned(recap)
            val firstHighlightedRow = buildDeltas(recap, highlightNames)
            buildMuscleVolume(recap)
            buildEffort(recap)
            buildAdherence(recap)
            buildDuration(recap)
            buildPacing(recap)
            buildFooterTip()
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

    /**
     * Item 10: hero-style session header — eyebrow, headline date, focus muscle, and the key
     * numbers (duration / sets / volume) as styled stat chips, on the app-wide hero wash.
     */
    private fun buildHero(r: SessionRecap) {
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = requireContext().getDrawable(R.drawable.bg_hero_wash)
            setPadding(0, dp(6), 0, dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        // Eyebrow row: teal beacon dot + tracked-out label.
        val eyebrowRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        eyebrowRow.addView(View(requireContext()).apply {
            background = requireContext().getDrawable(R.drawable.bg_eyebrow_dot)
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(8) }
        })
        eyebrowRow.addView(TextView(requireContext()).apply {
            setTextAppearance(R.style.TextAppearance_Auros_Eyebrow)
            text = "Session recap"
        })
        col.addView(eyebrowRow)

        col.addView(TextView(requireContext()).apply {
            text = dateFmt.format(Date(com.migul.treningsprogram.domain.DayBoundary.toLogicalMillis(r.session.dateMs)))
            setTypeface(typeface, Typeface.BOLD)
            textSize = 22f
            letterSpacing = -0.01f
            setTextColor(requireContext().getColor(R.color.auros_snow))
        })
        if (r.focusMuscle.isNotBlank()) {
            col.addView(mutedText("${r.focusMuscle} focus").apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(2)
            })
        }

        // Stat chips: the session's key numbers.
        val chips = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        if (r.durationMinutes > 0) chips.addView(statChip("${r.durationMinutes} min"))
        chips.addView(statChip("${r.totalSets} sets"))
        chips.addView(statChip("${r.totalVolumeKg.toInt()} kg"))
        col.addView(chips)

        // QoL item 03: estimated calories as a fourth stat chip on its own row (a 4th chip in the
        // row above would clip on narrow screens). null = no figure, no chip.
        // QoL 2026-08 item 03: tapping the chip explains the estimate with the session's ACTUAL
        // numbers (same Breakdown that produced the chip figure, so they always agree).
        r.estimatedKcal?.let { kcal ->
            col.addView(statChip(com.migul.treningsprogram.domain.CalorieEstimator.format(kcal)).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8)
                r.kcalBreakdown?.let { b ->
                    isClickable = true
                    setOnClickListener { showCalorieExplanation(b) }
                }
            })
        }

        // Accent underline — the hero band signature.
        col.addView(View(requireContext()).apply {
            background = requireContext().getDrawable(R.drawable.bg_gradient_accent)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(3)).apply { topMargin = dp(14) }
        })
        binding.layoutRecap.addView(col)
    }

    /**
     * QoL 2026-08 item 03 — the calorie chip's explanation: the session's actual inputs plugged
     * into the estimator's arithmetic, step by step, ending in exactly the chip's figure.
     * Presentation (D1, delegated): a plain dialog, plain language.
     */
    private fun showCalorieExplanation(b: CalorieEstimator.Breakdown) {
        val hours = b.minutes / 60f
        val met1 = fmt1(CalorieEstimator.STRENGTH_MET)
        val cardioMet1 = fmt1(CalorieEstimator.CARDIO_MET)

        val timeLine = if (b.usedPerSetFallback)
            "Time: no duration was recorded for this session, so it is estimated from your " +
                "${b.totalSets} logged sets at ${CalorieEstimator.FALLBACK_MINUTES_PER_SET} minutes each " +
                "= ${b.minutes} minutes."
        else
            "Time: ${b.minutes} minutes (this session's logged duration)."

        val intensityLine = when {
            b.cardioSets == 0 ->
                "Intensity: ${fmt1(b.met)} — the standard level for strength training " +
                    "(rest periods included)."
            b.cardioSets == b.totalSets ->
                "Intensity: ${fmt1(b.met)} — the standard level for cardio."
            else ->
                "Intensity: ${b.cardioSets} of your ${b.totalSets} sets were cardio, so the level " +
                    "blends strength ($met1) and cardio ($cardioMet1) into ${fmt1(b.met)}."
        }

        val weightLine = when (b.bodyWeightSource) {
            CalorieEstimator.BodyWeightSource.WEIGH_IN ->
                "Body weight: ${fmt(b.bodyWeightKg)} kg — your most recent weigh-in before this session."
            CalorieEstimator.BodyWeightSource.EARLIEST_WEIGH_IN ->
                "Body weight: ${fmt(b.bodyWeightKg)} kg — your earliest logged weigh-in (none " +
                    "existed before this session yet)."
            CalorieEstimator.BodyWeightSource.DEFAULT ->
                "Body weight: ${fmt(b.bodyWeightKg)} kg — a default value, since no weigh-in has " +
                    "been logged. Log your weight for a more accurate estimate."
        }

        val message =
            "Calories are estimated as intensity × body weight × time:\n\n" +
                "• $timeLine\n\n" +
                "• $intensityLine\n\n" +
                "• $weightLine\n\n" +
                "Putting it together:\n" +
                "${fmt1(b.met)} × ${fmt(b.bodyWeightKg)} kg × " +
                "${String.format(Locale.US, "%.2f", hours)} h = ${b.rawKcal.toInt()} kcal\n\n" +
                "Rounded to the nearest 10: ${CalorieEstimator.format(b.kcal)}.\n\n" +
                "This is a rough estimate — actual burn varies with pace, technique, and physiology."

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("How this estimate was made")
            .setMessage(message)
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun fmt1(v: Float): String = String.format(Locale.US, "%.1f", v)

    /** A rounded stat pill: reef fill, luminous cyan number text. */
    private fun statChip(text: String): View = TextView(requireContext()).apply {
        this.text = text
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(accent)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(requireContext().getColor(R.color.auros_reef))
            cornerRadius = dp(20).toFloat()
        }
        setPadding(dp(12), dp(6), dp(12), dp(6))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8) }
    }

    /**
     * Item 14: "Earned this session" — the achievements this session unlocked (with their R5
     * rarity styling) and the new heaviest-weight PRs, folded into one highlights story
     * (A-14b). Sessions that earned nothing simply don't get the card.
     */
    private fun buildEarned(r: SessionRecap) {
        val newPrs = r.exercises.filter { it.isPrThisSession && it.topWeightKg > 0f }
        if (newPrs.isEmpty() && r.earnedAchievements.isEmpty()) return
        val card = card()
        val col = cardColumn(card)
        col.addView(eyebrow("Earned this session"))

        r.earnedAchievements.forEach { a -> col.addView(achievementRow(a)) }

        newPrs.forEach { ex ->
            col.addView(TextView(requireContext()).apply {
                text = "🏆 ${ex.exerciseName} — new heaviest weight ${fmt(ex.topWeightKg)} kg!"
                textSize = 13f
                setTextColor(gold)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(5), 0, dp(5))
            })
        }
        col.addView(mutedText("Full PR history (estimated 1RM) is on the Progress tab.").apply {
            textSize = 11f
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
        })
        binding.layoutRecap.addView(card)
    }

    /** One unlocked achievement: emoji + name + rarity chip, consistent with the R5 gallery. */
    private fun achievementRow(a: Achievement): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
        }
        row.addView(TextView(requireContext()).apply {
            text = a.emoji
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) }
        })
        val nameCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameCol.addView(TextView(requireContext()).apply {
            text = a.name
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.auros_snow))
        })
        if (a.description.isNotBlank()) {
            nameCol.addView(mutedText(a.description).apply { textSize = 12f })
        }
        row.addView(nameCol)
        val meta = AchievementCatalog.metaFor(a.id)
        if (meta != null) {
            row.addView(TextView(requireContext()).apply {
                text = meta.tier.label.uppercase()
                textSize = 10f
                letterSpacing = 0.08f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(tierColor(meta.tier))
            })
        }
        return row
    }

    private fun tierColor(tier: AchievementCatalog.Tier): Int = requireContext().getColor(
        when (tier) {
            AchievementCatalog.Tier.COMMON -> R.color.tier_common
            AchievementCatalog.Tier.RARE -> R.color.tier_rare
            AchievementCatalog.Tier.EPIC -> R.color.tier_epic
            AchievementCatalog.Tier.LEGENDARY -> R.color.tier_legendary
        }
    )

    /**
     * Builds the "Vs. last time" card. When [highlightNames] is non-empty (B06: arriving from a
     * recovery-muscle tap), every matching exercise row is visually highlighted. Returns the first
     * highlighted row (or null) so the caller can scroll it into view.
     */
    private fun buildDeltas(r: SessionRecap, highlightNames: Set<String> = emptySet()): View? {
        val card = card()
        val col = cardColumn(card)
        col.addView(eyebrow("Vs. last time"))
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
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.auros_snow))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(requireContext()).apply {
            // F2 (v1.24.1): bodyweight work (0 kg) reads "BW × reps", matching the log screen's
            // "BW" label — "0 kg × 6" read as no load.
            text = when {
                ex.isCardio -> "${ex.totalReps} reps · ${ex.sets} sets"
                ex.topWeightKg > 0f -> "${fmt(ex.topWeightKg)} kg × ${ex.topReps}"
                else -> "BW × ${ex.topReps}"
            }
            textSize = 13f
            setTextColor(if (ex.isPrThisSession) gold else neutral)
            if (ex.isPrThisSession) setTypeface(typeface, Typeface.BOLD)
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
        // Item 14 information parity: the old "Heaviest weight" card's existing-best reference
        // lives here now — per exercise, the best on record and when it was set.
        if (!ex.isCardio && !ex.isPrThisSession &&
            ex.existingPrWeightKg != null && ex.existingPrWeightKg > 0f
        ) {
            val whenStr = ex.existingPrDateMs?.let { " · ${relativeTime(it)}" } ?: ""
            row.addView(mutedText("Best on record: ${fmt(ex.existingPrWeightKg)} kg$whenStr").apply {
                textSize = 11f
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(2)
            })
        }
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

    /** Item 9: fine muscle labels; bars coloured by the label's broad group (implicit legend). */
    private fun buildMuscleVolume(r: SessionRecap) {
        if (r.muscleVolume.isEmpty()) return
        val card = card()
        val col = cardColumn(card)
        col.addView(eyebrow("Muscles hit this session"))
        val maxSets = r.muscleVolume.maxOf { it.second }.coerceAtLeast(1)
        r.muscleVolume.forEach { (muscle, sets) -> col.addView(muscleBarRow(muscle, sets, maxSets)) }
        col.addView(mutedText("Weighted by each exercise's muscle contributions — same model as the recovery panel.").apply {
            textSize = 11f
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
        })
        binding.layoutRecap.addView(card)
    }

    /**
     * One coloured horizontal bar row for a (fine) muscle label. Colour comes from the label's
     * BROAD group via the app-wide [MuscleClassifier] mapping, so Triceps/Biceps share the Arms
     * hue, all delts the Shoulders hue, etc. — an implicit legend.
     */
    private fun muscleBarRow(muscle: String, sets: Int, maxSets: Int): View {
        val barColor = Color.parseColor(
            MuscleClassifier.colorFor(MuscleClassifier.broadGroupFor(muscle), "#7E908E")
        )
        val rowLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        rowLayout.addView(TextView(requireContext()).apply {
            text = muscle
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        rowLayout.addView(View(requireContext()).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(barColor)
                cornerRadius = dp(3).toFloat()
            }
            val w = (dp(114) * sets / maxSets).coerceAtLeast(dp(6))
            layoutParams = LinearLayout.LayoutParams(w, dp(10)).apply { marginEnd = dp(8) }
        })
        rowLayout.addView(TextView(requireContext()).apply {
            text = if (sets == 1) "1 set" else "$sets sets"
            textSize = 12f
            setTextColor(barColor)
        })
        return rowLayout
    }

    /** Item 10: effort visualized — a proportional segmented bar plus a coloured legend. */
    private fun buildEffort(r: SessionRecap) {
        if (r.effort.isEmpty()) return
        val card = card()
        val col = cardColumn(card)
        col.addView(eyebrow("Effort"))

        val colors = mapOf("Easy" to up, "Moderate" to accent, "Hard" to gold)
        val bar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10)
            ).apply { bottomMargin = dp(8) }
        }
        r.effort.forEach { (label, count) ->
            bar.addView(View(requireContext()).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(colors[label] ?: neutral)
                    cornerRadius = dp(3).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(10), count.toFloat()).apply {
                    marginEnd = dp(2)
                }
            })
        }
        col.addView(bar)

        val legend = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        r.effort.forEach { (label, count) ->
            legend.addView(TextView(requireContext()).apply {
                text = "$label $count"
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colors[label] ?: neutral)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(14) }
            })
        }
        col.addView(legend)
        col.addView(mutedText("Working sets by logged effort.").apply {
            textSize = 11f
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
        })
        binding.layoutRecap.addView(card)
    }

    private fun buildAdherence(r: SessionRecap) {
        if (r.plannedSets == null) return
        val card = card()
        val col = cardColumn(card)
        col.addView(eyebrow("Adherence"))
        col.addView(TextView(requireContext()).apply {
            text = "Completed ${r.totalSets} of ${r.plannedSets} planned sets"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onSurface())
        })
        // A slim completion bar: accent fill over the inert track.
        if (r.plannedSets > 0) {
            val frac = (r.totalSets.toFloat() / r.plannedSets).coerceIn(0f, 1f)
            val track = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(requireContext().getColor(R.color.auros_reef_dim))
                    cornerRadius = dp(3).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
                ).apply { topMargin = dp(8) }
            }
            if (frac > 0f) {
                track.addView(View(requireContext()).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(accent)
                        cornerRadius = dp(3).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(0, dp(8), frac)
                })
                track.addView(View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(8), 1f - frac)
                })
            }
            col.addView(track)
        }
        if (r.skippedExercises.isNotEmpty()) {
            col.addView(mutedText("Skipped: ${r.skippedExercises.joinToString(", ")}").apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
            })
        }
        binding.layoutRecap.addView(card)
    }

    private fun buildDuration(r: SessionRecap) {
        if (r.estimatedMinutes == null || r.durationMinutes <= 0) return
        val card = card()
        val col = cardColumn(card)
        col.addView(eyebrow("Duration"))
        col.addView(mutedText("Planned ~${r.estimatedMinutes} min  ·  Actual ${r.durationMinutes} min"))
        binding.layoutRecap.addView(card)
    }

    private fun buildPacing(r: SessionRecap) {
        val p = r.pacing ?: return
        val card = card()
        val col = cardColumn(card)
        col.addView(eyebrow("Rest & pacing"))

        // Rest adherence — neutral framing, never red.
        val restLine = if (p.targetRestSeconds != null) {
            "Avg rest ${fmtDur(p.avgRestSeconds)}  ·  target ~${fmtDur(p.targetRestSeconds)}"
        } else {
            "Avg rest ${fmtDur(p.avgRestSeconds)} between sets"
        }
        col.addView(TextView(requireContext()).apply {
            text = restLine
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
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

    /** The hero chips already carry the totals — only the drill-in tip remains down here. */
    private fun buildFooterTip() {
        binding.layoutRecap.addView(mutedText("Tip: tap an exercise above to see its trend over time.").apply {
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(12)
            }
        })
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
        strokeColor = requireContext().getColor(R.color.auros_hairline)
        setCardBackgroundColor(requireContext().getColor(R.color.auros_trench))
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

    /** Item 10: eyebrow-style section header (tracked-out uppercase label). */
    private fun eyebrow(text: String) = TextView(requireContext()).apply {
        setTextAppearance(R.style.TextAppearance_Auros_Eyebrow)
        this.text = text
        setPadding(0, 0, 0, dp(10))
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
        // Item 7 day boundary: LOGICAL local days, so "yesterday" flips at the configured cutoff
        // (default 04:00) like everywhere else — not at a UTC midnight offset by the timezone.
        val days = (com.migul.treningsprogram.domain.DayBoundary.todayEpochDay() -
            com.migul.treningsprogram.domain.DayBoundary.logicalEpochDay(ms)).toInt()
        return when {
            days <= 0 -> "today"
            days == 1 -> "yesterday"
            days < 7 -> "$days days ago"
            days < 14 -> "1 week ago"
            days < 30 -> "${days / 7} weeks ago"
            days < 60 -> "1 month ago"
            days < 365 -> "${days / 30} months ago"
            else -> prDateFmt.format(Date(com.migul.treningsprogram.domain.DayBoundary.toLogicalMillis(ms)))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /**
         * B06 (pure, unit-tested): given the exercise names logged in a session and a tapped
         * [muscle] label, returns the subset of names whose [MuscleClassifier.finerMusclesFor]
         * attribution includes that muscle — i.e. the exercises that drove that muscle's fatigue and
         * should be highlighted in the session view. Match is case-insensitive on the muscle label;
         * a blank muscle yields an empty set.
         *
         * Stage-3 item 11: the label may also be a BROAD group ("Arms", "Legs" — the heatmap's
         * rows); it then matches every exercise whose fine labels roll up to that group.
         */
        fun exercisesHittingMuscle(exerciseNames: List<String>, muscle: String?): Set<String> {
            val target = muscle?.trim().orEmpty()
            if (target.isEmpty()) return emptySet()
            return exerciseNames.filter { name ->
                MuscleClassifier.finerMusclesFor(name).any { (label, _) ->
                    label.equals(target, ignoreCase = true) ||
                        MuscleClassifier.broadGroupFor(label).equals(target, ignoreCase = true)
                }
            }.toSet()
        }
    }
}
