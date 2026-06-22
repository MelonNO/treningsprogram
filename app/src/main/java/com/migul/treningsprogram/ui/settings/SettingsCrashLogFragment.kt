package com.migul.treningsprogram.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.migul.treningsprogram.data.CrashLog
import com.migul.treningsprogram.databinding.FragmentSettingsCrashLogBinding
import com.migul.treningsprogram.databinding.ItemCrashEntryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SettingsCrashLogFragment : Fragment() {

    private var _binding: FragmentSettingsCrashLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    private lateinit var adapter: CrashAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsCrashLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CrashAdapter { entry ->
            val text = formatEntry(entry, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()))
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("crash_report", text))
            Snackbar.make(binding.root, "Crash report copied", Snackbar.LENGTH_SHORT).show()
        }
        binding.rvCrashLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCrashLog.adapter = adapter

        binding.btnClearCrashLog.setOnClickListener { viewModel.clearCrashLog() }

        binding.btnShareCrashLog.setOnClickListener {
            val entries = viewModel.crashLogEntries.value
            if (entries.isEmpty()) {
                Snackbar.make(binding.root, "No crash reports to share", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val text = entries.joinToString("\n\n---\n\n") { formatEntry(it, fmt) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Crash Reports — treningsprogram")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, "Share crash reports"))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refreshCrashLog()
                viewModel.crashLogEntries.collect { entries ->
                    if (entries.isEmpty()) {
                        binding.rvCrashLog.visibility = View.GONE
                        binding.tvCrashEmpty.visibility = View.VISIBLE
                    } else {
                        binding.rvCrashLog.visibility = View.VISIBLE
                        binding.tvCrashEmpty.visibility = View.GONE
                        adapter.submitList(entries)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatEntry(entry: CrashLog.Entry, fmt: SimpleDateFormat): String =
        "${entry.exceptionClass} @ ${fmt.format(Date(entry.timestampMs))}\nThread: ${entry.thread}\n${entry.message}\n${entry.stackTrace}"
}

private class CrashAdapter(
    private val onCopy: (CrashLog.Entry) -> Unit
) : ListAdapter<CrashLog.Entry, CrashAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CrashLog.Entry>() {
            override fun areItemsTheSame(a: CrashLog.Entry, b: CrashLog.Entry) = a.timestampMs == b.timestampMs
            override fun areContentsTheSame(a: CrashLog.Entry, b: CrashLog.Entry) = a == b
        }
        private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    inner class VH(val binding: ItemCrashEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        var expanded = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCrashEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        holder.binding.tvCrashException.text = entry.exceptionClass.substringAfterLast('.')
        holder.binding.tvCrashTime.text = fmt.format(Date(entry.timestampMs))
        holder.binding.tvCrashThread.text = "Thread: ${entry.thread}"
        holder.binding.tvCrashMessage.text = entry.message.take(200)
        holder.binding.tvCrashStacktrace.text = entry.stackTrace
        holder.expanded = false
        holder.binding.tvCrashStacktrace.visibility = View.GONE
        holder.binding.tvExpandToggle.text = "Show stack trace ▾"

        holder.binding.btnCopyCrash.setOnClickListener { onCopy(entry) }

        holder.binding.tvExpandToggle.setOnClickListener {
            holder.expanded = !holder.expanded
            holder.binding.tvCrashStacktrace.visibility = if (holder.expanded) View.VISIBLE else View.GONE
            holder.binding.tvExpandToggle.text = if (holder.expanded) "Hide stack trace ▴" else "Show stack trace ▾"
        }
    }
}
