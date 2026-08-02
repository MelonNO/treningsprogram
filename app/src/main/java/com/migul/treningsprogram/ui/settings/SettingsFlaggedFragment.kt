package com.migul.treningsprogram.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.data.ExerciseCatalog
import com.migul.treningsprogram.data.ExerciseInfoCorrections
import com.migul.treningsprogram.databinding.FragmentSettingsFlaggedBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * QoL 2026-08 item 04 — the debug list of flagged database mismatches.
 *
 * Per entry: **Unflag** (remove the report), **Re-match** (search the exercise database by name,
 * pick the correct entry — takes real effect app-wide via [ExerciseInfoCorrections] overrides and
 * auto-clears the flag). **Copy All** puts the whole list on the clipboard as plain text, one
 * `name -> matched-entry` per line (D4).
 */
@AndroidEntryPoint
class SettingsFlaggedFragment : Fragment() {

    private var _binding: FragmentSettingsFlaggedBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var corrections: ExerciseInfoCorrections

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsFlaggedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCopyAllFlagged.setOnClickListener {
            val text = corrections.copyText()
            if (text.isBlank()) {
                Snackbar.make(binding.root, "Nothing to copy", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("flagged_exercises", text))
            Snackbar.make(binding.root, "Copied flagged list", Snackbar.LENGTH_SHORT).show()
        }

        refresh()
    }

    private fun refresh() {
        val flags = corrections.flags()
        binding.tvFlaggedEmpty.visibility = if (flags.isEmpty()) View.VISIBLE else View.GONE
        binding.cardFlagged.visibility = if (flags.isEmpty()) View.GONE else View.VISIBLE
        binding.layoutFlaggedList.removeAllViews()
        flags.forEach { binding.layoutFlaggedList.addView(flagRow(it)) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun flagRow(flag: ExerciseInfoCorrections.Flag): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        row.addView(TextView(requireContext()).apply {
            text = flag.exerciseName
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        val matchedLabel = ExerciseCatalog.getDbEntry(flag.matchedDbId)?.name ?: flag.matchedDbId
        row.addView(TextView(requireContext()).apply {
            text = "Matched to: $matchedLabel"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
        })
        val actions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.addView(textButton("Re-match") { showRematchDialog(flag) })
        actions.addView(textButton("Unflag") {
            corrections.removeFlag(flag.exerciseName)
            Snackbar.make(binding.root, "Unflagged ${flag.exerciseName}", Snackbar.LENGTH_SHORT).show()
            refresh()
        })
        row.addView(actions)
        return row
    }

    private fun textButton(label: String, onClick: () -> Unit) =
        MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = label
            textSize = 13f
            setOnClickListener { onClick() }
        }

    /**
     * D4: the search UI — a text field filtering the exercise database by name; tapping a result
     * applies the re-match (override wins app-wide) and auto-clears the flag.
     */
    private fun showRematchDialog(flag: ExerciseInfoCorrections.Flag) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val input = EditText(requireContext()).apply {
            hint = "Search the exercise database…"
            setText(flag.exerciseName)
        }
        container.addView(input)
        val results = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        container.addView(results)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Re-match \"${flag.exerciseName}\"")
            .setView(android.widget.ScrollView(requireContext()).apply { addView(container) })
            .setNegativeButton("Cancel", null)
            .create()

        fun render(query: String) {
            results.removeAllViews()
            val q = query.trim().lowercase()
            val matches = ExerciseCatalog.entries
                .filter { q.isBlank() || it.name.lowercase().contains(q) }
                .sortedBy { it.name }
                .take(25)
            if (matches.isEmpty()) {
                results.addView(TextView(requireContext()).apply {
                    text = "No entries match \"$query\""
                    textSize = 13f
                    setTextColor(0xFF888888.toInt())
                    setPadding(0, dp(8), 0, dp(8))
                })
                return
            }
            matches.forEach { entry ->
                results.addView(TextView(requireContext()).apply {
                    text = entry.name
                    textSize = 15f
                    setPadding(0, dp(10), 0, dp(10))
                    setOnClickListener {
                        corrections.setOverride(flag.exerciseName, entry.id)
                        dialog.dismiss()
                        Snackbar.make(
                            binding.root,
                            "${flag.exerciseName} now shows \"${entry.name}\"",
                            Snackbar.LENGTH_LONG
                        ).show()
                        refresh()
                    }
                })
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { render(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        render(flag.exerciseName)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
