package com.migul.treningsprogram.ui.log

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.migul.treningsprogram.data.ExerciseCatalog

class ExerciseInfoBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(exerciseName: String, dbId: String? = null) =
            ExerciseInfoBottomSheet().apply {
                arguments = Bundle().also {
                    it.putString("name", exerciseName)
                    it.putString("dbId", dbId)
                }
            }
    }

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

        val name  = arguments?.getString("name") ?: ""
        val dbId  = arguments?.getString("dbId")
        val dbEntry = dbId?.let { ExerciseCatalog.getDbEntry(it) }
        val staticEntry = ExerciseCatalog.getEntry(name)

        // Title
        layout.addView(TextView(requireContext()).apply {
            text = dbEntry?.name ?: name
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, smallPad)
        })

        when {
            dbEntry != null -> {
                // Primary muscles
                if (dbEntry.primaryMuscles.isNotEmpty()) {
                    layout.addView(metaLine(
                        "Muscles: ${dbEntry.primaryMuscles.joinToString(", ").replaceFirstChar { it.uppercaseChar() }}",
                        density
                    ))
                }
                // Equipment
                if (!dbEntry.equipment.isNullOrBlank()) {
                    layout.addView(metaLine(
                        "Equipment: ${dbEntry.equipment.replaceFirstChar { it.uppercaseChar() }}",
                        density
                    ))
                }
                // Level / category
                val meta = listOfNotNull(dbEntry.level, dbEntry.category)
                    .joinToString("  •  ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                if (meta.isNotBlank()) {
                    layout.addView(metaLine(meta, density))
                }

                layout.addView(divider(density, medPad))

                // Numbered steps
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
