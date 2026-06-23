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
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.databinding.FragmentHistoryRecapBinding
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
            selectSession(target)
        }
    }

    private fun loadSessions() {
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
            val initial = requested?.takeIf { id -> sessions.any { it.id == id } } ?: sessions.first().id
            selectSession(initial)
        }
    }

    private fun selectSession(sessionId: Long) {
        val idx = sessions.indexOfFirst { it.id == sessionId }.takeIf { it >= 0 } ?: 0
        binding.acSession.setText(sessionLabel(sessions[idx]), false)
        renderRecap(sessions[idx].id)
    }

    private fun sessionLabel(s: WorkoutSession): String {
        val dur = if (s.durationMinutes > 0) " · ${s.durationMinutes} min" else ""
        return "${dateFmt.format(Date(s.dateMs))}$dur"
    }

    private fun renderRecap(sessionId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val recap = viewModel.buildRecap(sessionId)
            if (_binding == null) return@launch
            binding.layoutRecap.removeAllViews()
            if (recap == null) {
                binding.layoutRecap.addView(mutedText("This session has no logged sets."))
                return@launch
            }
            buildHeader(recap)
            buildDeltas(recap)
            buildPrs(recap)
            buildMuscleVolume(recap)
            buildEffort(recap)
            buildAdherence(recap)
            buildDuration(recap)
            buildTotals(recap)
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

    private fun buildDeltas(r: SessionRecap) {
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Vs. last time"))
        r.exercises.forEach { ex -> col.addView(deltaRow(ex, r)) }
        binding.layoutRecap.addView(card)
    }

    private fun deltaRow(ex: ExerciseRecap, r: SessionRecap): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { openTrends(ex.exerciseName, r.session.dateMs) }
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
        col.addView(sectionTitle("Personal records"))
        newPrs.forEach { ex ->
            col.addView(TextView(requireContext()).apply {
                text = "🎉 ${ex.exerciseName} — new best ${fmt(ex.topWeightKg)} kg!"
                textSize = 13f
                setTextColor(accent)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        existing.forEach { ex ->
            val whenStr = ex.existingPrDateMs?.let { "  ·  ${relativeTime(it)}" } ?: ""
            col.addView(mutedText("${ex.exerciseName} — PR: ${fmt(ex.existingPrWeightKg!!)} kg$whenStr").apply {
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        binding.layoutRecap.addView(card)
    }

    private fun buildMuscleVolume(r: SessionRecap) {
        if (r.muscleVolume.isEmpty()) return
        val card = card()
        val col = cardColumn(card)
        col.addView(sectionTitle("Muscles hit this session"))
        val maxSets = r.muscleVolume.maxOf { it.second }.coerceAtLeast(1)
        r.muscleVolume.forEach { (muscle, sets) ->
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
            // proportional bar
            rowLayout.addView(View(requireContext()).apply {
                setBackgroundColor(accent)
                val w = (dp(120) * sets / maxSets).coerceAtLeast(dp(6))
                layoutParams = LinearLayout.LayoutParams(w, dp(8)).apply { marginEnd = dp(8) }
            })
            rowLayout.addView(TextView(requireContext()).apply {
                text = if (sets == 1) "1 set" else "$sets sets"
                textSize = 12f
                setTextColor(neutral)
            })
            col.addView(rowLayout)
        }
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
}
