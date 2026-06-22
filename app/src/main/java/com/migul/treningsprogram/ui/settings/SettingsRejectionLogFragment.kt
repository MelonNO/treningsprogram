package com.migul.treningsprogram.ui.settings

import android.graphics.Typeface
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
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.RejectionLog
import com.migul.treningsprogram.databinding.FragmentSettingsRejectionLogBinding
import com.migul.treningsprogram.databinding.ItemRejectionSessionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SettingsRejectionLogFragment : Fragment() {

    private var _binding: FragmentSettingsRejectionLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsRejectionLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = RejectionSessionAdapter(fmt)
        binding.rvRejectionLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRejectionLog.adapter = adapter

        binding.btnClearRejectionLog.setOnClickListener {
            viewModel.clearRejectionLog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rejectionLogSessions.collect { sessions ->
                    adapter.submitList(sessions)
                    binding.tvRejectionLogEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvRejectionLog.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        viewModel.refreshRejectionLog()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class RejectionSessionAdapter(private val fmt: SimpleDateFormat) :
        ListAdapter<RejectionLog.Session, RejectionSessionAdapter.VH>(object : DiffUtil.ItemCallback<RejectionLog.Session>() {
            override fun areItemsTheSame(a: RejectionLog.Session, b: RejectionLog.Session) = a.timestampMs == b.timestampMs
            override fun areContentsTheSame(a: RejectionLog.Session, b: RejectionLog.Session) = a == b
        }) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemRejectionSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemRejectionSessionBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(session: RejectionLog.Session) {
                val ctx = b.root.context
                val dp = ctx.resources.displayMetrics.density

                if (session.succeeded) {
                    b.tvSessionStatus.text = "ACCEPTED after ${session.attempts.size + 1} attempt${if (session.attempts.size != 0) "s" else ""}"
                    b.tvSessionStatus.setTextColor(ctx.getColor(android.R.color.holo_green_dark))
                } else {
                    b.tvSessionStatus.text = "FAILED — all attempts rejected"
                    b.tvSessionStatus.setTextColor(ctx.getColor(com.google.android.material.R.color.design_default_color_error))
                }
                b.tvSessionTimestamp.text = fmt.format(Date(session.timestampMs))

                b.layoutAttempts.removeAllViews()
                val errorColor = ctx.getColor(com.google.android.material.R.color.design_default_color_error)
                val warnColor = 0xFFFFB347.toInt()

                session.attempts.forEach { attempt ->
                    val accent = if (attempt.finalFailure) errorColor else warnColor
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        val vp = (8 * dp).toInt()
                        setPadding(0, vp, 0, vp)
                    }
                    val bar = View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams((3 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT).also {
                            it.marginEnd = (10 * dp).toInt()
                        }
                        setBackgroundColor(accent)
                    }
                    val col = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val label = TextView(ctx).apply {
                        text = "Attempt ${attempt.attempt}${if (attempt.finalFailure) " — FAILED" else " — rejected"}"
                        setTextColor(accent)
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                    }
                    val body = TextView(ctx).apply {
                        text = attempt.reason
                        setTextColor(ctx.getColor(R.color.on_surface_variant))
                        textSize = 12f
                    }
                    col.addView(label)
                    col.addView(body)
                    row.addView(bar)
                    row.addView(col)
                    b.layoutAttempts.addView(row)
                }
            }
        }
    }
}
