package com.migul.treningsprogram.ui.log

import android.graphics.Color
import android.net.Uri
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
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
import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.ExerciseCatalog
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.data.repository.WgerRepository
import com.migul.treningsprogram.databinding.DialogWorkoutResultBinding
import com.migul.treningsprogram.databinding.FragmentLogWorkoutBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.migul.treningsprogram.domain.model.WorkoutResult
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.ui.shared.SharedWorkoutResultViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.fragment.app.activityViewModels
@AndroidEntryPoint
class LogWorkoutFragment : Fragment() {

    private var _binding: FragmentLogWorkoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LogWorkoutViewModel by viewModels()
    private val sharedResultVm: SharedWorkoutResultViewModel by activityViewModels()
    private val recapTarget: com.migul.treningsprogram.ui.history.RecapTargetViewModel by activityViewModels()
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
        // P2: when set, perform THIS source day's planned workout as today's session.
        val moveFromDay = arguments?.getInt("moveFromDay", 0) ?: 0

        if (sessionId > 0L) {
            viewModel.loadSession(sessionId, dayOfWeek, moveFromDay)
        } else {
            viewModel.resumeSession(dayOfWeek, moveFromDay)
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

        binding.btnTimerRecall.setOnClickListener { openTimerRecall() }
        binding.btnPauseWorkout.setOnClickListener { showPauseDialog() }

        // Item 6 / B01 — tap the "Exercise X / Y" progress region to open the quick-access menu.
        // The listener is on the whole header container (layout_session_progress) so the tap
        // target is the entire counter row + bar area, not just the thin bar or narrow label.
        // The pause button is a child that consumes its own taps, so it is unaffected.
        binding.layoutSessionProgress.setOnClickListener {
            if (!freestyleMode) showQuickAccessMenu()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.currentIndex.value > 0) {
                        saveCurrentValues()
                        viewModel.previousExercise()
                    } else {
                        showPauseDialog()
                    }
                }
            }
        )

        // Navigation
        binding.btnPrevExercise.setOnClickListener {
            saveCurrentValues()
            viewModel.previousExercise()
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
                                // No jump menu in freestyle: drop the ripple/click on the header.
                                binding.layoutSessionProgress.isClickable = false
                                binding.btnNextExercise.text = "Complete"
                                binding.btnPrevExercise.visibility = View.GONE
                                binding.tilFreestyleExercise.visibility = View.VISIBLE
                                binding.tvExerciseName.text = "Log any exercise"
                                binding.chipTargetSets.visibility = View.GONE
                                binding.chipTargetReps.visibility = View.GONE
                                binding.chipTargetWeight.visibility = View.GONE
                            } else {
                                freestyleMode = false
                                binding.tilFreestyleExercise.visibility = View.GONE
                                binding.progressSession.visibility = View.VISIBLE
                                binding.layoutSessionProgress.isClickable = true
                                binding.btnPrevExercise.visibility = View.VISIBLE
                            }
                        }
                }

                launch {
                    combine(viewModel.guidedPlan, viewModel.currentIndex) { plan, idx -> plan to idx }
                        .collect { (plan, idx) ->
                            if (plan.isNotEmpty()) {
                                val isLast = idx >= plan.size - 1
                                binding.tvExerciseCounter.text = "Exercise ${idx + 1} / ${plan.size}"
                                // Completion-based: reaching the last exercise fills the bar
                                // to 100% (was idx/size, which capped at (size-1)/size).
                                binding.progressSession.progress = (idx + 1) * 100 / plan.size
                                binding.btnNextExercise.text = if (isLast) "Finish" else "Next"
                                binding.btnPrevExercise.visibility = if (idx == 0) View.INVISIBLE else View.VISIBLE
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

                // Update timer button in bottom bar
                launch {
                    combine(restTimerManager.isRunning, restTimerManager.remainingMs) { running, ms -> running to ms }
                        .collect { (running, ms) ->
                            if (running) {
                                val secs = (ms / 1000).toInt()
                                binding.btnTimerRecall.text = "⏱ %d:%02d".format(secs / 60, secs % 60)
                            } else {
                                binding.btnTimerRecall.text = "⏱"
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
                ExerciseInfoBottomSheet.newInstance(
                    exercise.exerciseName, exercise.exerciseDbId, exercise.notes
                ).show(childFragmentManager, "exercise_info")
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

        // Added / custom exercises (Item 6) carry no AI target: sets == 0 and no target reps.
        // Show a single "Log freely" chip instead of empty "0 sets /  reps" prescriptions.
        val hasAiTarget = exercise.sets > 0 || exercise.targetReps.isNotBlank()
        if (hasAiTarget) {
            binding.chipTargetSets.visibility = View.VISIBLE
            binding.chipTargetReps.visibility = View.VISIBLE
            binding.chipTargetWeight.visibility = View.VISIBLE
            binding.chipTargetSets.text = "${exercise.sets} sets"
            val isCardio = exercise.targetWeightKg == 0f &&
                (exercise.targetReps.contains("min", ignoreCase = true) || exercise.targetReps.contains("km") || exercise.targetReps.contains("×"))
            binding.chipTargetReps.text = if (isCardio) exercise.targetReps else "${exercise.targetReps} reps"
            binding.chipTargetWeight.text = if (exercise.targetWeightKg > 0f) "${formatWeight(exercise.targetWeightKg)}kg" else "BW"
        } else {
            binding.chipTargetSets.visibility = View.VISIBLE
            binding.chipTargetReps.visibility = View.GONE
            binding.chipTargetWeight.visibility = View.GONE
            binding.chipTargetSets.text = "Log freely"
        }

        val (badge, color) = getMuscleStyle(exercise.exerciseName)
        binding.tvMuscleBadge.text = badge
        try {
            binding.tvMuscleBadge.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor(color))
        } catch (_: Exception) {}

        // Set weight (B02): saved draft > own last-logged (async, below) > AI suggestion > BW.
        // Resolve synchronously WITHOUT the previous exercise's value as an input so a fresh
        // bodyweight exercise clears the field (→ "BW" hint) instead of inheriting the weight
        // that was sitting in the field from the previously viewed exercise. The own last-logged
        // weight isn't known yet on this pass; the async block below re-resolves with it.
        val savedWeight = viewModel.getSavedWeight(exercise.exerciseName)
        val syncWeight = LogWorkoutViewModel.resolveWeightDefault(
            savedDraftWeight = savedWeight,
            ownLastLoggedWeight = null,
            aiTargetWeightKg = exercise.targetWeightKg
        )
        binding.etWeight.setText(syncWeight?.let { formatWeight(it) } ?: "")

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
                // B02: re-resolve now that this exercise's OWN last-logged weight is known.
                // saved draft still wins (draft-restore preserved); otherwise the exercise's
                // own last weight prefills — including legitimate added weight on bodyweight
                // work. This is its OWN history, never the previous exercise's value.
                val resolved = LogWorkoutViewModel.resolveWeightDefault(
                    savedDraftWeight = viewModel.getSavedWeight(exercise.exerciseName),
                    ownLastLoggedWeight = lastSets.last().weightKg,
                    aiTargetWeightKg = exercise.targetWeightKg
                )
                binding.etWeight.setText(resolved?.let { formatWeight(it) } ?: "")
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
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        val margin = dpToPx(32)
        val imageView = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).also { it.setMargins(margin, margin * 2, margin, margin * 2) }
        }
        val container = FrameLayout(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
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

    // Badge label + colour for an exercise, via the shared MuscleClassifier so the Log
    // banner agrees with the Program badges and the muscle group stored on each set.
    // Keeps this screen's accent-purple fallback for unclassifiable names.
    private fun getMuscleStyle(exerciseName: String): Pair<String, String> {
        val group = getMuscleGroupName(exerciseName)
        return group to MuscleClassifier.colorFor(group, fallbackColor = "#7C67F5")
    }

    private fun getMuscleGroupName(exerciseName: String): String =
        MuscleClassifier.displayName(exerciseName)

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
        val streakEmoji = when {
            result.currentStreak >= 7 -> "🔥🔥"
            result.currentStreak >= 3 -> "🔥"
            else                      -> "📅"
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
            result.completedChallenges.forEach { add("🎯 ${it.name} challenge!") }
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
                if (isAdded) startCompletionFlow(result)
            }
            .setNeutralButton("View full analysis") { _, _ ->
                viewModel.clearResult()
                if (isAdded) startAnalysisFlow()
            }
            .setCancelable(false)
            .show()
    }

    /** Leaves the log screen and opens the latest session's Recap under the Stats tab. */
    private fun startAnalysisFlow() {
        if (!isAdded || _binding == null) return
        viewModel.clearResult()
        findNavController().popBackStack()
        recapTarget.request(null)  // latest session
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
            ?.selectedItemId = R.id.historyFragment
    }

    private fun startCompletionFlow(result: WorkoutResult) {
        if (!isAdded || _binding == null) return
        viewModel.clearResult()
        val day = viewModel.workoutDayOfWeek.let { if (it > 0) it else currentDayOfWeek() }
        sharedResultVm.setResult(result, day)
        // P2: a completed move commits the week change; flag the Program tab to rebalance the week.
        if (viewModel.consumeMoveCommitted()) sharedResultVm.setMoveRebalancePending()
        val prevDestId = findNavController().previousBackStackEntry?.destination?.id
        // Pop logWorkoutFragment so it doesn't persist in any tab's back stack
        findNavController().popBackStack()
        // If we came from Home, we need to explicitly switch to Program tab.
        // If we came from Program, popBackStack() already returned to programFragment
        // and ProgramFragment.onResume() will pick up the pending result.
        if (prevDestId != R.id.programFragment) {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
                ?.selectedItemId = R.id.programFragment
        }
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

    // ---- Item 6: quick-access exercise menu ----------------------------------------

    private fun showQuickAccessMenu() {
        val plan = viewModel.guidedPlan.value
        if (plan.isEmpty()) return
        val currentIdx = viewModel.currentIndex.value
        val finished = viewModel.loggedExerciseNames()
        val rows = plan.mapIndexed { idx, ex ->
            val status = when {
                idx == currentIdx -> QuickAccessBottomSheet.Status.CURRENT
                ex.exerciseName in finished -> QuickAccessBottomSheet.Status.FINISHED
                else -> QuickAccessBottomSheet.Status.UPCOMING
            }
            QuickAccessBottomSheet.Row(ex.exerciseName, status, idx)
        }
        val sheet = QuickAccessBottomSheet().apply {
            this.rows = rows
            onJump = { targetIdx ->
                // Save typed values first, then jump. Jumping to a finished exercise behaves
                // like Back to it (its logged sets show and can be edited) — nothing is lost.
                saveCurrentValues()
                viewModel.jumpToExercise(targetIdx)
            }
            onAddExercise = { showAddExerciseDialog() }
        }
        sheet.show(childFragmentManager, "quick_access")
    }

    private fun showAddExerciseDialog() {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            hint = "Search exercises"
            setSingleLine()
        }
        val til = com.google.android.material.textfield.TextInputLayout(ctx).apply {
            addView(input)
        }
        val resultsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = androidx.core.widget.NestedScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (260 * density).toInt()
            )
            addView(resultsContainer)
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(til)
            addView(scroll)
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Add exercise")
            .setView(root)
            .setNegativeButton("Cancel", null)
            .create()

        fun renderResults(query: String) {
            resultsContainer.removeAllViews()
            val q = query.trim()
            viewLifecycleOwner.lifecycleScope.launch {
                val matches = if (q.isBlank()) emptyList() else viewModel.searchLocalExercises(q)
                if (!isAdded) return@launch
                resultsContainer.removeAllViews()
                matches.forEach { ex ->
                    resultsContainer.addView(TextView(ctx).apply {
                        text = ex.name
                        textSize = 16f
                        val v = (12 * density).toInt()
                        setPadding((4 * density).toInt(), v, (4 * density).toInt(), v)
                        isClickable = true
                        setBackgroundResource(selectableItemBg())
                        setOnClickListener {
                            viewModel.addExerciseAfterCurrent(ex.name, ex.exerciseDbId, ex.muscleGroup)
                            dialog.dismiss()
                        }
                    })
                }
                // "Add anyway" — create a custom, loggable exercise (no DB info, no AI target)
                if (q.isNotBlank()) {
                    resultsContainer.addView(TextView(ctx).apply {
                        text = "Add anyway: \"$q\"  (custom exercise)"
                        textSize = 15f
                        setTextColor(Color.parseColor("#7C67F5"))
                        val v = (12 * density).toInt()
                        setPadding((4 * density).toInt(), v, (4 * density).toInt(), v)
                        isClickable = true
                        setBackgroundResource(selectableItemBg())
                        setOnClickListener {
                            viewModel.addExerciseAfterCurrent(q, null, "")
                            dialog.dismiss()
                        }
                    })
                }
            }
        }

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { renderResults(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        dialog.show()
    }

    private fun selectableItemBg(): Int {
        val outValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(
            android.R.attr.selectableItemBackground, outValue, true
        )
        return outValue.resourceId
    }

    private fun showPauseDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pause Workout")
            .setPositiveButton("Resume", null)
            .setNegativeButton("Abandon") { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Abandon Workout?")
                    .setMessage("This session will be discarded.")
                    .setPositiveButton("Yes, abandon") { _, _ -> viewModel.abandonSession() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
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

    override fun onPause() {
        super.onPause()
        // Capture whatever the user has typed so a process kill mid-workout doesn't
        // revert it to AI suggestions on resume. Persists via the ViewModel's draft store.
        if (_binding != null && !freestyleMode) saveCurrentValues()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopImageAlternation()
        _binding = null
    }
}
