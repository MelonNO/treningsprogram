package com.migul.treningsprogram.ui.program

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.migul.treningsprogram.data.db.entity.Program
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.WgerRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.databinding.FragmentProgramBinding
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import com.migul.treningsprogram.ui.log.ExerciseInfoBottomSheet
import com.migul.treningsprogram.ui.shared.SharedWorkoutResultViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProgramFragment : Fragment() {

    private var _binding: FragmentProgramBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProgramViewModel by viewModels()
    private val sharedResultVm: SharedWorkoutResultViewModel by activityViewModels()

    @Inject lateinit var wgerRepository: WgerRepository

    private val dayAbbreviations = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val dayChipViews = mutableListOf<View>()
    private var progressAnimating = false

    // Item 11: the "Why your program changed" card always starts collapsed on each visit (no
    // persistence). Reset in onViewCreated so a re-created view never restores a prior open state.
    private var rationaleExpanded = false

    // E2: current program list + the spinner-suppression guard so programmatic selection updates
    // don't fire the "user switched program" callback.
    private var programList: List<Program> = emptyList()
    private var suppressProgramSpinner = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProgramBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutWeekDays.removeAllViews()
        dayChipViews.clear()
        for (i in 1..7) {
            val chip = layoutInflater.inflate(R.layout.item_day_chip, binding.layoutWeekDays, false)
            chip.findViewById<TextView>(R.id.tv_day_abbr).text = dayAbbreviations[i - 1]
            chip.setOnClickListener { viewModel.selectDay(i) }
            binding.layoutWeekDays.addView(chip)
            dayChipViews.add(chip)
        }

        binding.btnGoToSettings.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
                ?.selectedItemId = R.id.profileFragment
        }

        // E2: program switcher wiring.
        binding.spinnerProgram.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressProgramSpinner) return
                val selected = programList.getOrNull(position) ?: return
                if (!selected.isActive) viewModel.switchProgram(selected.id)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.btnSaveProgram.setOnClickListener { showSaveProgramDialog() }
        binding.btnProgramOptions.setOnClickListener { showProgramOptionsDialog() }

        // Item 11: force the rationale card collapsed on every open; tapping the header toggles it.
        rationaleExpanded = false
        binding.tvRationale.visibility = View.GONE
        binding.tvRationaleChevron.text = "▸"   // ▸
        binding.layoutRationaleHeader.setOnClickListener { toggleRationale() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // E2: keep the program switcher + deload chip in sync with the active program.
                    viewModel.programs.collect { programs ->
                        programList = programs
                        renderProgramSwitcher(programs, viewModel.activeProgram.value)
                    }
                }
                launch {
                    viewModel.activeProgram.collect { active ->
                        renderProgramSwitcher(programList, active)
                    }
                }
                launch {
                    viewModel.deloadActive.collect { deload ->
                        binding.tvProgramDeloadChip.visibility = if (deload) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.weekPlan.collect { plan ->
                        val hasPlan = plan.isNotEmpty()
                        // Item 8: never show the "No Program Yet" empty state while a full generation runs.
                        binding.cardEmptyState.visibility =
                            if (hasPlan || viewModel.fullGenerating.value) View.GONE else View.VISIBLE
                        binding.cardWeek.visibility = if (hasPlan) View.VISIBLE else View.GONE
                        binding.layoutDaySection.visibility = if (hasPlan) View.VISIBLE else View.GONE
                        if (hasPlan) updateDayChips(plan, viewModel.selectedDay.value)
                    }
                }
                // Item 8: full-program generation launched from Settings — show a generating animation
                // here (additive; the Settings screen keeps its own status). No auto-switch to this tab.
                launch {
                    viewModel.fullGenerating.collect { generating ->
                        binding.cardFullGenerating.visibility = if (generating) View.VISIBLE else View.GONE
                        binding.cardEmptyState.visibility = when {
                            generating -> View.GONE
                            viewModel.weekPlan.value.isEmpty() -> View.VISIBLE
                            else -> View.GONE
                        }
                    }
                }
                launch {
                    viewModel.fullGenerationStatus.collect { status ->
                        binding.tvFullGeneratingStatus.text = status
                    }
                }
                launch {
                    viewModel.fullGenerating.collectLatest { generating ->
                        if (!generating) return@collectLatest
                        var i = 0
                        while (true) {
                            binding.tvFullGeneratingTip.text =
                                com.migul.treningsprogram.ui.common.GenerationTips.tip(i++)
                            kotlinx.coroutines.delay(4500)
                        }
                    }
                }
                launch {
                    viewModel.selectedDay.collect { day ->
                        updateDayChips(viewModel.weekPlan.value, day)
                        updateDaySection(day, viewModel.selectedDayExercises.value)
                    }
                }
                launch {
                    viewModel.selectedDayExercises.collect { exercises ->
                        updateDaySection(viewModel.selectedDay.value, exercises)
                    }
                }
                launch {
                    // B2: show the "why your program changed" card only when a non-blank rationale
                    // exists. Item 11: the card body stays collapsed (set in onViewCreated / on toggle);
                    // this observer only sets the text + card visibility, never forces the body open.
                    viewModel.weekRationale.collect { rationale ->
                        if (rationale.isNotBlank()) {
                            binding.tvRationale.text = rationale
                            binding.cardRationale.visibility = View.VISIBLE
                        } else {
                            binding.cardRationale.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.weekProgress.collect { (logged, total) ->
                        binding.tvWeekProgress.text = if (total > 0) "$logged / $total done" else ""
                        if (!progressAnimating) {
                            binding.progressWeek.progress = if (total > 0) (logged * 100 / total) else 0
                        }
                    }
                }
                launch {
                    viewModel.isDayGenerating.collect { generating ->
                        binding.layoutRegenProgress.visibility = if (generating) View.VISIBLE else View.GONE
                        binding.btnRegenerateDay.isEnabled = !generating
                        binding.btnStartDayWorkout.isEnabled = !generating
                    }
                }
                launch {
                    viewModel.dayGenerationStatus.collect { status ->
                        binding.tvRegenStatus.text = status
                    }
                }
                // P5: rotate friendly/informative wait copy under the real status line while a day
                // regen / rebalance runs. The real status (tvRegenStatus) always stays visible above it.
                launch {
                    viewModel.isDayGenerating.collectLatest { generating ->
                        if (!generating) return@collectLatest
                        var i = 0
                        while (true) {
                            binding.tvRegenTip.text =
                                com.migul.treningsprogram.ui.common.GenerationTips.tip(i++)
                            kotlinx.coroutines.delay(4500)
                        }
                    }
                }
                launch {
                    viewModel.dayGenerationError.collect { error ->
                        if (error != null) {
                            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                            viewModel.clearDayGenerationError()
                        }
                    }
                }
                // Keep presets populated so the swap-day dialog always has the current list
                launch { viewModel.presets.collect { } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // P2: a completed "do another day today" move routes back here — rebalance the week (always,
        // regardless of the P1 toggle) and stay on the Program tab to show it, instead of the normal
        // completion animation that hops to Home.
        val pendingMove = sharedResultVm.consumeMoveRebalancePending()
        val result = sharedResultVm.consumeForProgram()
        if (pendingMove) {
            viewModel.selectDay(currentDayOfWeek())
            viewModel.rebalanceAfterDayMove()
            return
        }
        if (result == null) return
        binding.root.post { playCompletionAnimation() }
    }

    private fun playCompletionAnimation() {
        val workoutDay = sharedResultVm.workoutDayOfWeek.let { if (it > 0) it else currentDayOfWeek() }
        viewModel.selectDay(workoutDay)

        // Compute progress before vs after this workout (unit = workout days, not exercises)
        val (logged, total) = viewModel.weekProgress.value
        val afterPct  = if (total > 0) (logged * 100 / total) else 0
        val beforePct = if (total > 0) ((logged - 1).coerceAtLeast(0) * 100 / total) else 0

        // Reset bar to pre-workout position and take control
        progressAnimating = true
        binding.progressWeek.progress = beforePct

        val chip = dayChipViews.getOrNull(workoutDay - 1) ?: return
        val abbr = chip.findViewById<TextView>(R.id.tv_day_abbr)

        // Step 1 — bounce the chip, mid-bounce swap background to green
        chip.animate()
            .scaleX(1.5f).scaleY(1.5f)
            .setDuration(220)
            .withEndAction {
                // At peak: swap to green "done" background
                abbr.background = requireContext().getDrawable(R.drawable.bg_day_done)
                abbr.setTextColor(Color.WHITE)
                chip.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(420)
                    .setInterpolator(OvershootInterpolator(4f))
                    .start()
            }.start()

        // Step 2 — fill the week progress bar after the chip settles
        binding.progressWeek.postDelayed({
            if (_binding == null) return@postDelayed
            ObjectAnimator.ofInt(binding.progressWeek, "progress", beforePct, afterPct).apply {
                duration = 1400
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        progressAnimating = false
                        // Step 3 — navigate to Home after the bar finishes filling
                        binding.root.postDelayed({
                            if (!isAdded || _binding == null) return@postDelayed
                            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
                                ?.selectedItemId = R.id.homeFragment
                        }, 600)
                    }
                })
                start()
            }
        }, 550)
    }

    // Item 11: expand/collapse the rationale card body (chevron ▸ collapsed / ▾ expanded).
    private fun toggleRationale() {
        rationaleExpanded = !rationaleExpanded
        binding.tvRationale.visibility = if (rationaleExpanded) View.VISIBLE else View.GONE
        binding.tvRationaleChevron.text = if (rationaleExpanded) "▾" else "▸"
    }

    private fun getDayWorkoutType(exercises: List<PlannedExercise>): String? {
        if (exercises.isEmpty()) return null
        val hasCardio = exercises.any { getMuscleGroup(it.exerciseName) == "Cardio" }
        val hasStrength = exercises.any { getMuscleGroup(it.exerciseName) != "Cardio" }
        return when {
            hasCardio && hasStrength -> "MIX"
            hasCardio -> "RUN"
            else -> "STR"
        }
    }

    private fun updateDayChips(plan: List<PlannedExercise>, selectedDay: Int) {
        val exercisesByDay = plan.groupBy { it.dayOfWeek }
        for (i in 1..7) {
            val chip = dayChipViews.getOrNull(i - 1) ?: continue
            val abbr = chip.findViewById<TextView>(R.id.tv_day_abbr)
            val progress = chip.findViewById<TextView>(R.id.tv_day_progress)
            val dayType = chip.findViewById<TextView>(R.id.tv_day_type)
            val dayExercises = exercisesByDay[i] ?: emptyList()
            val logged = dayExercises.count { it.isLogged }
            val total = dayExercises.size
            val workoutType = getDayWorkoutType(dayExercises)

            val today = currentDayOfWeek()
            val isDone = logged >= 1 && total > 0
            when {
                i == selectedDay && isDone -> {
                    // Selected AND fully done — green
                    abbr.background = requireContext().getDrawable(R.drawable.bg_day_done)
                    abbr.setTextColor(Color.WHITE)
                    abbr.textSize = 11.5f
                }
                i == selectedDay && i == today -> {
                    abbr.background = requireContext().getDrawable(R.drawable.bg_day_selected)
                    abbr.setTextColor(Color.WHITE)
                    abbr.textSize = 11.5f
                }
                i == selectedDay -> {
                    abbr.background = requireContext().getDrawable(R.drawable.bg_day_selected)
                    abbr.setTextColor(Color.WHITE)
                    abbr.textSize = 11f
                }
                isDone -> {
                    // Fully done, not selected — green circle
                    abbr.background = requireContext().getDrawable(R.drawable.bg_day_done)
                    abbr.setTextColor(Color.WHITE)
                    abbr.textSize = 11f
                }
                i == today -> {
                    abbr.background = requireContext().getDrawable(R.drawable.bg_day_has_workout)
                    abbr.setTextColor(Color.WHITE)
                    abbr.textSize = 11.5f
                }
                workoutType == "RUN" -> {
                    abbr.background = requireContext().getDrawable(R.drawable.bg_day_cardio)
                    abbr.setTextColor(Color.parseColor("#00BCD4"))
                    abbr.textSize = 11f
                }
                workoutType == "MIX" -> {
                    abbr.background = requireContext().getDrawable(R.drawable.bg_day_mixed)
                    abbr.setTextColor(Color.parseColor("#FFB347"))
                    abbr.textSize = 11f
                }
                total > 0 -> {
                    abbr.background = requireContext().getDrawable(R.drawable.bg_day_has_workout)
                    abbr.setTextColor(Color.parseColor("#7C67F5"))
                    abbr.textSize = 11f
                }
                else -> {
                    abbr.background = null
                    abbr.setTextColor(Color.parseColor("#8888A8"))
                    abbr.textSize = 11f
                }
            }

            val daysAhead = i - today
            val isTentative = daysAhead > 1
            if (workoutType != null) {
                val typeColor = when (workoutType) {
                    "RUN" -> "#00BCD4"
                    "MIX" -> "#FFB347"
                    else  -> "#7C67F5"
                }
                dayType.text = if (isTentative) "~$workoutType" else workoutType
                dayType.setTextColor(Color.parseColor(typeColor))
                dayType.alpha = if (isTentative) 0.65f else 1f
                dayType.visibility = View.VISIBLE
            } else {
                dayType.text = ""
                dayType.visibility = View.GONE
            }

            progress.text = when {
                total == 0 -> ""
                logged == total -> "done"
                else -> "$logged/$total"
            }
            progress.setTextColor(
                if (logged >= 1 && total > 0) Color.parseColor("#4CAF50")
                else Color.parseColor("#8888A8")
            )
        }
    }

    private fun updateDaySection(day: Int, exercises: List<PlannedExercise>) {
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        binding.tvSelectedDayName.text = dayNames.getOrElse(day - 1) { "Day $day" }

        val isRestDay = exercises.isEmpty()
        binding.cardRestDay.visibility = if (isRestDay) View.VISIBLE else View.GONE
        binding.layoutExercises.visibility = if (isRestDay) View.GONE else View.VISIBLE
        binding.btnStartDayWorkout.visibility = if (isRestDay) View.GONE else View.VISIBLE

        if (!isRestDay) {
            val logged = exercises.count { it.isLogged }
            binding.tvDayCompletion.text = "$logged / ${exercises.size}"
            val workoutType = getDayWorkoutType(exercises)
            val typeLabel = when (workoutType) {
                "RUN" -> "Cardio"
                "MIX" -> "Mixed"
                else -> {
                    val dominant = dominantMuscleGroup(exercises)
                    val label = if (dominant == "AI decides") "Strength" else dominant
                    "$label focus"
                }
            }
            binding.tvWorkoutType.text = "${exercises.size} exercises  •  $typeLabel"

            val totalMins = WorkoutTimeEstimator.estimateDayMinutes(exercises)
            binding.tvTotalTime.text = "~${totalMins}m"

            // Item 10: a single "Start Workout" button. On another day it performs that day's plan
            // attributed to TODAY (the move + week rebalance commit on completion — see
            // LogWorkoutViewModel.completeWorkout / commitDayMove); on today's own day it behaves as
            // before. The separate "Do this workout today" button is gone.
            binding.btnStartDayWorkout.setOnClickListener {
                val bundle = Bundle().apply {
                    putLong("sessionId", -1L)
                    putInt("dayOfWeek", day)
                }
                if (findNavController().currentDestination?.id == R.id.programFragment)
                    findNavController().navigate(R.id.action_program_to_log, bundle)
            }
        } else {
            binding.tvDayCompletion.text = ""
            binding.tvWorkoutType.text = "Take it easy today"
            binding.tvTotalTime.text = ""
        }

        binding.btnRegenerateDay.visibility = View.VISIBLE
        binding.btnRegenerateDay.setOnClickListener { showRegenerateDayDialog(day) }

        // E1: "Add exercise" is available on any non-rest day (and we still allow adding to a
        // currently-empty/rest day via the same control once it is shown — it stays hidden only
        // when the whole week has no plan, since the day section itself is hidden then).
        binding.btnAddExercise.visibility = if (isRestDay) View.GONE else View.VISIBLE
        binding.btnAddExercise.setOnClickListener { showAddExerciseDialog(day) }

        binding.layoutExercises.removeAllViews()
        exercises.forEachIndexed { index, ex ->
            binding.layoutExercises.addView(
                inflateDayOverviewCard(ex, index, exercises.size)
            )
        }
    }

    private fun inflateDayOverviewCard(exercise: PlannedExercise, index: Int, count: Int): View {
        val card = layoutInflater.inflate(R.layout.item_day_overview, binding.layoutExercises, false)

        val muscleBadge = card.findViewById<TextView>(R.id.tv_muscle_badge)
        val exerciseName = card.findViewById<TextView>(R.id.tv_exercise_name)
        val chipSets = card.findViewById<TextView>(R.id.chip_sets)
        val chipReps = card.findViewById<TextView>(R.id.chip_reps)
        val chipWeight = card.findViewById<TextView>(R.id.chip_weight)
        val tvNotes = card.findViewById<TextView>(R.id.tv_notes)
        val tvExerciseTime = card.findViewById<TextView>(R.id.tv_exercise_time)

        val group = getMuscleGroup(exercise.exerciseName)
        val (badgeText, badgeColor) = getMuscleStyle(group)
        muscleBadge.text = badgeText
        try {
            muscleBadge.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor(badgeColor))
        } catch (_: Exception) {}

        exerciseName.text = exercise.exerciseName

        val isCardio = getMuscleGroup(exercise.exerciseName) == "Cardio"

        chipSets.text = if (isCardio) "cardio" else "${exercise.sets} sets"
        chipReps.text = if (isCardio) exercise.targetReps else "${exercise.targetReps} reps"
        chipWeight.text = if (exercise.targetWeightKg > 0) "${formatWeight(exercise.targetWeightKg)} kg" else "Bodyweight"
        if (isCardio) chipWeight.visibility = View.GONE

        if (isCardio) {
            tvExerciseTime.text = exercise.targetReps
        } else {
            val mins = (WorkoutTimeEstimator.estimateExerciseSeconds(exercise) + 30) / 60
            tvExerciseTime.text = "~${mins}m"
        }

        if (exercise.notes.isNotBlank()) {
            tvNotes.text = exercise.notes
            tvNotes.visibility = View.VISIBLE
        }

        card.setOnClickListener {
            if (isAdded) {
                ExerciseInfoBottomSheet.newInstance(exercise.exerciseName, exercise.exerciseDbId)
                    .show(childFragmentManager, "exercise_info")
            }
        }

        // E1: per-exercise edit controls. Move buttons are disabled at the list edges.
        val btnEdit = card.findViewById<View>(R.id.btn_edit_exercise)
        val btnMoveUp = card.findViewById<View>(R.id.btn_move_up)
        val btnMoveDown = card.findViewById<View>(R.id.btn_move_down)
        val btnDelete = card.findViewById<View>(R.id.btn_delete_exercise)

        btnEdit.setOnClickListener { showEditExerciseDialog(exercise) }
        btnDelete.setOnClickListener { confirmDeleteExercise(exercise) }

        btnMoveUp.isEnabled = index > 0
        btnMoveUp.alpha = if (index > 0) 1f else 0.35f
        btnMoveUp.setOnClickListener { viewModel.moveExercise(exercise, up = true) }

        btnMoveDown.isEnabled = index < count - 1
        btnMoveDown.alpha = if (index < count - 1) 1f else 0.35f
        btnMoveDown.setOnClickListener { viewModel.moveExercise(exercise, up = false) }

        return card
    }

    // Muscle classification is centralised in MuscleClassifier (shared with the Log screen
    // and the set-write path) so badges, day-type detection, and stored muscle groups all
    // agree. "" → "Training" for display; the neutral fallback colour stays this screen's.
    private fun getMuscleGroup(name: String): String =
        MuscleClassifier.displayName(name)

    private fun getMuscleStyle(group: String): Pair<String, String> =
        group to MuscleClassifier.colorFor(group, fallbackColor = "#607D8B")

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    private fun showRegenerateDayDialog(dayOfWeek: Int) {
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayName = dayNames.getOrElse(dayOfWeek - 1) { "Day $dayOfWeek" }
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        fun dpI(n: Int) = (n * dp).toInt()

        // Build equipment list: "No equipment" + all presets
        val presets = viewModel.presets.value
        val presetLabels = mutableListOf("No equipment (bodyweight only)")
        val presetIds = mutableListOf(-1L)
        presets.forEach { p -> presetLabels.add(p.name); presetIds.add(p.id) }
        val currentPresetIdx = presetIds.indexOf(viewModel.currentPresetId).coerceAtLeast(0)

        // Muscle focus options
        val muscleOptions = listOf("AI decides", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Full body", "Cardio")
        val currentMuscle = dominantMuscleGroup(viewModel.selectedDayExercises.value)
        val currentMuscleIdx = muscleOptions.indexOf(currentMuscle).coerceAtLeast(0)

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpI(20), dpI(4), dpI(20), dpI(4))
        }
        val wrapParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        // Equipment label + spinner
        container.addView(TextView(ctx).apply {
            text = "Equipment"
            textSize = 12f
            setTextColor(Color.parseColor("#8888A8"))
            layoutParams = wrapParams.also { it.topMargin = dpI(8); it.bottomMargin = dpI(2) }
        })
        val equipSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, presetLabels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(currentPresetIdx)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dpI(16) }
        }
        container.addView(equipSpinner)

        // Muscle focus label + spinner
        container.addView(TextView(ctx).apply {
            text = "Muscle focus"
            textSize = 12f
            setTextColor(Color.parseColor("#8888A8"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dpI(2) }
        })
        val muscleSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, muscleOptions).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(currentMuscleIdx)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        container.addView(muscleSpinner)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Swap $dayName's workout")
            .setMessage("Only this day changes — the rest of the week stays the same.")
            .setView(container)
            .setPositiveButton("Generate") { _, _ ->
                val selectedPresetId = presetIds[equipSpinner.selectedItemPosition]
                val equipment = viewModel.getEquipmentForPreset(selectedPresetId)
                val notes = viewModel.getNotesForPreset(selectedPresetId)
                val focus = muscleOptions[muscleSpinner.selectedItemPosition].let { if (it == "AI decides") "" else it }
                viewModel.regenerateDay(dayOfWeek, equipment, notes, focus)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── E1: manual program editing dialogs ─────────────────────────────────────────────────────────

    /** Build a vertically-stacked container of labelled inputs, mirroring the dialog look on this screen. */
    private fun editFieldsContainer(
        ctx: android.content.Context,
        nameView: View?,
        setsInput: EditText,
        repsInput: EditText,
        weightInput: EditText,
        notesInput: EditText
    ): LinearLayout {
        val dp = resources.displayMetrics.density
        fun dpI(n: Int) = (n * dp).toInt()
        fun label(text: String) = TextView(ctx).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#8888A8"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpI(10); it.bottomMargin = dpI(2) }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpI(20), dpI(4), dpI(20), dpI(4))
            if (nameView != null) { addView(label("Exercise")); addView(nameView) }
            addView(label("Sets")); addView(setsInput)
            addView(label("Reps (e.g. 8-12 or 30 min)")); addView(repsInput)
            addView(label("Target weight (kg, 0 = bodyweight)")); addView(weightInput)
            addView(label("Notes (optional)")); addView(notesInput)
        }
    }

    private fun showEditExerciseDialog(exercise: PlannedExercise) {
        val ctx = requireContext()
        val setsInput = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(exercise.sets.toString())
        }
        val repsInput = EditText(ctx).apply {
            setSingleLine(); setText(exercise.targetReps)
        }
        val weightInput = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(formatWeight(exercise.targetWeightKg))
        }
        val notesInput = EditText(ctx).apply { setText(exercise.notes) }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Edit ${exercise.exerciseName}")
            .setView(editFieldsContainer(ctx, null, setsInput, repsInput, weightInput, notesInput))
            .setPositiveButton("Save") { _, _ ->
                viewModel.editExercise(
                    exercise,
                    sets = setsInput.text?.toString()?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: exercise.sets,
                    reps = repsInput.text?.toString()?.trim().orEmpty().ifBlank { exercise.targetReps },
                    weight = weightInput.text?.toString()?.trim()?.replace(',', '.')?.toFloatOrNull()
                        ?.coerceAtLeast(0f) ?: exercise.targetWeightKg,
                    notes = notesInput.text?.toString()?.trim().orEmpty()
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteExercise(exercise: PlannedExercise) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove ${exercise.exerciseName}?")
            .setMessage("This removes it from this day's plan.")
            .setPositiveButton("Remove") { _, _ -> viewModel.deleteExercise(exercise) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddExerciseDialog(day: Int) {
        val ctx = requireContext()
        // Exercise-name picker sourced from the bundled catalog (same source E3's library uses),
        // de-duplicated and sorted; users can also type a custom name.
        val names = com.migul.treningsprogram.data.ExerciseCatalog.entries
            .map { it.name }
            .distinct()
            .sorted()
        val nameInput = android.widget.AutoCompleteTextView(ctx).apply {
            hint = "Start typing an exercise…"
            setSingleLine()
            setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names))
            threshold = 1
        }
        val setsInput = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText("3")
        }
        val repsInput = EditText(ctx).apply { setSingleLine(); setText("8-12") }
        val weightInput = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("0")
        }
        val notesInput = EditText(ctx).apply { hint = "Notes (optional)" }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Add exercise")
            .setView(editFieldsContainer(ctx, nameInput, setsInput, repsInput, weightInput, notesInput))
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    Snackbar.make(binding.root, "Enter an exercise name.", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.addExercise(
                    day = day,
                    name = name,
                    sets = setsInput.text?.toString()?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                    reps = repsInput.text?.toString()?.trim().orEmpty().ifBlank { "8-12" },
                    weight = weightInput.text?.toString()?.trim()?.replace(',', '.')?.toFloatOrNull()
                        ?.coerceAtLeast(0f) ?: 0f,
                    notes = notesInput.text?.toString()?.trim().orEmpty()
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dominantMuscleGroup(exercises: List<PlannedExercise>): String {
        if (exercises.isEmpty()) return "AI decides"
        val dominant = exercises.map { getMuscleGroup(it.exerciseName) }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key ?: return "AI decides"
        return if (dominant == "Training") "AI decides" else dominant
    }

    // ── E2: program switcher ─────────────────────────────────────────────────────────────────────

    private fun renderProgramSwitcher(programs: List<Program>, active: Program?) {
        if (_binding == null) return
        if (programs.isEmpty()) {
            binding.cardProgramSwitcher.visibility = View.GONE
            return
        }
        binding.cardProgramSwitcher.visibility = View.VISIBLE
        val labels = programs.map { p ->
            buildString {
                append(p.name)
                if (p.mesocycleWeeks > 0) append("  •  ${p.mesocycleWeeks}-wk block")
                if (p.isFrozen) append("  •  frozen")
            }
        }
        suppressProgramSpinner = true
        binding.spinnerProgram.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, labels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val activeIdx = programs.indexOfFirst { it.id == active?.id }.coerceAtLeast(0)
        binding.spinnerProgram.setSelection(activeIdx)
        // Reset the guard after the pending selection callback has been dispatched.
        binding.spinnerProgram.post { suppressProgramSpinner = false }
    }

    private fun showSaveProgramDialog() {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val input = EditText(ctx).apply {
            hint = "Program name"
            setSingleLine()
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Save as new program")
            .setMessage("Saves the current week's plan as a new program and switches to it.")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotBlank()) viewModel.saveCurrentAsProgram(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProgramOptionsDialog() {
        val active = viewModel.activeProgram.value ?: return
        val options = mutableListOf<String>()
        options.add("Rename")
        // B09: the default regenerate now PRESERVES days you've already logged and only rebuilds the
        // rest of the week (rebalanced around your logged work). Full fresh-week regen lives in
        // Settings → AI & Program.
        options.add("Regenerate (keep logged days)")
        // Mesocycle toggle.
        options.add(if (active.mesocycleWeeks > 0) "Turn off mesocycle block" else "Make a mesocycle block")
        // Item 4: the auto-rebalance toggle moved to the "App Settings" screen (Profile → Settings).
        // Frozen toggle (assumption N).
        options.add(if (active.isFrozen) "Unfreeze (resume weekly AI adaptation)" else "Freeze (stop weekly AI adaptation)")
        if (viewModel.programs.value.size > 1) options.add("Delete this program")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(active.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Rename" -> showRenameProgramDialog(active)
                    "Regenerate (keep logged days)" -> {
                        val presetId = viewModel.currentPresetId
                        viewModel.regeneratePreservingLoggedDays(
                            equipment = viewModel.getEquipmentForPreset(presetId),
                            equipmentNotes = viewModel.getNotesForPreset(presetId)
                        )
                    }
                    "Make a mesocycle block" -> showMesocycleDialog()
                    "Turn off mesocycle block" -> viewModel.setMesocycle(0)
                    "Freeze (stop weekly AI adaptation)" -> viewModel.setFrozen(true)
                    "Unfreeze (resume weekly AI adaptation)" -> viewModel.setFrozen(false)
                    "Delete this program" -> confirmDeleteProgram(active)
                }
            }
            .show()
    }

    private fun showRenameProgramDialog(program: Program) {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val input = EditText(ctx).apply {
            setText(program.name)
            setSingleLine()
            setSelection(text.length)
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Rename program")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotBlank()) viewModel.renameActiveProgram(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMesocycleDialog() {
        val weeks = listOf(4, 5, 6, 8)
        val labels = weeks.map { "$it-week block" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mesocycle block length")
            .setItems(labels) { _, which -> viewModel.setMesocycle(weeks[which]) }
            .show()
    }

    private fun confirmDeleteProgram(program: Program) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete \"${program.name}\"?")
            .setMessage("This removes the program and its plan. Your logged workout history is kept.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteProgram(program.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
