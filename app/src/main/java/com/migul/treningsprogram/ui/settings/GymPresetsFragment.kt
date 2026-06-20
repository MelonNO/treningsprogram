package com.migul.treningsprogram.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.databinding.FragmentGymPresetsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GymPresetsFragment : Fragment() {

    private var _binding: FragmentGymPresetsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GymPresetsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGymPresetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarGymPresets.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.fabAddPreset.setOnClickListener { showEditDialog(null, emptyList(), "") }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.presets.collect { renderPresets(it) }
            }
        }
    }

    private fun renderPresets(presets: List<GymPreset>) {
        binding.layoutPresetsList.removeAllViews()
        if (presets.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "No presets yet. Tap + to add one."
                setTextColor(Color.parseColor("#8888A8"))
                setPadding(0, 32, 0, 0)
                gravity = Gravity.CENTER
            }
            binding.layoutPresetsList.addView(tv)
            return
        }
        presets.forEach { preset ->
            val card = layoutInflater.inflate(R.layout.item_gym_preset, binding.layoutPresetsList, false)
            val isActive = preset.id == viewModel.selectedPresetId
            val equipment = viewModel.getEquipment(preset)

            card.findViewById<TextView>(R.id.tv_preset_name).text = preset.name
            val badge = card.findViewById<TextView>(R.id.tv_preset_active_badge)
            badge.visibility = if (isActive) View.VISIBLE else View.GONE

            card.findViewById<TextView>(R.id.tv_preset_equipment).text =
                if (equipment.isEmpty()) "No equipment (bodyweight only)"
                else equipment.take(4).joinToString("  •  ") +
                    if (equipment.size > 4) "  +${equipment.size - 4} more" else ""

            val tvNotes = card.findViewById<TextView>(R.id.tv_preset_notes)
            if (preset.notes.isNotBlank()) {
                tvNotes.text = "⚠️ ${preset.notes}"
                tvNotes.visibility = View.VISIBLE
            }

            if (isActive) {
                (card as? com.google.android.material.card.MaterialCardView)?.apply {
                    strokeColor = Color.parseColor("#7C67F5")
                    strokeWidth = 6
                }
            }

            card.findViewById<View>(R.id.btn_select_preset).setOnClickListener {
                viewModel.selectPreset(preset.id)
                renderPresets(viewModel.presets.value)
            }
            card.findViewById<View>(R.id.btn_edit_preset).setOnClickListener {
                showEditDialog(preset, equipment, preset.notes)
            }
            card.findViewById<View>(R.id.btn_delete_preset).setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete preset")
                    .setMessage("Delete \"${preset.name}\"?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deletePreset(preset) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            binding.layoutPresetsList.addView(card)
        }
    }

    private fun showEditDialog(existing: GymPreset?, initialEquipment: List<String>, initialNotes: String) {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val equipmentItems = initialEquipment.toMutableList()

        val dialogView = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        fun dp(n: Int) = (n * dp).toInt()

        val etName = TextInputEditText(ctx).apply { setText(existing?.name ?: "") }
        val tilName = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "Preset name"
            addView(etName)
        }
        dialogView.addView(tilName)

        val etNotes = TextInputEditText(ctx).apply { setText(initialNotes) }
        val tilNotes = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "Limitations / notes (optional)"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(12) }
            addView(etNotes)
        }
        dialogView.addView(tilNotes)

        val tvHeader = TextView(ctx).apply {
            text = "Equipment items"
            textSize = 14f
            setTextColor(Color.parseColor("#E2E2EC"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(16); it.bottomMargin = dp(6)
            }
        }
        dialogView.addView(tvHeader)

        val equipLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        dialogView.addView(equipLayout)

        fun rebuildEquipList() {
            equipLayout.removeAllViews()
            equipmentItems.forEachIndexed { idx, item ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(4) }
                }
                val tv = TextView(ctx).apply {
                    text = "• $item"
                    textSize = 13f
                    setTextColor(Color.parseColor("#8888A8"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val btnDel = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "×"
                    textSize = 14f
                    val size = dp(36)
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    setOnClickListener { equipmentItems.removeAt(idx); rebuildEquipList() }
                }
                row.addView(tv); row.addView(btnDel)
                equipLayout.addView(row)
            }
        }
        rebuildEquipList()

        val addRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(10) }
        }
        val etNewItem = TextInputEditText(ctx)
        val tilNewItem = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "Add item"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(etNewItem)
        }
        val btnAdd = MaterialButton(ctx).apply {
            text = "Add"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginStart = dp(8) }
            setOnClickListener {
                val txt = etNewItem.text.toString().trim()
                if (txt.isNotBlank()) { equipmentItems.add(txt); etNewItem.setText(""); rebuildEquipList() }
            }
        }
        addRow.addView(tilNewItem); addRow.addView(btnAdd)
        dialogView.addView(addRow)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existing == null) "New Preset" else "Edit Preset")
            .setView(ScrollView(ctx).apply { addView(dialogView) })
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                val notes = etNotes.text.toString().trim()
                if (existing == null) viewModel.addPreset(name, equipmentItems, notes)
                else viewModel.updatePreset(existing, name, equipmentItems, notes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
