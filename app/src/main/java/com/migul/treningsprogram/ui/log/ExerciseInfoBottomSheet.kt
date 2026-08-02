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
import com.migul.treningsprogram.data.ExerciseInfoCorrections
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ExerciseInfoBottomSheet : BottomSheetDialogFragment() {

    /** QoL 2026-08 item 04: user mismatch flags + re-match overrides. */
    @Inject lateinit var corrections: ExerciseInfoCorrections

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

        /**
         * QoL item 04 (History week-browser): the same info sheet, prefixed with a read-only
         * "Performed" section — the day's logged sets (reps × weight), warm-ups distinguished,
         * and at most one PR-flagged set per the baseline rule ([prIndex] = -1 for none).
         * Parallel primitive arrays keep the Bundle trivial; all existing callers are unaffected.
         */
        fun newInstanceForHistory(
            exerciseName: String,
            dbId: String?,
            performedLabel: String,
            setNumbers: IntArray,
            reps: IntArray,
            weights: FloatArray,
            warmups: BooleanArray,
            prIndex: Int
        ) = ExerciseInfoBottomSheet().apply {
            arguments = Bundle().also {
                it.putString("name", exerciseName)
                it.putString("dbId", dbId)
                it.putString("perfLabel", performedLabel)
                it.putIntArray("perfSetNums", setNumbers)
                it.putIntArray("perfReps", reps)
                it.putFloatArray("perfWeights", weights)
                it.putBooleanArray("perfWarmups", warmups)
                it.putInt("perfPrIndex", prIndex)
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
        val passedDbId = arguments?.getString("dbId")
        val aiNote  = arguments?.getString("aiNote")?.trim().orEmpty()
        // Item 04: a user re-match override wins over the (possibly stale, plan-row-denormalized)
        // dbId from the caller, so the corrected info shows from EVERY entry point.
        val dbId = corrections.overrideFor(name)?.takeIf { ExerciseCatalog.byId.containsKey(it) }
            ?: passedDbId
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

        // Title — QoL 2026-08 item 06: ALWAYS the program's exercise name (the name the user
        // tapped), matched or not. The DB entry's own name appears above the DB content below.
        layout.addView(TextView(requireContext()).apply {
            text = name.ifBlank { dbEntry?.name.orEmpty() }
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, smallPad)
        })

        // QoL item 04 — the performed-data half of the history detail view: the tapped day's
        // logged sets for this exercise, above the exercise info so one tap shows everything.
        val perfReps = arguments?.getIntArray("perfReps")
        if (perfReps != null && perfReps.isNotEmpty()) {
            val setNums  = arguments?.getIntArray("perfSetNums") ?: IntArray(perfReps.size) { it + 1 }
            val weights  = arguments?.getFloatArray("perfWeights") ?: FloatArray(perfReps.size)
            val warmups  = arguments?.getBooleanArray("perfWarmups") ?: BooleanArray(perfReps.size)
            val prIndex  = arguments?.getInt("perfPrIndex", -1) ?: -1

            layout.addView(TextView(requireContext()).apply {
                text = arguments?.getString("perfLabel").orEmpty().ifBlank { "Performed" }
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF7FE9E1.toInt())
                setPadding(0, 0, 0, (6 * density).toInt())
            })
            for (i in perfReps.indices) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
                }
                val main = TextView(requireContext()).apply {
                    val w = weights.getOrElse(i) { 0f }
                    val wTxt = if (w > 0f) {
                        val n = if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()
                        "${perfReps[i]} × $n kg"
                    } else "${perfReps[i]} reps"
                    text = "Set ${setNums.getOrElse(i) { i + 1 }}   $wTxt"
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                    if (warmups.getOrElse(i) { false }) {
                        setTextColor(0xFF888888.toInt())
                    }
                }
                row.addView(main)
                if (warmups.getOrElse(i) { false }) {
                    row.addView(TextView(requireContext()).apply {
                        text = "warm-up"
                        textSize = 12f
                        setTextColor(0xFF888888.toInt())
                    })
                } else if (i == prIndex) {
                    row.addView(TextView(requireContext()).apply {
                        text = "PR"
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(0xFFFFD54A.toInt())
                    })
                }
                layout.addView(row)
            }
            layout.addView(divider(density, medPad))
        }

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
                // Item 06: the DB entry's own name, right above the database-sourced content, so
                // the user can see which entry the info comes from. Hidden when identical to the
                // program name (D3 — never show the same string twice).
                if (!dbEntry.name.trim().equals(name.trim(), ignoreCase = true)) {
                    layout.addView(TextView(requireContext()).apply {
                        text = "From database: ${dbEntry.name}"
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(0xFF7FE9E1.toInt())
                        setPadding(0, 0, 0, (4 * density).toInt())
                    })
                }
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

        // QoL 2026-08 item 04: flag "this database info doesn't match this exercise" — available
        // from every entry point that opens this sheet. Flagging only records the pair (nothing
        // else changes); the list lives in Settings → About → Debug.
        if (name.isNotBlank()) {
            layout.addView(divider(density, medPad))
            layout.addView(TextView(requireContext()).apply {
                fun flaggedState() {
                    text = "Flagged — manage under Settings → About → Debug → Flagged Matches"
                    setTextColor(0xFF888888.toInt())
                    isClickable = false
                }
                if (corrections.isFlagged(name)) {
                    flaggedState()
                } else {
                    text = "Wrong pictures or instructions? Flag this match"
                    setTextColor(0xFF7FE9E1.toInt())
                    setPadding(0, 0, 0, smallPad)
                    setOnClickListener {
                        corrections.addFlag(name, dbId)
                        flaggedState()
                    }
                }
                textSize = 13f
                setPadding(0, 0, 0, smallPad)
            })
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
