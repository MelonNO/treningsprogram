package com.migul.treningsprogram.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.migul.treningsprogram.data.db.entity.XpEvent
import com.migul.treningsprogram.databinding.ItemXpEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * U2: renders the XP log, newest first. Each row shows what earned the XP ([XpEvent.reason]), the
 * amount as "+N XP", and the formatted date/time it was earned.
 */
class XpLogAdapter : ListAdapter<XpEvent, XpLogAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemXpEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemXpEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

        fun bind(item: XpEvent) {
            binding.tvXpReason.text = item.reason
            binding.tvXpAmount.text = "+${item.amount} XP"
            binding.tvXpDate.text = dateFmt.format(Date(item.timestampMs))
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<XpEvent>() {
            override fun areItemsTheSame(a: XpEvent, b: XpEvent) = a.id == b.id
            override fun areContentsTheSame(a: XpEvent, b: XpEvent) = a == b
        }
    }
}
