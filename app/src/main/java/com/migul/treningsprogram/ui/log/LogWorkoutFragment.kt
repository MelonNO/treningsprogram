package com.migul.treningsprogram.ui.log

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.data.repository.WgerRepository
import com.migul.treningsprogram.databinding.DialogWorkoutResultBinding
import com.migul.treningsprogram.databinding.FragmentLogWorkoutBinding
import com.migul.treningsprogram.domain.model.WorkoutResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LogWorkoutFragment : Fragment() {

    private var _binding: FragmentLogWorkoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LogWorkoutViewModel by viewModels()
    @Inject lateinit var wgerRepository: WgerRepository

    private var freestyleMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogWorkoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionId = arguments?.getLong("sessionId", -1L) ?: -1L
        val dayOfWeek = arguments?.getInt("dayOfWeek", -1) ?: -1

        if (sessionId > 0L) {
            viewModel.loadSession(sessionId, dayOfWeek)
        } else {
            viewModel.resumeSession(dayOfWeek)
        }

        // +/- weight buttons
        binding.btnWeightMinus.setOnClickListener {
            val cur = binding.etWeight.text.toString().toFloatOrNull() ?: 0f
            binding.etWeight.setText(formatWeight((cur - 2.5f).coerceAtLeast(0f)))
        }
        binding.btnWeightPlus.setOnClickListener {
            val cur = binding.etWeight.text.toString().toFloatOrNull() ?: 0f
            binding.etWeight.setText(formatWeight(cur + 2.5f))
        }

        // +/- reps buttons
        binding.btnRepsMinus.setOnClickListener {
            val cur = binding.etReps.text.toString().toIntOrNull() ?: 0
            if (cur > 0) binding.etReps.setText((cur - 1).toString())
        }
        binding.btnRepsPlus.setOnClickListener {
            val cur = binding.etReps.text.toString().toIntOrNull() ?: 0
            binding.etReps.setText((cur + 1).toString())
        }

        // Log Set
        binding.btnLogSet.setOnClickListener {
            val weight = binding.etWeight.text.toString().toFloatOrNull() ?: 0f
            val reps = binding.etReps.text.toString().toIntOrNull() ?: 0
            if (reps <= 0) return@setOnClickListener

            val isWarmup = binding.chipWarmup.isChecked
            val rpe = when {
                binding.chipRpeEasy.isChecked -> "Easy"
                binding.chipRpeModerate.isChecked -> "Moderate"
                binding.chipRpeHard.isChecked -> "Hard"
                else -> ""
            }

            if (freestyleMode) {
                val name = binding.etFreestyleExercise.text?.toString()?.trim() ?: ""
                if (name.isBlank()) return@setOnClickListener
                viewModel.logFreestyleSet(name, weight, reps, isWarmup, rpe)
            } else {
                viewModel.logSet(weight, reps, isWarmup, rpe)
            }

            // Confirm animation
            binding.btnLogSet.animate().scaleX(1.08f).scaleY(1.08f).setDuration(80)
                .withEndAction { binding.btnLogSet.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                .start()

            binding.cgRpe.clearCheck()

            val restSecs = viewModel.getRestSecondsForCurrentExercise()
            val exerciseName = if (freestyleMode) binding.etFreestyleExercise.text?.toString()?.trim() ?: ""
                               else viewModel.currentExercise.value?.exerciseName ?: ""
            showRestTimer(restSecs, exerciseName)
        }

        // Navigation
        binding.btnPrevExercise.setOnClickListener { viewModel.previousExercise() }
        binding.btnSkipExercise.setOnClickListener { viewModel.skipExercise() }
        binding.btnNextExercise.setOnClickListener {
            if (freestyleMode || viewModel.isLastExercise) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Finish workout?")
                    .setMessage("End the session now?")
                    .setPositiveButton("Complete") { _, _ -> viewModel.completeWorkout() }
                    .setNegativeButton("Keep going", null)
                    .show()
            } else {
                viewModel.nextExercise()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.elapsedTimeMs.collect { ms ->
                        val mins = ms / 60000; val secs = (ms % 60000) / 1000
                        binding.tvElapsed.text = "%02d:%02d".format(mins, secs)
                    }
                }

                launch {
                    // Wait for plan to finish loading before deciding guided vs freestyle
                    combine(viewModel.planLoaded, viewModel.guidedPlan) { loaded, plan -> loaded to plan }
                        .collect { (loaded, plan) ->
                            if (!loaded) return@collect  // still loading — keep UI neutral
                            if (plan.isEmpty()) {
                                freestyleMode = true
                                binding.tvExerciseCounter.text = "Free Session"
                                binding.progressSession.visibility = View.GONE
                                binding.btnNextExercise.text = "Complete"
                                binding.btnPrevExercise.visibility = View.GONE
                                binding.btnSkipExercise.visibility = View.GONE
                                binding.tilFreestyleExercise.visibility = View.VISIBLE
                                binding.tvExerciseName.text = "Log any exercise"
                                binding.chipTargetSets.visibility = View.GONE
                                binding.chipTargetReps.visibility = View.GONE
                                binding.chipTargetWeight.visibility = View.GONE
                            } else {
                                freestyleMode = false
                                binding.tilFreestyleExercise.visibility = View.GONE
                                binding.progressSession.visibility = View.VISIBLE
                                binding.btnPrevExercise.visibility = View.VISIBLE
                                binding.btnSkipExercise.visibility = View.VISIBLE
                            }
                        }
                }

                launch {
                    combine(viewModel.guidedPlan, viewModel.currentIndex) { plan, idx -> plan to idx }
                        .collect { (plan, idx) ->
                            if (plan.isNotEmpty()) {
                                val isLast = idx >= plan.size - 1
                                binding.tvExerciseCounter.text = "Exercise ${idx + 1} / ${plan.size}"
                                binding.progressSession.progress = idx * 100 / plan.size
                                binding.btnNextExercise.text = if (isLast) "Finish" else "Next"
                                binding.btnPrevExercise.visibility = if (idx == 0) View.INVISIBLE else View.VISIBLE
                                binding.btnSkipExercise.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
                            }
                        }
                }

                launch {
                    viewModel.currentExercise.collect { exercise ->
                        if (exercise != null && !freestyleMode) updateExerciseDisplay(exercise)
                    }
                }

                launch {
                    viewModel.setsForCurrentExercise.collect { sets ->
                        updateLoggedSets(sets)
                    }
                }

                launch {
                    combine(viewModel.setsForCurrentExercise, viewModel.currentExercise) { sets, exercise ->
                        sets to exercise
                    }.collect { (sets, exercise) ->
                        if (exercise != null && !freestyleMode) {
                            val nextSet = sets.count { !it.isWarmup } + 1
                            val target = exercise.sets
                            binding.tvSetCounter.text = if (nextSet > target) "Set $nextSet" else "Set $nextSet of $target"
                        } else {
                            binding.tvSetCounter.text = ""
                        }
                    }
                }

                launch {
                    viewModel.currentExerciseElapsedMs.collect { ms ->
                        if (!freestyleMode) {
                            val mins = ms / 60000
                            val secs = (ms % 60000) / 1000
                            binding.tvExerciseTime.text = "%d:%02d on this exercise".format(mins, secs)
                        }
                    }
                }

                launch {
                    viewModel.sessionAbandoned.collect { abandoned ->
                        if (abandoned) findNavController().popBackStack(R.id.homeFragment, false)
                    }
                }
                launch {
                    viewModel.workoutResult.collect { result ->
                        result?.let { showResultDialog(it) }
                    }
                }
            }
        }
    }

    private fun updateExerciseDisplay(exercise: PlannedExercise) {
        binding.tvExerciseName.text = exercise.exerciseName
        binding.chipTargetSets.visibility = View.VISIBLE
        binding.chipTargetReps.visibility = View.VISIBLE
        binding.chipTargetWeight.visibility = View.VISIBLE
        binding.chipTargetSets.text = "${exercise.sets} sets"
        val isCardio = exercise.targetWeightKg == 0f &&
            (exercise.targetReps.contains("min", ignoreCase = true) || exercise.targetReps.contains("km") || exercise.targetReps.contains("×"))
        binding.chipTargetReps.text = if (isCardio) exercise.targetReps else "${exercise.targetReps} reps"
        binding.chipTargetWeight.text = if (exercise.targetWeightKg > 0f) "${formatWeight(exercise.targetWeightKg)}kg" else "BW"

        val (badge, color) = getMuscleStyle(exercise.exerciseName)
        binding.tvMuscleBadge.text = badge
        try {
            binding.tvMuscleBadge.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor(color))
        } catch (_: Exception) {}

        if (exercise.targetWeightKg > 0f) {
            binding.etWeight.setText(formatWeight(exercise.targetWeightKg))
        }
        val firstRep = Regex("\\d+").find(exercise.targetReps)?.value
        if (firstRep != null) binding.etReps.setText(firstRep)

        viewLifecycleOwner.lifecycleScope.launch {
            val lastSets = viewModel.getLastSets(exercise.exerciseName)
            if (_binding == null) return@launch
            if (lastSets.isNotEmpty()) {
                val summary = lastSets.joinToString("  •  ") { "S${it.setNumber}: ${it.reps} reps @ ${formatWeight(it.weightKg)}kg" }
                binding.tvLastSession.text = "Last: $summary"
                binding.tvLastSession.visibility = View.VISIBLE
                if (exercise.targetWeightKg == 0f) {
                    binding.etWeight.setText(formatWeight(lastSets.last().weightKg))
                }
            } else {
                binding.tvLastSession.visibility = View.GONE
            }
        }

        val (_, bannerColor) = getMuscleStyle(exercise.exerciseName)
        val muscleName = getMuscleGroupName(exercise.exerciseName)
        binding.layoutMuscleBanner.setBackgroundColor(
            Color.parseColor(bannerColor).let { base ->
                Color.argb(50, Color.red(base), Color.green(base), Color.blue(base))
            }
        )
        binding.tvMuscleBannerLabel.text = muscleName.uppercase()
        binding.tvMuscleBannerLabel.setTextColor(Color.parseColor(bannerColor))
        binding.ivExerciseImage.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val url = wgerRepository.getExerciseImageUrl(exercise.exerciseName)
            if (_binding == null) return@launch
            if (url != null) {
                binding.ivExerciseImage.visibility = View.VISIBLE
                binding.ivExerciseImage.load(url) {
                    crossfade(true)
                    listener(onError = { _, _ ->
                        if (_binding != null) binding.ivExerciseImage.visibility = View.GONE
                    })
                }
                binding.tvMuscleBannerLabel.visibility = View.GONE
            }
        }
    }

    private fun updateLoggedSets(sets: List<WorkoutSet>) {
        if (sets.isEmpty()) {
            binding.cardLoggedSets.visibility = View.GONE
            return
        }
        binding.cardLoggedSets.visibility = View.VISIBLE
        val warmupCount = sets.count { it.isWarmup }
        val workingCount = sets.count { !it.isWarmup }
        binding.tvSetsHeader.text = buildString {
            if (workingCount > 0) append("$workingCount working set${if (workingCount > 1) "s" else ""}")
            if (warmupCount > 0) {
                if (workingCount > 0) append("  +  ")
                append("$warmupCount warm-up")
            }
        }
        binding.layoutLoggedSets.removeAllViews()
        sets.forEach { set ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(4) }
            }
            val label = if (set.isWarmup) "W${set.setNumber}" else "S${set.setNumber}"
            val colorHex = if (set.isWarmup) "#8888A8" else "#7C67F5"
            val tv = TextView(requireContext()).apply {
                text = "$label: ${set.reps} reps @ ${formatWeight(set.weightKg)}kg"
                textSize = 13f
                setTextColor(Color.parseColor(colorHex))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(tv)
            if (set.rpeLabel.isNotBlank()) {
                val rpeTv = TextView(requireContext()).apply {
                    text = set.rpeLabel
                    textSize = 11f
                    setTextColor(Color.parseColor("#8888A8"))
                }
                row.addView(rpeTv)
            }
            binding.layoutLoggedSets.addView(row)
        }
    }

    private fun getMuscleStyle(exerciseName: String): Pair<String, String> {
        val lower = exerciseName.lowercase()
        return when {
            lower.contains("run") || lower.contains("jog") || lower.contains("bike") ||
            lower.contains("burpee") || lower.contains("interval") || lower.contains("tempo") ||
            lower.contains("mountain") || lower.contains("high knee") || lower.contains("jump rope") -> "Cardio" to "#00BCD4"
            lower.contains("bench") || lower.contains("fly") || lower.contains("push") ||
            lower.contains("chest") -> "Chest" to "#E91E63"
            lower.contains("pull") || lower.contains("row") || lower.contains("deadlift") ||
            lower.contains("lat") -> "Back" to "#2196F3"
            lower.contains("squat") || lower.contains("leg") || lower.contains("lunge") ||
            lower.contains("calf") || lower.contains("romanian") -> "Legs" to "#4CAF50"
            lower.contains("shoulder") || lower.contains("lateral") || lower.contains("face pull") ||
            lower.contains("overhead") -> "Shoulders" to "#9C27B0"
            lower.contains("curl") || lower.contains("tricep") || lower.contains("hammer") ||
            lower.contains("skull") || lower.contains("pushdown") -> "Arms" to "#FF5722"
            lower.contains("plank") || lower.contains("crunch") || lower.contains("russian") ||
            lower.contains("ab") || lower.contains("core") -> "Core" to "#FF9800"
            else -> "Training" to "#7C67F5"
        }
    }

    private fun getMuscleGroupName(exerciseName: String): String {
        val lower = exerciseName.lowercase()
        return when {
            lower.contains("run") || lower.contains("jog") || lower.contains("bike") ||
            lower.contains("burpee") || lower.contains("interval") || lower.contains("tempo") ||
            lower.contains("mountain") || lower.contains("high knee") || lower.contains("jump rope") -> "Cardio"
            lower.contains("bench") || lower.contains("fly") || lower.contains("push") ||
            lower.contains("chest") -> "Chest"
            lower.contains("pull") || lower.contains("row") || lower.contains("deadlift") ||
            lower.contains("lat") -> "Back"
            lower.contains("squat") || lower.contains("leg") || lower.contains("lunge") ||
            lower.contains("calf") || lower.contains("romanian") -> "Legs"
            lower.contains("shoulder") || lower.contains("lateral") || lower.contains("face pull") ||
            lower.contains("overhead") -> "Shoulders"
            lower.contains("curl") || lower.contains("tricep") || lower.contains("hammer") ||
            lower.contains("skull") || lower.contains("pushdown") -> "Arms"
            lower.contains("plank") || lower.contains("crunch") || lower.contains("russian") ||
            lower.contains("ab") || lower.contains("core") -> "Core"
            else -> "Training"
        }
    }

    private fun showRestTimer(seconds: Int, exerciseName: String = "") {
        RestTimerBottomSheet.newInstance(seconds, exerciseName).show(childFragmentManager, "rest_timer")
    }

    private fun showResultDialog(result: WorkoutResult) {
        val dialogBinding = DialogWorkoutResultBinding.inflate(layoutInflater)
        dialogBinding.tvLevel.text = "L${result.level}"
        dialogBinding.tvXpEarned.text = "+${result.xpEarned} XP"
        dialogBinding.progressXp.progress = (result.levelProgress * 100).toInt()
        dialogBinding.tvXpToNext.text = "${result.xpToNextLevel} XP to Level ${result.level + 1}"
        if (result.didLevelUp) dialogBinding.tvLevelUp.visibility = View.VISIBLE
        val streakEmoji = when {
            result.currentStreak >= 7  -> "🔥🔥"
            result.currentStreak >= 3  -> "🔥"
            else                       -> "📅"
        }
        dialogBinding.tvStreak.text = "$streakEmoji ${result.currentStreak}-day streak"
        val volumeStr = if (result.totalVolumeKg > 0f) "  •  ${result.totalVolumeKg.toInt()} kg volume" else ""
        dialogBinding.tvSessionSummary.text =
            "${result.exerciseCount} exercises  •  ${result.setsLogged} sets$volumeStr"
        if (result.personalRecords.isNotEmpty()) {
            dialogBinding.cardPrs.visibility = View.VISIBLE
            dialogBinding.tvPrs.text = result.personalRecords.joinToString("\n") { "• $it" }
        }
        val bonusLines = buildList {
            result.newAchievements.forEach { add("${it.emoji} ${it.name} — ${it.description}") }
            result.completedChallenges.forEach { add("Goal: ${it.name} challenge! +${it.bonusXp} XP") }
        }
        if (bonusLines.isNotEmpty()) {
            dialogBinding.cardAchievements.visibility = View.VISIBLE
            dialogBinding.tvAchievements.text = bonusLines.joinToString("\n")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Workout Complete!")
            .setView(dialogBinding.root)
            .setPositiveButton("Awesome!") { _, _ ->
                viewModel.clearResult()
                findNavController().popBackStack(R.id.homeFragment, false)
            }
            .setCancelable(false)
            .show()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
