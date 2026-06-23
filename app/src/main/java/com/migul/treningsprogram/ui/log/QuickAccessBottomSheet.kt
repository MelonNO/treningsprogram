package com.migul.treningsprogram.ui.log

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Quick-access exercise menu (Item 6). Opened by tapping the top "Exercise X / Y" bar.
 * Lists every exercise in the day's session with finished / current / upcoming status,
 * lets the user jump to any of them, and offers an "Add exercise" entry.
 *
 * Built programmatically to match the app's other bottom sheets (e.g. ExerciseInfoBottomSheet)
 * and to stay independent of view-binding so the host fragment can hand it a fresh data
 * snapshot + callbacks right before showing.
 */
class QuickAccessBottomSheet : BottomSheetDialogFragment() {

    data class Row(val name: String, val status: Status, val index: Int)

    enum class Status { FINISHED, CURRENT, UPCOMING }

    /** Set by the host fragment before show(); not retained across recreation. */
    var rows: List<Row> = emptyList()
    var onJump: ((Int) -> Unit)? = null
    var onAddExercise: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        // Host fragment was recreated (e.g. rotation) and didn't re-supply data — close.
        if (rows.isEmpty() || onJump == null) {
            dismissAllowingStateLoss()
            return View(requireContext())
        }

        val scroll = ScrollView(requireContext()).apply {
            setPadding(pad, pad, pad, pad)
        }
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

        layout.addView(TextView(requireContext()).apply {
            text = "Exercises"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, (12 * density).toInt())
        })

        rows.forEach { row ->
            layout.addView(makeExerciseRow(row, density))
        }

        // Divider before the add entry
        layout.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()
            ).also { it.topMargin = (8 * density).toInt(); it.bottomMargin = (8 * density).toInt() }
            setBackgroundColor(0x22FFFFFF)
        })

        // "Add exercise" empty entry
        layout.addView(TextView(requireContext()).apply {
            text = "+  Add exercise"
            textSize = 16f
            setTextColor(Color.parseColor("#7C67F5"))
            setTypeface(null, Typeface.BOLD)
            val v = (14 * density).toInt()
            setPadding((4 * density).toInt(), v, (4 * density).toInt(), v)
            isClickable = true
            isFocusable = true
            setBackgroundResource(selectableBg())
            setOnClickListener {
                onAddExercise?.invoke()
                dismissAllowingStateLoss()
            }
        })

        scroll.addView(layout)
        return scroll
    }

    private fun makeExerciseRow(row: Row, density: Float): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val v = (12 * density).toInt()
            setPadding((4 * density).toInt(), v, (4 * density).toInt(), v)
            isClickable = true
            isFocusable = true
            setBackgroundResource(selectableBg())
            setOnClickListener {
                onJump?.invoke(row.index)
                dismissAllowingStateLoss()
            }
        }

        val (badge, badgeColor, nameColor) = when (row.status) {
            Status.FINISHED -> Triple("✓", "#4CAF50", "#9A9AB0")
            Status.CURRENT  -> Triple("▶", "#7C67F5", "#FFFFFF")
            Status.UPCOMING -> Triple("○", "#6A6A80", "#C8C8D8")
        }

        container.addView(TextView(requireContext()).apply {
            text = badge
            textSize = 16f
            setTextColor(Color.parseColor(badgeColor))
            width = (28 * density).toInt()
            gravity = Gravity.CENTER
        })

        container.addView(TextView(requireContext()).apply {
            text = "${row.index + 1}. ${row.name}"
            textSize = 16f
            setTextColor(Color.parseColor(nameColor))
            if (row.status == Status.CURRENT) setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginStart = (8 * density).toInt() }
        })

        if (row.status == Status.FINISHED) {
            container.addView(TextView(requireContext()).apply {
                text = "done"
                textSize = 12f
                setTextColor(Color.parseColor("#4CAF50"))
            })
        } else if (row.status == Status.CURRENT) {
            container.addView(TextView(requireContext()).apply {
                text = "current"
                textSize = 12f
                setTextColor(Color.parseColor("#7C67F5"))
            })
        }

        return container
    }

    private fun selectableBg(): Int {
        val outValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(
            android.R.attr.selectableItemBackground, outValue, true
        )
        return outValue.resourceId
    }
}
