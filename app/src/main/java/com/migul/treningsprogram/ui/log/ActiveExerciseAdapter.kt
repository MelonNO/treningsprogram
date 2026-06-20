package com.migul.treningsprogram.ui.log

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.databinding.ItemActiveSetBinding

class ActiveExerciseAdapter(
    private val onDelete: (WorkoutSet) -> Unit
) : ListAdapter<WorkoutSet, ActiveExerciseAdapter.SetViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SetViewHolder {
        val binding = ItemActiveSetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SetViewHolder(private val binding: ItemActiveSetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(set: WorkoutSet) {
            binding.tvExerciseName.text = set.exerciseName
            binding.chipSetNumber.text = "Set ${set.setNumber}"
            binding.chipReps.text = "${set.reps} reps"
            binding.chipWeight.text = "${set.weightKg} kg"

            val (badgeText, badgeColor) = getMuscleStyle(set.muscleGroup)
            binding.tvMuscleBadge.text = badgeText
            binding.tvMuscleBadge.setTextColor(Color.parseColor(badgeColor))

            binding.btnDelete.setOnClickListener { onDelete(set) }
        }

        private fun getMuscleStyle(group: String): Pair<String, String> = when (group) {
            "Cardio"    -> "CA" to "#00BCD4"
            "Chest"     -> "CH" to "#E91E63"
            "Back"      -> "BA" to "#2196F3"
            "Legs"      -> "LE" to "#4CAF50"
            "Shoulders" -> "SH" to "#9C27B0"
            "Arms"      -> "AR" to "#FF5722"
            "Core"      -> "CO" to "#FF9800"
            else        -> "FT" to "#607D8B"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WorkoutSet>() {
            override fun areItemsTheSame(a: WorkoutSet, b: WorkoutSet) = a.id == b.id
            override fun areContentsTheSame(a: WorkoutSet, b: WorkoutSet) = a == b
        }
    }
}
