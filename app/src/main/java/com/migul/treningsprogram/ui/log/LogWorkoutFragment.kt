package com.migul.treningsprogram.ui.log

import android.graphics.Color
import android.net.Uri
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import android.app.Dialog
import android.widget.ImageView
import android.widget.FrameLayout
import coil.load
import coil.size.Scale
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.CalisthenicsProgressionMap
import com.migul.treningsprogram.data.ExerciseCatalog
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
    @Inject lateinit var restTimerManager: RestTimerManager

    private var freestyleMode = false
    private var swapButton: MaterialButton? = null

    private val imageHandler = Handler(Looper.getMainLooper())
    private var imageAlternateRunnable: Runnable? = null
    private var imageFrame = 0
    private var currentImageDbId: String? = null

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

        // Dismiss keyboard on Done for all text inputs in the set-entry area
        val doneAction: (android.widget.TextView) -> Boolean = { v ->
            v.clearFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
            true
        }
        binding.etFreestyleExercise.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) doneAction(v) else false
        }
        binding.etWeight.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) doneAction(v) else false
        }
        binding.etReps.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) doneAction(v) else false
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

        // Persistent recall bar — tap or swipe-up to start or re-open rest timer (Bug 02)
        val timerBarDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                openTimerRecall(); return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && (e1.y - e2.y) > 80 && velocityY < -300) {
                    openTimerRecall(); return true
                }
                return false
            }
        })
        binding.viewTimerRecallBar.setOnTouchListener { _, event -> timerBarDetector.onTouchEvent(event) }

        // Navigation
        binding.btnPrevExercise.setOnClickListener {
            saveCurrentValues()
            viewModel.previousExercise()
        }
        binding.btnSkipExercise.setOnClickListener {
            saveCurrentValues()
            viewModel.skipExercise()
        }
        binding.btnNextExercise.setOnClickListener {
            if (freestyleMode || viewModel.isLastExercise) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Finish workout?")
                    .setMessage("End the session now?")
                    .setPositiveButton("Complete") { _, _ -> viewModel.completeWorkout() }
                    .setNegativeButton("Keep going", null)
                    .show()
            } else {
                saveCurrentValues()
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

                // Update persistent recall bar (Bug 02)
                launch {
                    combine(restTimerManager.isRunning, restTimerManager.remainingMs) { running, ms -> running to ms }
                        .collect { (running, ms) ->
                            if (running) {
                                val secs = (ms / 1000).toInt()
                                binding.tvTimerBarLabel.text = "Resting — tap to view"
                                binding.tvTimerBarCountdown.text = "%d:%02d".format(secs / 60, secs % 60)
                                binding.tvTimerBarCountdown.visibility = View.VISIBLE
                            } else {
                                binding.tvTimerBarLabel.text = "Rest Timer"
                                binding.tvTimerBarCountdown.visibility = View.GONE
                            }
                        }
                }
            }
        }
    }

    private fun updateExerciseDisplay(exercise: PlannedExercise) {
        binding.tvExerciseName.text = exercise.exerciseName

        // Issue 11 — tap name to see instructions
        binding.tvExerciseName.setOnClickListener {
            if (isAdded) {
                ExerciseInfoBottomSheet.newInstance(exercise.exerciseName, exercise.exerciseDbId)
                    .show(childFragmentManager, "exercise_info")
            }
        }

        // Issue 12 — swap button for calisthenics exercises
        // Remove previous swap button if it exists
        swapButton?.let { btn ->
            (btn.parent as? ViewGroup)?.removeView(btn)
        }
        swapButton = null

        if (CalisthenicsProgressionMap.looksLikeCalisthenics(exercise.exerciseName)) {
            val btn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Swap"
                textSize = 12f
                val hPad = dpToPx(12)
                val vPad = dpToPx(4)
                setPadding(hPad, vPad, hPad, vPad)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.topMargin = dpToPx(4)
                    it.bottomMargin = dpToPx(4)
                }
                setOnClickListener { showSwapDialog(exercise) }
            }
            // Insert after tvExerciseName's parent row — find the parent LinearLayout of tvExerciseName
            val nameParent = binding.tvExerciseName.parent as? LinearLayout
            val cardContent = nameParent?.parent as? LinearLayout
            val nameRowIndex = cardContent?.indexOfChild(nameParent) ?: -1
            if (cardContent != null && nameRowIndex >= 0) {
                cardContent.addView(btn, nameRowIndex + 1)
            }
            swapButton = btn
        }

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

        // Set weight: saved value > AI suggestion (last actual checked async below)
        val savedWeight = viewModel.getSavedWeight(exercise.exerciseName)
        if (savedWeight != null) {
            binding.etWeight.setText(formatWeight(savedWeight))
        } else if (exercise.targetWeightKg > 0f) {
            binding.etWeight.setText(formatWeight(exercise.targetWeightKg))
        }

        // Set reps: saved value > AI suggestion
        val savedReps = viewModel.getSavedReps(exercise.exerciseName)
        if (savedReps != null) {
            binding.etReps.setText(savedReps.toString())
        } else {
            val firstRep = Regex("\\d+").find(exercise.targetReps)?.value
            if (firstRep != null) binding.etReps.setText(firstRep)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val lastSets = viewModel.getLastSets(exercise.exerciseName)
            if (_binding == null) return@launch
            if (lastSets.isNotEmpty()) {
                val summary = lastSets.joinToString("  •  ") { "S${it.setNumber}: ${it.reps} reps @ ${formatWeight(it.weightKg)}kg" }
                binding.tvLastSession.text = "Last: $summary"
                binding.tvLastSession.visibility = View.VISIBLE
                // Prefill from last session only if user hasn't entered their own value yet
                if (viewModel.getSavedWeight(exercise.exerciseName) == null) {
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

        val dbId = exercise.exerciseDbId
        if (dbId != null) {
            binding.ivExerciseImage.visibility = View.VISIBLE
            binding.tvMuscleBannerLabel.visibility = View.GONE
            binding.tvImageExpandHint.visibility = View.VISIBLE
            startImageAlternation(dbId)
            binding.layoutMuscleBanner.setOnClickListener { showFullScreenImage(dbId) }
        } else {
            stopImageAlternation()
            binding.tvImageExpandHint.visibility = View.GONE
            binding.layoutMuscleBanner.setOnClickListener(null)
            binding.layoutMuscleBanner.isClickable = false
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
    }

    private fun startImageAlternation(dbId: String) {
        stopImageAlternation()
        currentImageDbId = dbId
        imageFrame = 0
        imageAlternateRunnable = object : Runnable {
            override fun run() {
                if (_binding == null) return
                binding.ivExerciseImage.load(Uri.parse(ExerciseCatalog.getImageSource(dbId, imageFrame))) {
                    crossfade(200)
                    scale(Scale.FILL)
                    listener(onError = { _, _ -> /* keep showing last good frame */ })
                }
                imageFrame = 1 - imageFrame
                imageHandler.postDelayed(this, 1000L)
            }
        }
        imageHandler.post(imageAlternateRunnable!!)
    }

    private fun stopImageAlternation() {
        imageAlternateRunnable?.let { imageHandler.removeCallbacks(it) }
        imageAlternateRunnable = null
        currentImageDbId = null
    }

    private fun showFullScreenImage(dbId: String) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val imageView = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val container = FrameLayout(requireContext()).apply {
            setBackgroundColor(Color.BLACK)
            addView(imageView)
        }

        dialog.setContentView(container)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        var fsFrame = 0
        val fsHandler = Handler(Looper.getMainLooper())
        val fsRunnable = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                imageView.load(Uri.parse(ExerciseCatalog.getImageSource(dbId, fsFrame))) {
                    crossfade(200)
                    scale(Scale.FIT)
                }
                fsFrame = 1 - fsFrame
                fsHandler.postDelayed(this, 1000L)
            }
        }

        container.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { fsHandler.removeCallbacks(fsRunnable) }
        dialog.show()
        fsHandler.post(fsRunnable)
    }

    private fun saveCurrentValues() {
        val exerciseName = viewModel.currentExercise.value?.exerciseName ?: return
        val weight = binding.etWeight.text.toString().toFloatOrNull() ?: return
        val reps = binding.etReps.text.toString().toIntOrNull() ?: return
        viewModel.saveCurrentExerciseValues(exerciseName, weight, reps)
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
        if (!restTimerManager.isRunning.value) {
            restTimerManager.start(seconds * 1000L)
        }
        val existing = childFragmentManager.findFragmentByTag("rest_timer")
        if (existing == null || !existing.isAdded) {
            RestTimerBottomSheet.newInstance(seconds, exerciseName).show(childFragmentManager, "rest_timer")
        }
    }

    private fun showResultDialog(result: WorkoutResult) {
        if (!isAdded || _binding == null) return
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
                if (isAdded) {
                    findNavController().popBackStack(R.id.homeFragment, false)
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun showSwapDialog(exercise: PlannedExercise) {
        val easier = CalisthenicsProgressionMap.getEasierOptions(exercise.exerciseName)
        val harder = CalisthenicsProgressionMap.getHarderOptions(exercise.exerciseName)

        if (easier.isEmpty() && harder.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Swap Exercise")
                .setMessage("No progression alternatives found for ${exercise.exerciseName}.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val options = mutableListOf<String>()
        if (harder.isNotEmpty()) options.addAll(harder.map { "↑ $it" })
        if (easier.isNotEmpty()) options.addAll(easier.map { "↓ $it" })

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Swap: ${exercise.exerciseName}")
            .setItems(options.toTypedArray()) { _, which ->
                val chosen = options[which]
                    .removePrefix("↑ ")
                    .removePrefix("↓ ")
                viewModel.swapCurrentExercise(exercise, chosen)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openTimerRecall() {
        val restSecs = viewModel.getRestSecondsForCurrentExercise()
        val exerciseName = if (freestyleMode) binding.etFreestyleExercise.text?.toString()?.trim() ?: ""
                           else viewModel.currentExercise.value?.exerciseName ?: ""
        showRestTimer(restSecs, exerciseName)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        stopImageAlternation()
        _binding = null
    }
}
