package com.migul.treningsprogram.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.databinding.FragmentSettingsUnrecognizedBinding
import com.migul.treningsprogram.databinding.ItemUnrecognizedExerciseBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsUnrecognizedFragment : Fragment() {

    private var _binding: FragmentSettingsUnrecognizedBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    private lateinit var adapter: UnrecognizedAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsUnrecognizedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = UnrecognizedAdapter { name ->
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("exercise_name", name))
            Snackbar.make(binding.root, "Copied: $name", Snackbar.LENGTH_SHORT).show()
        }
        binding.rvUnrecognized.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUnrecognized.adapter = adapter

        binding.btnCopyAllUnrecognized.setOnClickListener {
            val names = viewModel.unrecognizedExercises.value
            if (names.isEmpty()) {
                Snackbar.make(binding.root, "No exercises to copy", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("unrecognized_exercises", names.joinToString("\n")))
            Snackbar.make(binding.root, "Copied ${names.size} exercises", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnClearUnrecognized.setOnClickListener {
            viewModel.clearUnrecognized()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refreshUnrecognized()
                viewModel.unrecognizedExercises.collect { names ->
                    if (names.isEmpty()) {
                        binding.cardUnrecognized.visibility = View.GONE
                        binding.tvUnrecognizedEmpty.visibility = View.VISIBLE
                    } else {
                        binding.cardUnrecognized.visibility = View.VISIBLE
                        binding.tvUnrecognizedEmpty.visibility = View.GONE
                        adapter.submitList(names)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class UnrecognizedAdapter(
    private val onCopy: (String) -> Unit
) : ListAdapter<String, UnrecognizedAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(a: String, b: String) = a == b
            override fun areContentsTheSame(a: String, b: String) = a == b
        }
    }

    inner class VH(val binding: ItemUnrecognizedExerciseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemUnrecognizedExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val name = getItem(position)
        holder.binding.tvExerciseName.text = name
        holder.binding.btnCopy.setOnClickListener { onCopy(name) }
    }
}
