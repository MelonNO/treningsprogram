package com.migul.treningsprogram.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.migul.treningsprogram.data.db.entity.ExerciseFeedback
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.databinding.FragmentSettingsExerciseFeedbackBinding
import com.migul.treningsprogram.domain.ExerciseFeedbackCatalog
import com.migul.treningsprogram.ui.common.ExerciseFeedbackDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Item 05 (2026-08-06), improvement D + decision D2 — the review-and-undo home for per-exercise
 * feedback, reached from Settings → Training beside "Exercises to exclude".
 *
 * Why it exists at all: without it, feedback accumulates invisibly and something said in one bad
 * week keeps steering programs forever. Every entry is listed with **when it was given** (the same
 * signal the AI is handed, decision D3), can be edited, and can be removed outright.
 *
 * It deliberately sits next to the exclusion list rather than being folded into it (decision D1):
 * that list is a hard filter, this is a nuanced hint, but the user should still find everything
 * that steers the AI in one place.
 */
@AndroidEntryPoint
class SettingsExerciseFeedbackFragment : Fragment() {

    private var _binding: FragmentSettingsExerciseFeedbackBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var workoutRepository: WorkoutRepository

    private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsExerciseFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                workoutRepository.observeExerciseFeedback().collectLatest { render(it) }
            }
        }
    }

    private fun render(items: List<ExerciseFeedback>) {
        if (_binding == null) return
        binding.tvFeedbackEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.cardFeedback.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        binding.layoutFeedbackList.removeAllViews()
        items.forEach { binding.layoutFeedbackList.addView(row(it)) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun row(item: ExerciseFeedback): View {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        container.addView(TextView(ctx).apply {
            text = item.exerciseName
            textSize = 15f
        })
        container.addView(TextView(ctx).apply {
            text = ExerciseFeedbackCatalog.labelFor(item.reasonKey)
            textSize = 13f
            setTextColor(0xFF7FE9E1.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        if (item.note.isNotBlank()) {
            container.addView(TextView(ctx).apply {
                text = "“${item.note}”"
                textSize = 13f
                setTextColor(0xFF888888.toInt())
                setPadding(0, dp(2), 0, 0)
            })
        }
        container.addView(TextView(ctx).apply {
            // D3: the age is what makes old feedback fade — show the user the same thing the AI sees.
            text = "Given ${dateFmt.format(Date(item.updatedAtMs))}"
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setPadding(0, dp(4), 0, 0)
        })
        container.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Edit"
                textSize = 12f
                setOnClickListener {
                    ExerciseFeedbackDialog.show(
                        context = ctx,
                        exerciseName = item.exerciseName,
                        existing = item,
                        onSubmit = { reasonKey, note ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                workoutRepository.saveExerciseFeedback(item.exerciseName, reasonKey, note)
                            }
                        },
                        onRemove = {
                            viewLifecycleOwner.lifecycleScope.launch {
                                workoutRepository.deleteExerciseFeedback(item.exerciseName)
                            }
                        },
                    )
                }
            })
            addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Remove"
                textSize = 12f
                setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        workoutRepository.deleteExerciseFeedback(item.exerciseName)
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginStart = dp(8) }
            })
        })
        return container
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
