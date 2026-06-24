package com.migul.treningsprogram.ui.library

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.migul.treningsprogram.data.DbExerciseEntry
import com.migul.treningsprogram.data.ExerciseCatalog
import com.migul.treningsprogram.databinding.ItemLibraryExerciseBinding

class ExerciseLibraryAdapter(
    private val onClick: (DbExerciseEntry) -> Unit
) : ListAdapter<DbExerciseEntry, ExerciseLibraryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLibraryExerciseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemLibraryExerciseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: DbExerciseEntry) {
            binding.tvName.text = entry.name

            // Subtitle: primary muscle(s) + equipment, gracefully omitting missing pieces.
            val muscles = entry.primaryMuscles
                .joinToString(", ") { it.replaceFirstChar(Char::uppercaseChar) }
            val equip = entry.equipment?.replaceFirstChar(Char::uppercaseChar)
            binding.tvSubtitle.text = listOfNotNull(
                muscles.takeIf { it.isNotBlank() },
                equip?.takeIf { it.isNotBlank() }
            ).joinToString("  •  ")

            // Image: local-first from bundled assets. Hide the thumb if the entry has no
            // images or the asset fails to load (degrade gracefully).
            val thumb = binding.ivThumb
            if (entry.images.isNotEmpty()) {
                thumb.visibility = View.VISIBLE
                thumb.load(Uri.parse(ExerciseCatalog.getImageSource(entry.id, 0))) {
                    crossfade(true)
                    listener(onError = { _, _ -> thumb.visibility = View.GONE })
                }
            } else {
                thumb.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(entry) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DbExerciseEntry>() {
            override fun areItemsTheSame(a: DbExerciseEntry, b: DbExerciseEntry) = a.id == b.id
            override fun areContentsTheSame(a: DbExerciseEntry, b: DbExerciseEntry) = a == b
        }
    }
}
