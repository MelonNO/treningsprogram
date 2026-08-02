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

    // B1: session-scoped state of the warm-up ramp offer — dismissed exercises and the ladder
    // currently on offer (what one tap on "Log warm-up sets" will log).
    private val rampDismissed = mutableSetOf<String>()
    private var currentRampSteps: List<WarmupRamp.Step> = emptyList()

    // Item 9: state of the calculator-style weight keypad (source of truth while the pad is open).
    private var calcState = WeightCalculator.State()

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

        // +/- 2.5 kg quick-step buttons (separate from the Item 9 calculator pad — both stay).
        binding.btnWeightMinus.setOnClickListener {
            val cur = binding.etWeight.text.toString().toFloatOrNull() ?: 0f
            binding.etWeight.setText(formatWeight((cur - 2.5f).coerceAtLeast(0f)))
            reseedWeightKeypadIfOpen()
        }
        binding.btnWeightPlus.setOnClickListener {
            val cur = binding.etWeight.text.toString().toFloatOrNull() ?: 0f
            binding.etWeight.setText(formatWeight(cur + 2.5f))
            reseedWeightKeypadIfOpen()
        }

        // Item 9: calculator-style keypad for the weight field.
        setupWeightKeypad()

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

            // Confirm animation + haptic (F1)
            binding.btnLogSet.performHapticFeedback(
                if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
                else android.view.HapticFeedbackConstants.VIRTUAL_KEY
            )
            binding.btnLogSet.animate().scaleX(1.08f).scaleY(1.08f).setDuration(80)
                .withEndAction { binding.btnLogSet.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                .start()

            binding.cgRpe.clearCheck()
            // QoL item 07: the warm-up chip is per-set, not sticky — it clears after every
            // logged set so a forgotten toggle can't silently record working sets as warm-ups.
            // (The B1 ramp button logs its warm-ups independently of this chip.)
            binding.chipWarmup.isChecked = false

            val exerciseName = if (freestyleMode) binding.etFreestyleExercise.text?.toString()?.trim() ?: ""
                               else viewModel.currentExercise.value?.exerciseName ?: ""
            val rest = viewModel.getRestStart(if (freestyleMode) exerciseName else null)
            showRestTimer(rest, exerciseName)
        }

        binding.btnTimerRecall.setOnClickListener { openTimerRecall() }
        binding.btnPauseWorkout.setOnClickListener { showPauseDialog() }

        // B1: warm-up ramp — accept logs the ladder as warm-up sets; ✕ dismisses for this
        // exercise this session. The card recomputes as the working weight is adjusted (A-W3)
        // and disappears once any set is logged for the exercise.
        binding.btnLogWarmups.setOnClickListener {
            if (currentRampSteps.isNotEmpty()) viewModel.logWarmupRamp(currentRampSteps)
            binding.cardWarmupRamp.visibility = View.GONE
        }
        binding.btnWarmupDismiss.setOnClickListener {
            viewModel.currentExercise.value?.exerciseName?.let { rampDismissed.add(it) }
            binding.cardWarmupRamp.visibility = View.GONE
        }
        binding.etWeight.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { refreshWarmupRamp() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // N7: tap the quiet note line to edit in place; long-press the exercise name to add
        // one when none exists yet (the library detail screen is the discoverable add surface).
        binding.tvSetupNote.setOnClickListener { showSetupNoteDialog() }
        binding.tvExerciseName.setOnLongClickListener {
            if (!freestyleMode && viewModel.currentExercise.value != null) {
                showSetupNoteDialog(); true
            } else false
        }

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
                    .setPositiveButton("Complete") { _, _ ->
                        // F1: a firmer confirm for the session-level milestone
                        _binding?.root?.performHapticFeedback(
                            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
                            else android.view.HapticFeedbackConstants.LONG_PRESS
                        )
                        viewModel.completeWorkout()
                    }
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
                        // B1: the offer collapses the moment any set exists for the exercise.
                        refreshWarmupRamp()
                    }
                }

                // N7: the quiet setup-note line — shown only when the exercise has a note.
                launch {
                    viewModel.setupNote.collect { note ->
                        if (note.isNullOrBlank()) {
                            binding.tvSetupNote.visibility = View.GONE
                        } else {
                            binding.tvSetupNote.visibility = View.VISIBLE
                            binding.tvSetupNote.text = "📌 $note"
                        }
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

                // R7: "beat last time" chip + inline PR flare.
                launch {
                    viewModel.beatTarget.collect { target ->
                        if (!prFlashActive) renderBeatChip(target)
                    }
                }
                launch {
                    viewModel.prFlash.collect { newBest -> playPrFlash(newBest) }
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
                                // C2: the ⏱ glyph is now the button's vector icon; the text is
                                // just the live countdown.
                                val secs = (ms / 1000).toInt()
                                binding.btnTimerRecall.text = "%d:%02d".format(secs / 60, secs % 60)
                            } else {
                                binding.btnTimerRecall.text = ""
                            }
                        }
                }
            }
        }
    }

    // ── Item 9: calculator-style weight keypad ────────────────────────────────────────────────────

    private fun setupWeightKeypad() {
        // Tapping the weight field opens the custom +/− pad instead of the system numeric keyboard;
        // the field still holds a plain resolved number, so typing a value and being done still works.
        binding.etWeight.showSoftInputOnFocus = false
        binding.etWeight.setOnClickListener { showWeightKeypad() }
        binding.etWeight.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showWeightKeypad() }
        // Focusing reps / freestyle name is a different input → close the weight pad, let the IME show.
        binding.etReps.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) hideWeightKeypad() }
        binding.etFreestyleExercise.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) hideWeightKeypad() }

        val digits = mapOf(
            binding.btnKp0 to '0', binding.btnKp1 to '1', binding.btnKp2 to '2',
            binding.btnKp3 to '3', binding.btnKp4 to '4', binding.btnKp5 to '5',
            binding.btnKp6 to '6', binding.btnKp7 to '7', binding.btnKp8 to '8', binding.btnKp9 to '9'
        )
        digits.forEach { (btn, d) -> btn.setOnClickListener { applyCalc(WeightCalculator.digit(calcState, d)) } }
        binding.btnKpDot.setOnClickListener { applyCalc(WeightCalculator.dot(calcState)) }
        binding.btnKpPlus.setOnClickListener { applyCalc(WeightCalculator.operator(calcState, WeightCalculator.Op.ADD)) }
        binding.btnKpMinus.setOnClickListener { applyCalc(WeightCalculator.operator(calcState, WeightCalculator.Op.SUB)) }
        binding.btnKpBack.setOnClickListener { applyCalc(WeightCalculator.backspace(calcState)) }
        binding.btnKpClear.setOnClickListener { applyCalc(WeightCalculator.clear()) }
        binding.btnKpDone.setOnClickListener { hideWeightKeypad() }

        // Stage-3 item 16: while the pad is open, a tap anywhere OUTSIDE it closes it (resolving
        // any pending expression, same as Done) and the tap still performs its normal action
        // (pass-through, A-16a). Exempt the weight-editing surface itself: the pad, the weight
        // field, and the ±2.5 quick-steps (which keep reseeding the open pad as before).
        binding.root.onDispatchDown = { ev ->
            if (_binding != null && binding.layoutWeightKeypad.visibility == View.VISIBLE &&
                !isTouchInside(ev, binding.layoutWeightKeypad) &&
                !isTouchInside(ev, binding.etWeight) &&
                !isTouchInside(ev, binding.btnWeightMinus) &&
                !isTouchInside(ev, binding.btnWeightPlus)
            ) hideWeightKeypad()
        }
    }

    /** True when the raw touch point of [ev] lands within [v]'s on-screen bounds. */
    private fun isTouchInside(ev: android.view.MotionEvent, v: View): Boolean {
        if (v.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return ev.rawX >= loc[0] && ev.rawX <= loc[0] + v.width &&
            ev.rawY >= loc[1] && ev.rawY <= loc[1] + v.height
    }

    private fun applyCalc(newState: WeightCalculator.State) {
        if (_binding == null) return
        calcState = newState
        val text = WeightCalculator.fieldText(calcState)
        binding.etWeight.setText(text)
        binding.etWeight.setSelection(text.length)
        binding.tvKpExpr.text = WeightCalculator.expr(calcState)
        updatePlateHint()
    }

    /** F4: live "plates per side" readout for plate-loaded lifts while the pad is open. */
    private fun updatePlateHint() {
        if (_binding == null) return
        val name = if (freestyleMode) binding.etFreestyleExercise.text?.toString().orEmpty()
                   else viewModel.currentExercise.value?.exerciseName.orEmpty()
        // Item 01 (2026-08): the resolved DB entry's equipment lets dumbbell-by-nature lifts
        // without "dumbbell"/"DB" in the plan name (Zottman Curl, …) get the dumbbell readout.
        val dbEquipment = if (freestyleMode) null
            else viewModel.currentExercise.value?.exerciseDbId
                ?.let { com.migul.treningsprogram.data.ExerciseCatalog.getDbEntry(it)?.equipment }
        val hint = PlateMath.display(
            WeightCalculator.value(calcState), name, viewModel.plateProfile.value, dbEquipment
        )
        binding.tvKpPlates.visibility = if (hint == null) View.GONE else View.VISIBLE
        binding.tvKpPlates.text = hint.orEmpty()
    }

    private fun showWeightKeypad() {
        if (_binding == null) return
        // Suppress the system keyboard and seed the pad from the value currently in the field.
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etWeight.windowToken, 0)
        calcState = WeightCalculator.fromField(binding.etWeight.text?.toString())
        binding.tvKpExpr.text = WeightCalculator.expr(calcState)
        updatePlateHint()
        binding.layoutWeightKeypad.visibility = View.VISIBLE
    }

    private fun hideWeightKeypad() {
        if (_binding == null) return
        if (binding.layoutWeightKeypad.visibility == View.VISIBLE) {
            // Resolve any pending "60 + 5" into the field before closing.
            binding.etWeight.setText(WeightCalculator.fieldText(calcState))
        }
        binding.layoutWeightKeypad.visibility = View.GONE
    }

    /** Keep the pad's state consistent when the separate ±2.5 buttons change the field underneath it. */
    private fun reseedWeightKeypadIfOpen() {
        if (_binding == null) return
        if (binding.layoutWeightKeypad.visibility == View.VISIBLE) {
            calcState = WeightCalculator.fromField(binding.etWeight.text?.toString())
            binding.tvKpExpr.text = WeightCalculator.expr(calcState)
        }
    }

    private fun updateExerciseDisplay(exercise: PlannedExercise) {
        // Switching exercises re-populates the weight field, so any open calculator pad is dismissed.
        _binding?.layoutWeightKeypad?.visibility = View.GONE
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
                // Item 8: same data (one prior session, working sets only), cleaner formatting.
                binding.tvLastSession.text = LastSessionFormat.line(lastSets)
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
            binding.ivImageExpandHint.visibility = View.VISIBLE
            startImageAlternation(dbId)
            binding.layoutMuscleBanner.setOnClickListener { showFullScreenImage(dbId) }
        } else {
            stopImageAlternation()
            binding.ivImageExpandHint.visibility = View.GONE
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

    // ── B1: warm-up ramp offer ────────────────────────────────────────────────────────────────

    /**
     * Recomputes the offer from the CURRENT state: guided exercise + heavy compound + a real
     * working weight in the field + no sets logged yet + not dismissed. Pure math in
     * [WarmupRamp]; this only renders/hides.
     */
    private fun refreshWarmupRamp() {
        if (_binding == null) return
        val exercise = viewModel.currentExercise.value
        if (freestyleMode || exercise == null ||
            exercise.exerciseName in rampDismissed ||
            viewModel.setsForCurrentExercise.value.isNotEmpty()
        ) {
            binding.cardWarmupRamp.visibility = View.GONE
            return
        }
        val workingWeight = binding.etWeight.text.toString().toFloatOrNull() ?: 0f
        val steps = WarmupRamp.stepsFor(
            exercise.exerciseName, workingWeight, viewModel.plateProfile.value
        )
        currentRampSteps = steps
        if (steps.isEmpty()) {
            binding.cardWarmupRamp.visibility = View.GONE
        } else {
            binding.tvWarmupSteps.text = steps.joinToString("\n") {
                "•  ${formatWeight(it.weightKg)} kg × ${it.reps}"
            }
            binding.cardWarmupRamp.visibility = View.VISIBLE
        }
    }

    // ── N7: setup-note edit dialog (in place, no navigation away) ────────────────────────────

    private fun showSetupNoteDialog() {
        val exercise = viewModel.currentExercise.value ?: return
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            setText(viewModel.setupNote.value ?: "")
            hint = "e.g. pin height 7, seat 4, belt on top sets"
            setSelection(text?.length ?: 0)
        }
        val container = FrameLayout(ctx).apply {
            val p = (20 * density).toInt()
            setPadding(p, (8 * density).toInt(), p, 0)
            addView(input)
        }
        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle("Setup note — ${exercise.exerciseName}")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                viewModel.saveSetupNote(input.text?.toString() ?: "")
            }
            .setNegativeButton("Cancel", null)
        if (!viewModel.setupNote.value.isNullOrBlank()) {
            builder.setNeutralButton("Clear") { _, _ -> viewModel.saveSetupNote("") }
        }
        builder.show()
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
            val colorHex = if (set.isWarmup) "#7E908E" else "#7FE9E1"
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
                    setTextColor(Color.parseColor("#7E908E"))
                }
                row.addView(rpeTv)
            }
            // QoL item 01: a misslogged set can be removed on the spot — quiet ✕ per row,
            // guarded by an "are you sure" dialog. The sets flow re-emits after deletion, so
            // numbering, counters, ramp and beat targets refresh on their own; the running
            // rest timer is untouched.
            val deleteTv = TextView(requireContext()).apply {
                text = "✕"
                textSize = 14f
                setTextColor(Color.parseColor("#7E908E"))
                setPadding(dpToPx(12), dpToPx(2), dpToPx(4), dpToPx(2))
                contentDescription = "Delete set $label"
                setOnClickListener { confirmDeleteSet(set, "$label: ${set.reps} reps @ ${formatWeight(set.weightKg)}kg") }
            }
            row.addView(deleteTv)
            binding.layoutLoggedSets.addView(row)
        }
    }

    /** QoL item 01: "are you sure" guard before a mid-workout set deletion. */
    private fun confirmDeleteSet(set: WorkoutSet, description: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete set?")
            .setMessage("$description will be removed.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteSet(set) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Badge label + colour for an exercise, via the shared MuscleClassifier so the Log
    // banner agrees with the Program badges and the muscle group stored on each set.
    // Keeps this screen's accent-purple fallback for unclassifiable names.
    private fun getMuscleStyle(exerciseName: String): Pair<String, String> {
        val group = getMuscleGroupName(exerciseName)
        return group to MuscleClassifier.colorFor(group, fallbackColor = "#7FE9E1")
    }

    private fun getMuscleGroupName(exerciseName: String): String =
        MuscleClassifier.displayName(exerciseName)

    // ── R7: "beat last time" chip + inline PR moment ─────────────────────────────────────────

    /** True while the gold "PR!" flare owns the chip, so a beatTarget re-emission can't overwrite it. */
    private var prFlashActive = false

    private fun renderBeatChip(target: Float?) {
        if (_binding == null) return
        if (target == null) {
            binding.chipBeatTarget.visibility = View.GONE
        } else {
            binding.chipBeatTarget.visibility = View.VISIBLE
            binding.chipBeatTarget.text = "Beat: ${formatWeight(target)} kg"
            binding.chipBeatTarget.setTextColor(requireContext().getColor(R.color.auros_cyan))
        }
    }

    /**
     * The inline PR moment (A-P2: small and local — the big celebration stays at completion).
     * The chip flares gold "PR! 62.5 kg" with a quick pulse, then settles back into the raised
     * "Beat:" target, which the ViewModel has already lifted to the new best.
     */
    private fun playPrFlash(newBestKg: Float) {
        if (_binding == null) return
        prFlashActive = true
        val chip = binding.chipBeatTarget
        chip.visibility = View.VISIBLE
        chip.text = "PR! ${formatWeight(newBestKg)} kg"
        chip.setTextColor(requireContext().getColor(R.color.game_gold))
        chip.performHapticFeedback(
            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY
        )
        chip.animate().scaleX(1.25f).scaleY(1.25f).setDuration(140).withEndAction {
            _binding?.chipBeatTarget?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(140)?.start()
        }.start()
        chip.postDelayed({
            prFlashActive = false
            if (_binding != null && isAdded) renderBeatChip(viewModel.beatTarget.value)
        }, 1600L)
    }

    private fun showRestTimer(rest: RestStart, exerciseName: String = "") {
        if (!restTimerManager.isRunning.value) {
            restTimerManager.start(rest.seconds * 1000L)
        }
        val existing = childFragmentManager.findFragmentByTag("rest_timer")
        if (existing == null || !existing.isAdded) {
            RestTimerBottomSheet.newInstance(rest, exerciseName).show(childFragmentManager, "rest_timer")
        }
    }

    /**
     * R6 — the celebration surface: XP count-up over an Auros burst, itemized breakdown (same
     * numbers as the XP log), PRs with new-vs-previous weights, tier-styled achievement unlock
     * cards (R5 tier model), challenge + Perfect Week rows. Both exits unchanged (Program/Home
     * animation chain and Recap). Scales down gracefully: a no-frills session is just the
     * count-up + summary. Process-death safe exactly as before — the result lives in the shared
     * VM until a button clears it.
     */
    private fun showResultDialog(result: WorkoutResult) {
        if (!isAdded || _binding == null) return
        val d = DialogWorkoutResultBinding.inflate(layoutInflater)

        val streakEmoji = when {
            result.currentStreak >= 7 -> "🔥🔥"
            result.currentStreak >= 3 -> "🔥"
            else                      -> "📅"
        }
        d.tvStreak.text = "$streakEmoji ${result.currentStreak}-day streak"
        val volumeStr = if (result.totalVolumeKg > 0f) "  •  ${result.totalVolumeKg.toInt()} kg volume" else ""
        // QoL item 03: estimated calories on the completion summary (null = no figure).
        val kcalStr = result.estimatedKcal?.let {
            "  •  ${com.migul.treningsprogram.domain.CalorieEstimator.format(it)}"
        } ?: ""
        d.tvSessionSummary.text =
            "${result.exerciseCount} exercises  •  ${result.setsLogged} sets$volumeStr$kcalStr"

        // Itemized breakdown — the SAME component amounts the XP log records (observation only).
        val setXp = result.setsLogged * 5
        val prXp = result.personalRecords.size * 30
        d.tvXpBreakdown.text = buildList {
            add("Workout +50")
            if (setXp > 0) add("${result.setsLogged} sets +$setXp")
            if (prXp > 0) add("${result.personalRecords.size} PR +$prXp")
            if (result.bonusChallengeXp > 0) add("Challenges +${result.bonusChallengeXp}")
            if (result.perfectWeekXp > 0) add("Perfect Week +${result.perfectWeekXp}")
        }.joinToString("  ·  ")

        // PRs with their numbers: "Bench Press — 62.5 kg, up from 60 kg" (prDetails carries the
        // detection-time old→new weights; names-only fallback covers any legacy result).
        if (result.personalRecords.isNotEmpty()) {
            d.cardPrs.visibility = View.VISIBLE
            val rows = result.prDetails.ifEmpty { null }
            d.layoutPrRows.removeAllViews()
            if (rows != null) {
                rows.forEach { pr ->
                    d.layoutPrRows.addView(TextView(requireContext()).apply {
                        text = "${pr.exerciseName} — ${formatWeight(pr.newWeightKg)} kg, up from ${formatWeight(pr.previousWeightKg)} kg"
                        setTextColor(requireContext().getColor(R.color.auros_snow))
                        textSize = 14f
                        setPadding(0, 4, 0, 4)
                    })
                }
            } else {
                result.personalRecords.forEach { name ->
                    d.layoutPrRows.addView(TextView(requireContext()).apply {
                        text = "• $name"
                        setTextColor(requireContext().getColor(R.color.auros_snow))
                        textSize = 14f
                    })
                }
            }
        }

        // N5: goal-reach rows — the long-arc celebration moment (reuses the PR card language;
        // no XP rides on goals, A-G1).
        if (result.reachedGoals.isNotEmpty()) {
            d.cardGoals.visibility = View.VISIBLE
            d.layoutGoalRows.removeAllViews()
            result.reachedGoals.forEach { goal ->
                val kind = if (goal.isE1rm) "est. 1RM" else "weight"
                d.layoutGoalRows.addView(TextView(requireContext()).apply {
                    text = "${goal.exerciseName} — ${formatWeight(goal.targetWeightKg)} kg $kind goal reached!"
                    setTextColor(requireContext().getColor(R.color.auros_snow))
                    textSize = 14f
                    setPadding(0, 4, 0, 4)
                })
            }
        }

        // Achievement unlocks as tier-styled cards (reuses the R5 gallery item + tier palette).
        if (result.newAchievements.isNotEmpty()) {
            d.cardAchievements.visibility = View.VISIBLE
            d.layoutAchievementCards.removeAllViews()
            result.newAchievements.forEach { a ->
                val item = layoutInflater.inflate(R.layout.item_achievement, d.layoutAchievementCards, false)
                item.findViewById<TextView>(R.id.tv_achievement_emoji).text = a.emoji
                item.findViewById<TextView>(R.id.tv_achievement_name).text = a.name
                item.findViewById<TextView>(R.id.tv_achievement_desc).text = a.description
                val meta = com.migul.treningsprogram.domain.AchievementCatalog.metaFor(a.id)
                val tierChip = item.findViewById<TextView>(R.id.tv_achievement_tier)
                if (meta != null) {
                    tierChip.visibility = View.VISIBLE
                    tierChip.text = meta.tier.label.uppercase()
                    tierChip.setTextColor(tierColorFor(meta.tier))
                    // A Legendary unlock must feel visibly bigger than a Common one.
                    item.findViewById<TextView>(R.id.tv_achievement_name)
                        .setTextColor(tierColorFor(meta.tier))
                }
                d.layoutAchievementCards.addView(item)
            }
        }

        // Challenges + Perfect Week rows.
        if (result.completedChallenges.isNotEmpty() || result.perfectWeekXp > 0) {
            d.cardChallenges.visibility = View.VISIBLE
            d.layoutChallengeRows.removeAllViews()
            result.completedChallenges.forEach { ch ->
                d.layoutChallengeRows.addView(TextView(requireContext()).apply {
                    text = "✅ ${ch.name}  +${ch.bonusXp} XP"
                    setTextColor(requireContext().getColor(R.color.auros_snow))
                    textSize = 14f
                    setPadding(0, 4, 0, 4)
                })
            }
            if (result.perfectWeekXp > 0) {
                d.tvPerfectWeek.visibility = View.VISIBLE
                d.tvPerfectWeek.text = "🏆 PERFECT WEEK — every planned day done!  +${result.perfectWeekXp} XP"
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Workout Complete!")
            .setView(d.root)
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

        // Play the moment: count the XP up (same total the bar animates later) and fire the
        // burst — scaled up for level-ups, PRs and Perfect Weeks; brief and never input-blocking.
        val intensity = when {
            // N5: a reached goal is the long-arc payoff — full celebration intensity.
            result.perfectWeekXp > 0 || result.didLevelUp || result.reachedGoals.isNotEmpty() -> 1.5f
            result.personalRecords.isNotEmpty()           -> 1.25f
            else                                          -> 1f
        }
        d.burst.play(intensity)
        android.animation.ValueAnimator.ofInt(0, result.xpEarned).apply {
            duration = 800
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { d.tvXpCountup.text = "+${it.animatedValue} XP" }
            start()
        }
    }

    private fun tierColorFor(tier: com.migul.treningsprogram.domain.AchievementCatalog.Tier): Int =
        requireContext().getColor(
            when (tier) {
                com.migul.treningsprogram.domain.AchievementCatalog.Tier.COMMON -> R.color.tier_common
                com.migul.treningsprogram.domain.AchievementCatalog.Tier.RARE -> R.color.tier_rare
                com.migul.treningsprogram.domain.AchievementCatalog.Tier.EPIC -> R.color.tier_epic
                com.migul.treningsprogram.domain.AchievementCatalog.Tier.LEGENDARY -> R.color.tier_legendary
            }
        )

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
        // B10: the widget shows streak/challenges now — refresh it the moment they change.
        com.migul.treningsprogram.ui.widget.TodayWorkoutWidgetProvider.requestRefresh(requireContext())
        // QoL item 10: a committed move is attributed to TODAY (the source day was vacated), so
        // the celebration must bounce today's chip — not the stored workout day, which in the
        // direct "start another day's workout" path is the now-cleared source day.
        val moveCommitted = viewModel.consumeMoveCommitted()
        val day = LogWorkoutViewModel.celebrationDay(moveCommitted, viewModel.workoutDayOfWeek, currentDayOfWeek())
        sharedResultVm.setResult(result, day)
        // P2: a completed move commits the week change; flag the Program tab to rebalance the week.
        if (moveCommitted) sharedResultVm.setMoveRebalancePending()
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
                        setTextColor(Color.parseColor("#7FE9E1"))
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
        val exerciseName = if (freestyleMode) binding.etFreestyleExercise.text?.toString()?.trim() ?: ""
                           else viewModel.currentExercise.value?.exerciseName ?: ""
        val rest = viewModel.getRestStart(if (freestyleMode) exerciseName else null)
        showRestTimer(rest, exerciseName)
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
