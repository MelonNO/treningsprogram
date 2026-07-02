package com.migul.treningsprogram.ui.library

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import coil.load
import com.migul.treningsprogram.data.ExerciseCatalog
import com.migul.treningsprogram.databinding.FragmentExerciseDetailBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * BROWSE-ONLY detail view for a single catalog exercise (E3). Receives the catalog [exerciseId]
 * as a plain Bundle arg and renders muscles, equipment, and instructions with an image where
 * available. Missing image/instructions degrade gracefully (image hidden, fallback text shown).
 */
@AndroidEntryPoint
class ExerciseDetailFragment : Fragment() {

    private var _binding: FragmentExerciseDetailBinding? = null
    private val binding get() = _binding!!

    // Stage-3 item 7: two-frame image alternation, mirroring ExerciseInfoBottomSheet's
    // workout-surface animation (same 1200 ms cadence).
    private val imageHandler = Handler(Looper.getMainLooper())
    private var imageAlternateRunnable: Runnable? = null
    private var imageFrame = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExerciseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val exerciseId = arguments?.getString("exerciseId").orEmpty()
        val entry = ExerciseCatalog.getDbEntry(exerciseId)

        if (entry == null) {
            // Defensive: unknown id (e.g. catalog failed to load). Show a graceful message.
            binding.tvName.text = "Exercise unavailable"
            binding.tvMuscles.visibility = View.GONE
            binding.tvEquipment.visibility = View.GONE
            binding.tvMeta.visibility = View.GONE
            binding.tvInstructions.text = "No details available."
            return
        }

        binding.tvName.text = entry.name

        // Image — local-first from bundled assets; hide on missing or load error.
        // Stage-3 item 7: with two frames available, alternate them like the workout surfaces
        // so the movement reads as an animation. One frame (or none) degrades exactly as before.
        if (entry.images.size >= 2) {
            binding.ivImage.visibility = View.VISIBLE
            imageFrame = 0
            var firstLoad = true
            imageAlternateRunnable = object : Runnable {
                override fun run() {
                    val b = _binding ?: return
                    b.ivImage.load(Uri.parse(ExerciseCatalog.getImageSource(entry.id, imageFrame))) {
                        crossfade(200)
                        listener(onError = { _, _ ->
                            // First frame failing = no usable image (hide, as today);
                            // later failures just keep the last good frame.
                            if (firstLoad) _binding?.ivImage?.visibility = View.GONE
                        })
                    }
                    firstLoad = false
                    imageFrame = 1 - imageFrame
                    imageHandler.postDelayed(this, 1200L)
                }
            }
            imageHandler.post(imageAlternateRunnable!!)
        } else if (entry.images.isNotEmpty()) {
            val iv = binding.ivImage
            iv.visibility = View.VISIBLE
            iv.load(Uri.parse(ExerciseCatalog.getImageSource(entry.id, 0))) {
                crossfade(true)
                listener(onError = { _, _ -> iv.visibility = View.GONE })
            }
        } else {
            binding.ivImage.visibility = View.GONE
        }

        // Target muscles (primary + secondary), degrading when fields are empty.
        val primary = entry.primaryMuscles
            .joinToString(", ") { it.replaceFirstChar(Char::uppercaseChar) }
        val secondary = entry.secondaryMuscles
            .joinToString(", ") { it.replaceFirstChar(Char::uppercaseChar) }
        val muscleText = buildString {
            if (primary.isNotBlank()) append("Target: $primary")
            if (secondary.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("Secondary: $secondary")
            }
        }
        if (muscleText.isNotBlank()) {
            binding.tvMuscles.text = muscleText
        } else {
            binding.tvMuscles.visibility = View.GONE
        }

        // Equipment
        val equip = entry.equipment?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar(Char::uppercaseChar)
        if (equip != null) {
            binding.tvEquipment.text = "Equipment: $equip"
        } else {
            binding.tvEquipment.visibility = View.GONE
        }

        // Level / category
        val meta = listOfNotNull(entry.level, entry.category)
            .filter { it.isNotBlank() }
            .joinToString("  •  ") { it.replaceFirstChar(Char::uppercaseChar) }
        if (meta.isNotBlank()) {
            binding.tvMeta.text = meta
        } else {
            binding.tvMeta.visibility = View.GONE
        }

        // Instructions — numbered steps, or a graceful fallback when none exist.
        val steps = entry.instructions.filter { it.isNotBlank() }
        binding.tvInstructions.text = if (steps.isNotEmpty()) {
            steps.mapIndexed { i, step -> "${i + 1}. $step" }.joinToString("\n\n")
        } else {
            "No instructions available."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stage-3 item 7: stop the alternation cleanly — no leaked timers on fast exit.
        imageAlternateRunnable?.let { imageHandler.removeCallbacks(it) }
        imageAlternateRunnable = null
        _binding = null
    }
}
