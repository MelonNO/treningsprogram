package com.migul.treningsprogram.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.PromptLog
import com.migul.treningsprogram.databinding.FragmentSettingsPromptLogBinding
import com.migul.treningsprogram.databinding.ItemPromptLogBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SettingsPromptLogFragment : Fragment() {

    private var _binding: FragmentSettingsPromptLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsPromptLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PromptLogAdapter(fmt)
        binding.rvPromptLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPromptLog.adapter = adapter

        binding.btnClearPromptLog.setOnClickListener {
            viewModel.clearPromptLog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.promptLogEntries.collect { entries ->
                    adapter.submitList(entries)
                    binding.tvPromptLogEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvPromptLog.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        viewModel.refreshPromptLog()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class PromptLogAdapter(private val fmt: SimpleDateFormat) :
        ListAdapter<PromptLog.Entry, PromptLogAdapter.VH>(object : DiffUtil.ItemCallback<PromptLog.Entry>() {
            override fun areItemsTheSame(a: PromptLog.Entry, b: PromptLog.Entry) = a.timestampMs == b.timestampMs
            override fun areContentsTheSame(a: PromptLog.Entry, b: PromptLog.Entry) = a == b
        }) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPromptLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemPromptLogBinding) : RecyclerView.ViewHolder(b.root) {
            private var expanded = false

            fun bind(entry: PromptLog.Entry) {
                expanded = false
                b.layoutExpanded.visibility = View.GONE
                b.btnExpand.text = "Show"

                b.tvLogType.text = entry.type.replace("_", " ").uppercase()
                b.tvLogTimestamp.text = fmt.format(Date(entry.timestampMs))
                b.tvPromptPreview.text = entry.prompt.take(200).replace("\n", " ")
                b.tvFullPrompt.text = entry.prompt
                b.tvFullResponse.text = entry.response

                b.btnExpand.setOnClickListener {
                    expanded = !expanded
                    b.layoutExpanded.visibility = if (expanded) View.VISIBLE else View.GONE
                    b.btnExpand.text = if (expanded) "Hide" else "Show"
                }

                b.btnCopyPrompt.setOnClickListener {
                    val context = b.root.context
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("prompt", entry.prompt))
                    Snackbar.make(b.root, "Prompt copied", Snackbar.LENGTH_SHORT).show()
                }

                b.btnCopyResponse.setOnClickListener {
                    val context = b.root.context
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("response", entry.response))
                    Snackbar.make(b.root, "Response copied", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
}
