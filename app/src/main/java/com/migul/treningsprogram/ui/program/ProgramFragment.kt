package com.migul.treningsprogram.ui.program

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
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
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.WgerRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.databinding.FragmentProgramBinding
import com.migul.treningsprogram.ui.log.ExerciseInfoBottomSheet
import com.migul.treningsprogram.ui.shared.SharedWorkoutResultViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ADMIN_TIME_PER_EXERCISE_SECONDS = 60

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

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.weekPlan.collect { plan ->
                        val hasPlan = plan.isNotEmpty()
                        binding.cardEmptyState.visibility = if (hasPlan) View.GONE else View.VISIBLE
                        binding.cardWeek.visibility = if (hasPlan) View.VISIBLE else View.GONE
                        binding.layoutDaySection.visibility = if (hasPlan) View.VISIBLE else View.GONE
                        if (hasPlan) updateDayChips(plan, viewModel.selectedDay.value)
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
        val result = sharedResultVm.consumeForProgram() ?: return
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

            val totalSec = exercises.sumOf { ex -> exerciseEstimateSeconds(ex) }
            val totalMins = (totalSec + 30) / 60
            binding.tvTotalTime.text = "~${totalMins}m"

            binding.btnStartDayWorkout.setOnClickListener {
                val bundle = Bundle().apply {
                    putLong("sessionId", -1L)
                    putInt("dayOfWeek", day)
                }
                findNavController().navigate(R.id.action_program_to_log, bundle)
            }
        } else {
            binding.tvDayCompletion.text = ""
            binding.tvWorkoutType.text = "Take it easy today"
            binding.tvTotalTime.text = ""
        }

        binding.btnRegenerateDay.visibility = View.VISIBLE
        binding.btnRegenerateDay.setOnClickListener { showRegenerateDayDialog(day) }

        binding.layoutExercises.removeAllViews()
        exercises.forEach { ex ->
            binding.layoutExercises.addView(inflateDayOverviewCard(ex))
        }
    }

    private fun inflateDayOverviewCard(exercise: PlannedExercise): View {
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
            val totalSec = exerciseEstimateSeconds(exercise)
            val mins = (totalSec + 30) / 60
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

        return card
    }

    private fun exerciseEstimateSeconds(ex: PlannedExercise): Int {
        return if (getMuscleGroup(ex.exerciseName) == "Cardio") {
            parseCardioSeconds(ex.targetReps) + ADMIN_TIME_PER_EXERCISE_SECONDS
        } else {
            val maxReps = Regex("\\d+").findAll(ex.targetReps).lastOrNull()?.value?.toIntOrNull() ?: 10
            ex.sets * (maxReps * 3) + (ex.sets - 1) * ex.recommendedRestSeconds + ADMIN_TIME_PER_EXERCISE_SECONDS
        }
    }

    private fun getMuscleGroup(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.containsAny("run", "jog", "sprint", "cardio", "hiit", "bike", "cycling", "treadmill", "burpee", "mountain climber", "high knee", "jump rope", "tempo", "interval run") -> "Cardio"
            lower.containsAny("bench", "chest", "fly", "flye", "pec", "push-up", "pushup", "dip") -> "Chest"
            // Legs before Back so "Romanian Deadlift", "Back Squat", "Stiff-Leg Deadlift" etc resolve correctly
            lower.containsAny("squat", "leg press", "lunge", "calf", "hamstring", "quad", "romanian", "rdl", "glute", "hip thrust", "leg curl", "leg extension", "hip hinge", "step up", "step-up", "box jump", "split squat", "wall sit", "sumo") -> "Legs"
            lower.containsAny("row", "pulldown", "pull-up", "pullup", "chin-up", "chinup", "lat ", "deadlift", "shrug", "back") -> "Back"
            lower.containsAny("shoulder", "overhead", "lateral raise", "face pull", "delt", "military") -> "Shoulders"
            lower.containsAny("curl", "tricep", "bicep", "arm") -> "Arms"
            lower.containsAny("plank", "crunch", "ab ", "abs", "core", "sit-up", "sit up", "russian", "leg raise") -> "Core"
            else -> "Training"
        }
    }

    private fun getMuscleStyle(group: String): Pair<String, String> = when (group) {
        "Cardio"    -> "Cardio" to "#00BCD4"
        "Chest"     -> "Chest" to "#E91E63"
        "Back"      -> "Back" to "#2196F3"
        "Legs"      -> "Legs" to "#4CAF50"
        "Shoulders" -> "Shoulders" to "#9C27B0"
        "Arms"      -> "Arms" to "#FF5722"
        "Core"      -> "Core" to "#FF9800"
        else        -> "Training" to "#607D8B"
    }

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    private fun String.containsAny(vararg keywords: String) = keywords.any { this.contains(it) }

    private fun parseCardioSeconds(targetReps: String): Int {
        // "30 min" → 1800, "5km" → 1500 (@ 5min/km), "6×400m" → fallback 30 min
        val minMatch = Regex("(\\d+)\\s*min", RegexOption.IGNORE_CASE).find(targetReps)
        if (minMatch != null) return minMatch.groupValues[1].toInt() * 60
        val kmMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*km", RegexOption.IGNORE_CASE).find(targetReps)
        if (kmMatch != null) return (kmMatch.groupValues[1].toDouble() * 5 * 60).toInt()
        return 1800
    }

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

    private fun dominantMuscleGroup(exercises: List<PlannedExercise>): String {
        if (exercises.isEmpty()) return "AI decides"
        val dominant = exercises.map { getMuscleGroup(it.exerciseName) }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key ?: return "AI decides"
        return if (dominant == "Training") "AI decides" else dominant
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
