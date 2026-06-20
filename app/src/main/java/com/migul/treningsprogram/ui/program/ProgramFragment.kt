package com.migul.treningsprogram.ui.program

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.repository.WgerRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.databinding.FragmentProgramBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProgramFragment : Fragment() {

    private var _binding: FragmentProgramBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProgramViewModel by viewModels()

    @Inject lateinit var wgerRepository: WgerRepository

    private val dayAbbreviations = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val dayChipViews = mutableListOf<View>()

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

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.weekPlan.collect { plan ->
                        val hasPlan = plan.isNotEmpty()
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
                        binding.progressWeek.progress = if (total > 0) (logged * 100 / total) else 0
                    }
                }
            }
        }
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
            when {
                i == selectedDay && i == today -> {
                    // Today AND selected — bold filled
                    abbr.background = requireContext().getDrawable(R.drawable.bg_day_selected)
                    abbr.setTextColor(Color.WHITE)
                    abbr.textSize = 11.5f
                }
                i == selectedDay -> {
                    abbr.background = requireContext().getDrawable(R.drawable.bg_day_selected)
                    abbr.setTextColor(Color.WHITE)
                    abbr.textSize = 11f
                }
                i == today -> {
                    // Today but not selected — outlined indicator
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
                if (logged == total && total > 0) Color.parseColor("#4CAF50")
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
                    val firstMuscle = getMuscleGroup(exercises.firstOrNull()?.exerciseName ?: "")
                    "$firstMuscle focus"
                }
            }
            binding.tvWorkoutType.text = "${exercises.size} exercises  •  $typeLabel"

            val totalSec = exercises.sumOf { ex ->
                if (getMuscleGroup(ex.exerciseName) == "Cardio") {
                    parseCardioSeconds(ex.targetReps)
                } else {
                    val maxReps = Regex("\\d+").findAll(ex.targetReps).lastOrNull()?.value?.toIntOrNull() ?: 10
                    ex.sets * (maxReps * 3) + (ex.sets - 1) * ex.recommendedRestSeconds
                }
            }
            val totalMins = totalSec / 60
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
            val maxReps = Regex("\\d+").findAll(exercise.targetReps).lastOrNull()?.value?.toIntOrNull() ?: 10
            val setDurationSec = maxReps * 3
            val totalSec = exercise.sets * setDurationSec + (exercise.sets - 1) * exercise.recommendedRestSeconds
            val mins = (totalSec + 30) / 60  // round to nearest minute
            tvExerciseTime.text = "~${mins}m"
        }

        if (exercise.notes.isNotBlank()) {
            tvNotes.text = exercise.notes
            tvNotes.visibility = View.VISIBLE
        }

        return card
    }

    private fun getMuscleGroup(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.containsAny("run", "jog", "sprint", "cardio", "hiit", "bike", "cycling", "treadmill", "burpee", "mountain climber", "high knee", "jump rope", "tempo", "interval run") -> "Cardio"
            lower.containsAny("bench", "chest", "fly", "flye", "pec", "push-up", "pushup", "dip") -> "Chest"
            lower.containsAny("row", "pulldown", "pull-up", "pullup", "lat ", "deadlift", "shrug", "back") -> "Back"
            lower.containsAny("squat", "leg press", "lunge", "calf", "hamstring", "quad", "romanian", "glute") -> "Legs"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
