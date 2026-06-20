package com.migul.treningsprogram.ui.log

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.migul.treningsprogram.databinding.BottomSheetRestTimerBinding

class RestTimerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRestTimerBinding? = null
    private val binding get() = _binding!!

    private var totalSeconds = 90
    private var millisRemaining = 0L
    private var timer: CountDownTimer? = null

    companion object {
        fun newInstance(seconds: Int, exerciseName: String = "") = RestTimerBottomSheet().apply {
            arguments = Bundle().also {
                it.putInt("seconds", seconds)
                it.putString("exerciseName", exerciseName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        totalSeconds = arguments?.getInt("seconds", 90) ?: 90
        millisRemaining = totalSeconds * 1000L
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

        updateDisplay()
        startTimer()
        binding.btnSkip.setOnClickListener { cancelAndDismiss() }
        binding.btnMinus30.setOnClickListener {
            millisRemaining = (millisRemaining - 30_000L).coerceAtLeast(5_000L)
            totalSeconds = (totalSeconds - 30).coerceAtLeast(5)
            startTimer()
        }
        binding.btnPlus30.setOnClickListener {
            millisRemaining += 30_000L
            totalSeconds += 30
            startTimer()
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(millisRemaining, 100L) {
            override fun onTick(ms: Long) {
                millisRemaining = ms
                updateDisplay()
            }
            override fun onFinish() {
                _binding?.tvTimerDisplay?.text = "GO!"
                _binding?.progressTimer?.progress = 0
                vibrate()
                view?.postDelayed({ if (isAdded) dismiss() }, 1500L)
            }
        }.start()
    }

    private fun cancelAndDismiss() {
        timer?.cancel()
        dismiss()
    }

    private fun updateDisplay() {
        val secs = (millisRemaining / 1000).toInt()
        _binding?.tvTimerDisplay?.text = "%d:%02d".format(secs / 60, secs % 60)
        val progress = ((millisRemaining.toFloat() / (totalSeconds * 1000f)) * 100).toInt()
        _binding?.progressTimer?.progress = progress.coerceIn(0, 100)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
                .vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    override fun onDestroyView() {
        timer?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
