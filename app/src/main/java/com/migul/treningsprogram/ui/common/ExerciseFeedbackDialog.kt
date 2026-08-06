package com.migul.treningsprogram.ui.common

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.migul.treningsprogram.data.db.entity.ExerciseFeedback
import com.migul.treningsprogram.domain.ExerciseFeedbackCatalog

/**
 * Item 05 (2026-08-06) — the "leave feedback on this exercise" dialog.
 *
 * Built programmatically like the other set-entry dialogs on this screen (no layout file), and
 * deliberately tiny: **one tap on a reason is a complete submission**, the free-text box is
 * genuinely optional, and Save is enabled the moment a reason is chosen. That is the acceptance
 * criterion "leaving feedback takes a single reason tap" and the constraint that giving feedback
 * must never slow down logging a set.
 *
 * Reached from the exercise info sheet, which opens from BOTH required surfaces — the workout
 * logging screen (tap the exercise name) and the Program tab (tap the exercise card) — so no new
 * exercise-level menu had to be invented on the logging screen (assumption A3).
 *
 * The dialog is a dumb view: it never touches the database. Callers pass the [existing] entry and
 * receive [onSubmit] / [onRemove], so the same dialog serves the info sheet and the Settings
 * review-and-undo screen.
 */
object ExerciseFeedbackDialog {

    /**
     * @param existing the feedback already stored for this exercise, if any — its reason is
     *   preselected and its note prefilled, and a "Remove feedback" button appears (improvement D:
     *   the user must always be able to take it back).
     */
    fun show(
        context: Context,
        exerciseName: String,
        existing: ExerciseFeedback?,
        onSubmit: (reasonKey: String, note: String) -> Unit,
        onRemove: () -> Unit = {},
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val group = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            ExerciseFeedbackCatalog.REASONS.forEachIndexed { index, reason ->
                addView(RadioButton(context).apply {
                    id = index + 1
                    text = reason.label
                    textSize = 15f
                    tag = reason.key
                    isChecked = reason.key == existing?.reasonKey
                })
            }
        }

        val note = TextInputEditText(context).apply {
            setText(existing?.note.orEmpty())
            hint = "Optional — say more, or suggest a change"
            setSingleLine(false)
            minLines = 2
            setSelection(text?.length ?: 0)
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(context).apply {
                text = "This is a hint for the AI, not a ban — $exerciseName can still appear."
                textSize = 13f
                setTextColor(0xFF888888.toInt())
                setPadding(0, 0, 0, dp(8))
            })
            addView(group)
            addView(note, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8) })
        }

        val scroller = ScrollView(context).apply { addView(content) }

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(exerciseName)
            .setView(scroller)
            .setPositiveButton("Save") { _, _ ->
                val checked = group.findViewById<RadioButton>(group.checkedRadioButtonId)
                val key = checked?.tag as? String
                if (key != null) onSubmit(key, note.text?.toString().orEmpty())
            }
            .setNegativeButton("Cancel", null)

        if (existing != null) builder.setNeutralButton("Remove") { _, _ -> onRemove() }

        builder.show()
    }
}
