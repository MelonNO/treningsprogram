package com.migul.treningsprogram.ui.summary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.migul.treningsprogram.data.db.entity.WeeklySummary
import com.migul.treningsprogram.databinding.ItemWeeklySummaryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * B1: renders the scrollable history of weekly coach summaries, newest first. Each card shows the
 * week label and the plain-language summary text.
 */
class WeeklySummaryAdapter :
    ListAdapter<WeeklySummary, WeeklySummaryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemWeeklySummaryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemWeeklySummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

        fun bind(item: WeeklySummary) {
            // Header: ISO week key (e.g. "2026-W26") + the generation date for human context.
            binding.tvWeek.text = "Week of ${item.weekKey}"
            binding.tvDate.text = dateFmt.format(Date(item.createdAtMs))
            binding.tvSummaryText.text = item.summaryText
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WeeklySummary>() {
            override fun areItemsTheSame(a: WeeklySummary, b: WeeklySummary) = a.id == b.id
            override fun areContentsTheSame(a: WeeklySummary, b: WeeklySummary) = a == b
        }
    }
}
