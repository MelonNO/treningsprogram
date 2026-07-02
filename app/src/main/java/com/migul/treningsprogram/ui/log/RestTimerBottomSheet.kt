package com.migul.treningsprogram.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.migul.treningsprogram.databinding.BottomSheetRestTimerBinding
import com.migul.treningsprogram.domain.ManualRestTimes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RestTimerBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var timerManager: RestTimerManager

    // Item 2: the sheet is always shown from LogWorkoutFragment's childFragmentManager, so the
    // parent fragment's ViewModel is the live session VM — +30/−30 presses are recorded there so
    // the NEXT set of this exercise starts from the adjusted time (session-only, never persisted).
    private val logViewModel: LogWorkoutViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private var _binding: BottomSheetRestTimerBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(rest: RestStart, exerciseName: String = "") = RestTimerBottomSheet().apply {
            arguments = Bundle().also {
                it.putInt("seconds", rest.seconds)
                it.putInt("baseSeconds", rest.baseSeconds)
                it.putBoolean("manualSource", rest.isManualSource)
                it.putString("exerciseName", exerciseName)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetRestTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val exerciseName = arguments?.getString("exerciseName", "") ?: ""
        val suggestedSecs = arguments?.getInt("seconds", 90) ?: 90
        val baseSecs = arguments?.getInt("baseSeconds", suggestedSecs) ?: suggestedSecs
        val manualSource = arguments?.getBoolean("manualSource", false) ?: false

        binding.tvExerciseLabel.text = if (exerciseName.isNotBlank()) "Rest — $exerciseName" else "Rest"
        // Item 4: honest source label — the user's own category time vs the AI's suggestion.
        // Item 2: the BASE is shown; an active session adjustment is appended so the label stays honest.
        val sourcePrefix = if (manualSource) "Your time" else "AI suggested"
        val baseLabel = "$sourcePrefix: ${ManualRestTimes.formatMinSec(baseSecs)}"
        binding.tvAiSuggested.text =
            if (suggestedSecs != baseSecs) "$baseLabel · adjusted to ${ManualRestTimes.formatMinSec(suggestedSecs)}"
            else baseLabel

        // Start if not already running (idempotent — does nothing if timer already ticking)
        if (!timerManager.isRunning.value) {
            timerManager.start(suggestedSecs * 1000L)
        }

        // "Skip" = cancel timer AND dismiss (explicit stop)
        binding.btnSkip.setOnClickListener {
            timerManager.stop()
            dismiss()
        }
        // Adjust buttons restart timer with adjusted duration. Item 2: each press is also recorded
        // on the session VM so every REMAINING set of this exercise starts from base ± the net
        // adjustment (resets on the next exercise and next session — never persisted).
        binding.btnMinus30.setOnClickListener {
            val newMs = (timerManager.remainingMs.value - 30_000L).coerceAtLeast(5_000L)
            timerManager.start(newMs)
            logViewModel.recordRestAdjustment(exerciseName, -30)
        }
        binding.btnPlus30.setOnClickListener {
            timerManager.start(timerManager.remainingMs.value + 30_000L)
            logViewModel.recordRestAdjustment(exerciseName, +30)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                timerManager.remainingMs.collect { ms ->
                    if (_binding == null) return@collect
                    val total = timerManager.totalMs.value.coerceAtLeast(1L)
                    if (ms <= 0L && !timerManager.isRunning.value) {
                        binding.tvTimerDisplay.text = "GO!"
                        binding.progressTimer.progress = 0
                        view.postDelayed({
                            if (isAdded && _binding != null) dismiss()
                        }, 1500L)
                    } else if (ms > 0L) {
                        val secs = (ms / 1000).toInt()
                        binding.tvTimerDisplay.text = "%d:%02d".format(secs / 60, secs % 60)
                        val progress = ((ms.toFloat() / total) * 100).toInt().coerceIn(0, 100)
                        binding.progressTimer.progress = progress
                    }
                }
            }
        }
    }

    // Swipe-down (native BottomSheet dismiss) intentionally does NOT call timerManager.stop()
    // so the timer keeps running in the background service (Issue 05 fix).
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
