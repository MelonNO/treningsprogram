package com.migul.treningsprogram.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.migul.treningsprogram.databinding.BottomSheetRestTimerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RestTimerBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var timerManager: RestTimerManager

    private var _binding: BottomSheetRestTimerBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(seconds: Int, exerciseName: String = "") = RestTimerBottomSheet().apply {
            arguments = Bundle().also {
                it.putInt("seconds", seconds)
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

        binding.tvExerciseLabel.text = if (exerciseName.isNotBlank()) "Rest — $exerciseName" else "Rest"
        binding.tvAiSuggested.text = "AI suggested: %d:%02d".format(suggestedSecs / 60, suggestedSecs % 60)

        // Start if not already running (idempotent — does nothing if timer already ticking)
        if (!timerManager.isRunning.value) {
            timerManager.start(suggestedSecs * 1000L)
        }

        // "Skip" = cancel timer AND dismiss (explicit stop)
        binding.btnSkip.setOnClickListener {
            timerManager.stop()
            dismiss()
        }
        // Adjust buttons restart timer with adjusted duration
        binding.btnMinus30.setOnClickListener {
            val newMs = (timerManager.remainingMs.value - 30_000L).coerceAtLeast(5_000L)
            timerManager.start(newMs)
        }
        binding.btnPlus30.setOnClickListener {
            timerManager.start(timerManager.remainingMs.value + 30_000L)
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
