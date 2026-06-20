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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.datepicker.MaterialDatePicker
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.databinding.FragmentHistoryLogBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class HistoryLogFragment : Fragment() {

    private var _binding: FragmentHistoryLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels({ requireParentFragment() })

    private val dateFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.searchQuery.value = s?.toString() ?: ""
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.cgDateFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.dateFilter.value = when {
                checkedIds.contains(R.id.chip_filter_week) -> HistoryViewModel.DateFilter.WEEK
                checkedIds.contains(R.id.chip_filter_month) -> HistoryViewModel.DateFilter.MONTH
                checkedIds.contains(R.id.chip_filter_3months) -> HistoryViewModel.DateFilter.THREE_MONTHS
                else -> HistoryViewModel.DateFilter.ALL
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredSessions.collect { sessions ->
                    renderSessions(sessions)
                }
            }
        }
    }

    private fun renderSessions(sessions: List<WorkoutSession>) {
        binding.tvEmpty.isVisible = sessions.isEmpty()
        binding.layoutSessions.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (session in sessions) {
            val card = buildSessionCard(inflater, session)
            binding.layoutSessions.addView(card)
        }
    }

    private fun buildSessionCard(inflater: LayoutInflater, session: WorkoutSession): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 16 * resources.displayMetrics.density
            strokeWidth = (1 * resources.displayMetrics.density).toInt()
            strokeColor = requireContext().getColor(com.google.android.material.R.color.material_on_background_emphasis_medium)
            cardElevation = 0f
            val m = (12 * resources.displayMetrics.density).toInt()
            (layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(0, 0, 0, m)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = m }
        }

        val outer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }

        // Header row
        val headerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val tvDate = TextView(requireContext()).apply {
            text = dateFmt.format(Date(session.dateMs))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnEdit = MaterialButton(
            requireContext(),
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Edit date"
            textSize = 11f
            val p = (6 * resources.displayMetrics.density).toInt()
            setPadding(p, 0, p, 0)
            minimumWidth = 0
            minWidth = 0
            setOnClickListener {
                val picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Change session date")
                    .setSelection(session.dateMs)
                    .build()
                picker.addOnPositiveButtonClickListener { newDate ->
                    viewModel.updateSessionDate(session, newDate)
                }
                picker.show(parentFragmentManager, "date_picker")
            }
        }

        headerRow.addView(tvDate)
        headerRow.addView(btnEdit)
        outer.addView(headerRow)

        val tvMeta = TextView(requireContext()).apply {
            val dur = if (session.durationMinutes > 0) "${session.durationMinutes} min" else "—"
            text = dur
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            val m = (4 * resources.displayMetrics.density).toInt()
            (layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = m
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m }
            setTextColor(requireContext().getColor(com.google.android.material.R.color.material_on_background_emphasis_medium))
        }
        outer.addView(tvMeta)

        // Expandable sets container
        val setsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val t = (12 * resources.displayMetrics.density).toInt()
            (layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = t
        }

        val btnExpand = TextView(requireContext()).apply {
            text = "Show sets ▾"
            textSize = 12f
            setTextColor(requireContext().getColor(com.google.android.material.R.color.material_on_background_emphasis_medium))
            val m = (8 * resources.displayMetrics.density).toInt()
            val p = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, p, 0, p)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m }
            isClickable = true
            isFocusable = true

            var expanded = false
            setOnClickListener {
                if (!expanded) {
                    expanded = true
                    text = "Hide sets ▴"
                    setsContainer.visibility = View.VISIBLE
                    if (setsContainer.childCount == 0) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val sets = viewModel.getSetsForSession(session.id)
                            tvMeta.text = if (session.durationMinutes > 0) "${session.durationMinutes} min · ${sets.size} sets" else "${sets.size} sets"
                            if (sets.isEmpty()) {
                                val empty = TextView(requireContext()).apply {
                                    text = "No sets recorded"
                                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                                }
                                setsContainer.addView(empty)
                            } else {
                                sets.forEach { set -> setsContainer.addView(buildSetRow(set)) }
                            }
                        }
                    }
                } else {
                    expanded = false
                    text = "Show sets ▾"
                    setsContainer.visibility = View.GONE
                }
            }
        }

        outer.addView(btnExpand)
        outer.addView(setsContainer)
        card.addView(outer)
        return card
    }

    private fun buildSetRow(set: WorkoutSet): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val p = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, p, 0, p)
        }

        val (abbr, color) = muscleBadge(set.muscleGroup)
        val badge = TextView(requireContext()).apply {
            text = abbr
            textSize = 10f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            val size = (36 * resources.displayMetrics.density).toInt()
            val m = (6 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = m }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(color))
            }
        }

        val tvName = TextView(requireContext()).apply {
            text = set.exerciseName
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvInfo = TextView(requireContext()).apply {
            text = "Set ${set.setNumber}  ${set.reps}×${set.weightKg}kg"
            textSize = 12f
            setTextColor(requireContext().getColor(com.google.android.material.R.color.material_on_background_emphasis_medium))
        }

        val btnDel = TextView(requireContext()).apply {
            text = "×"
            textSize = 16f
            val p = (8 * resources.displayMetrics.density).toInt()
            setPadding(p, 0, p, 0)
            setTextColor(Color.parseColor("#FF5252"))
            isClickable = true
            isFocusable = true
            setOnClickListener { viewModel.deleteSet(set) }
        }

        row.addView(badge)
        row.addView(tvName)
        row.addView(tvInfo)
        row.addView(btnDel)
        return row
    }

    private fun muscleBadge(muscleGroup: String): Pair<String, String> = when (muscleGroup) {
        "Chest" -> "CH" to "#E91E63"
        "Back" -> "BA" to "#2196F3"
        "Legs" -> "LE" to "#4CAF50"
        "Shoulders" -> "SH" to "#9C27B0"
        "Arms" -> "AR" to "#FF5722"
        "Core" -> "CO" to "#FF9800"
        "Cardio" -> "CA" to "#00BCD4"
        else -> "??" to "#607D8B"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
