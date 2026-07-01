package com.migul.treningsprogram.ui.log

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import coil.load
import coil.size.Scale
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.migul.treningsprogram.data.ExerciseCatalog

class ExerciseInfoBottomSheet : BottomSheetDialogFragment() {

    companion object {
        /**
         * [aiNote] is the AI's short, exercise-specific explanation (PlannedExercise.notes).
         * When present it is shown as a distinct "Coach's note" section above the DB info
         * (Item 5). Defaults to null so callers that have no AI note are unaffected.
         */
        fun newInstance(exerciseName: String, dbId: String? = null, aiNote: String? = null) =
            ExerciseInfoBottomSheet().apply {
                arguments = Bundle().also {
                    it.putString("name", exerciseName)
                    it.putString("dbId", dbId)
                    it.putString("aiNote", aiNote)
                }
            }
    }

    private var imageView: ImageView? = null
    private val imageHandler = Handler(Looper.getMainLooper())
    private var imageAlternateRunnable: Runnable? = null
    private var imageFrame = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val density = resources.displayMetrics.density
        val paddingPx = (16 * density).toInt()
        val smallPad  = (8 * density).toInt()
        val medPad    = (12 * density).toInt()

        val scrollView = ScrollView(requireContext()).apply {
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val name    = arguments?.getString("name") ?: ""
        val dbId    = arguments?.getString("dbId")
        val aiNote  = arguments?.getString("aiNote")?.trim().orEmpty()
        val dbEntry = dbId?.let { ExerciseCatalog.getDbEntry(it) }
        val staticEntry = ExerciseCatalog.getEntry(name)

        // Exercise images — show if we have a dbId with local assets, or a static URL
        val hasDbImages = dbId != null
        val staticImageUrl = if (!hasDbImages) ExerciseCatalog.getImageUrl(name) else null

        if (hasDbImages || staticImageUrl != null) {
            val iv = ImageView(requireContext()).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (200 * density).toInt()
                ).also { it.bottomMargin = medPad }
                clipToOutline = true
            }
            layout.addView(iv)
            imageView = iv

            if (hasDbImages && dbId != null) {
                imageFrame = 0
                imageAlternateRunnable = object : Runnable {
                    override fun run() {
                        val currentIv = imageView ?: return
                        currentIv.load(Uri.parse(ExerciseCatalog.getImageSource(dbId, imageFrame))) {
                            crossfade(200)
                            scale(Scale.FILL)
                            listener(onError = { _, _ -> /* keep last good frame */ })
                        }
                        imageFrame = 1 - imageFrame
                        imageHandler.postDelayed(this, 1200L)
                    }
                }
                imageHandler.post(imageAlternateRunnable!!)
            } else if (staticImageUrl != null) {
                iv.load(staticImageUrl) {
                    crossfade(true)
                    listener(onError = { _, _ -> iv.visibility = View.GONE })
                }
            }
        }

        // Title
        layout.addView(TextView(requireContext()).apply {
            text = dbEntry?.name ?: name
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, smallPad)
        })

        // Item 5 — the AI's short, exercise-specific note, shown alongside the DB info.
        // Distinguished from the DB content by a labelled, accent-coloured block. Surfaced
        // in every branch below (including the no-DB-match case) since it sits above them.
        if (aiNote.isNotBlank()) {
            layout.addView(TextView(requireContext()).apply {
                text = "Coach's note"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF7FE9E1.toInt())
                setPadding(0, 0, 0, (2 * density).toInt())
            })
            layout.addView(TextView(requireContext()).apply {
                text = aiNote
                textSize = 15f
                setLineSpacing(0f, 1.35f)
                setPadding(0, 0, 0, smallPad)
            })
            layout.addView(divider(density, medPad))
        }

        when {
            dbEntry != null -> {
                if (dbEntry.primaryMuscles.isNotEmpty()) {
                    layout.addView(metaLine(
                        "Muscles: ${dbEntry.primaryMuscles.joinToString(", ").replaceFirstChar { it.uppercaseChar() }}",
                        density
                    ))
                }
                if (!dbEntry.equipment.isNullOrBlank()) {
                    layout.addView(metaLine(
                        "Equipment: ${dbEntry.equipment.replaceFirstChar { it.uppercaseChar() }}",
                        density
                    ))
                }
                val meta = listOfNotNull(dbEntry.level, dbEntry.category)
                    .joinToString("  •  ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                if (meta.isNotBlank()) {
                    layout.addView(metaLine(meta, density))
                }
                layout.addView(divider(density, medPad))
                dbEntry.instructions.forEachIndexed { i, step ->
                    layout.addView(TextView(requireContext()).apply {
                        text = "${i + 1}. $step"
                        textSize = 14f
                        setLineSpacing(0f, 1.4f)
                        setPadding(0, 0, 0, (8 * density).toInt())
                    })
                }
            }

            staticEntry != null -> {
                if (staticEntry.equipment.isNotEmpty()) {
                    layout.addView(metaLine("Equipment: ${staticEntry.equipment.joinToString(", ")}", density))
                }
                layout.addView(metaLine(staticEntry.muscleGroup, density))
                layout.addView(divider(density, medPad))
                layout.addView(TextView(requireContext()).apply {
                    text = staticEntry.instructions
                    textSize = 15f
                    setLineSpacing(0f, 1.4f)
                })
            }

            else -> {
                layout.addView(divider(density, medPad))
                layout.addView(TextView(requireContext()).apply {
                    text = "Set up properly and engage your core throughout the movement. " +
                           "Control the eccentric (lowering) phase and maintain good form. " +
                           "Adjust weight or difficulty as needed."
                    textSize = 15f
                    setLineSpacing(0f, 1.4f)
                })
            }
        }

        scrollView.addView(layout)
        return scrollView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        imageAlternateRunnable?.let { imageHandler.removeCallbacks(it) }
        imageAlternateRunnable = null
        imageView = null
    }

    private fun metaLine(text: String, density: Float) = TextView(requireContext()).apply {
        this.text = text
        textSize = 14f
        setTextColor(0xFF888888.toInt())
        setPadding(0, 0, 0, (4 * density).toInt())
    }

    private fun divider(density: Float, bottomPad: Int) = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()
        ).also { it.bottomMargin = bottomPad; it.topMargin = bottomPad }
        setBackgroundColor(0x22FFFFFF)
    }
}
