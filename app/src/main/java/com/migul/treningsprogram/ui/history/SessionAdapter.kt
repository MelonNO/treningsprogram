package com.migul.treningsprogram.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.*

class SessionAdapter(
    private val onClick: (WorkoutSession) -> Unit
) : ListAdapter<WorkoutSession, SessionAdapter.SessionViewHolder>(DIFF) {

    private val fmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SessionViewHolder(private val binding: ItemSessionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(session: WorkoutSession) {
            binding.tvDate.text = fmt.format(Date(com.migul.treningsprogram.domain.DayBoundary.toLogicalMillis(session.dateMs)))
            binding.tvDuration.text = if (session.durationMinutes > 0) "${session.durationMinutes} min" else "—"
            binding.root.setOnClickListener { onClick(session) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WorkoutSession>() {
            override fun areItemsTheSame(a: WorkoutSession, b: WorkoutSession) = a.id == b.id
            override fun areContentsTheSame(a: WorkoutSession, b: WorkoutSession) = a == b
        }
    }
}
