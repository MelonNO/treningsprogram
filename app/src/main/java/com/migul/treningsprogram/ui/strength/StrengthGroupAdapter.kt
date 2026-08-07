package com.migul.treningsprogram.ui.strength

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.migul.treningsprogram.R
import com.migul.treningsprogram.databinding.ItemStrengthGroupBinding
import com.migul.treningsprogram.domain.strength.GroupRating

/**
 * Brief 02 (2026-08-07) — the six muscle-group rows.
 *
 * All six are always submitted, in `StrengthStandards.RATED_GROUPS` order, rated or not. An
 * unrated group is a visible row carrying the sentence that would unlock it — never a hidden row
 * and never a guessed tier. The wording itself all comes from [StrengthCopy].
 */
class StrengthGroupAdapter : ListAdapter<GroupRating, StrengthGroupAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemStrengthGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemStrengthGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GroupRating) {
            val ctx = binding.root.context
            binding.tvStrengthGroupName.text = item.group
            binding.tvStrengthGroupTier.text = StrengthCopy.groupTierName(item)

            if (item.isRated) {
                binding.tvStrengthGroupTier.alpha = 1f
                binding.progressStrengthGroup.visibility = View.VISIBLE
                binding.progressStrengthGroup.setProgressCompat(
                    StrengthCopy.tierProgressPercent(item.score), false
                )
                val best = StrengthCopy.bestSetLine(item)
                binding.tvStrengthGroupBest.visibility =
                    if (best == null) View.GONE else View.VISIBLE
                binding.tvStrengthGroupBest.text = best.orEmpty()
                binding.tvStrengthGroupHint.text = StrengthCopy.nextStepLine(item)
                binding.tvStrengthGroupHint.setTextColor(
                    ContextCompat.getColor(ctx, R.color.auros_fog)
                )
            } else {
                // Unrated: dim the "Not rated" pill, drop the bar and the best-set line, and put
                // the unlock sentence in the amber the app already uses for "this needs you".
                binding.tvStrengthGroupTier.alpha = 0.7f
                binding.progressStrengthGroup.visibility = View.GONE
                binding.tvStrengthGroupBest.visibility = View.GONE
                binding.tvStrengthGroupHint.text =
                    StrengthCopy.unratedLine(item.group, item.unratedReason)
                binding.tvStrengthGroupHint.setTextColor(
                    ContextCompat.getColor(ctx, R.color.game_amber)
                )
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<GroupRating>() {
            override fun areItemsTheSame(a: GroupRating, b: GroupRating) = a.group == b.group
            override fun areContentsTheSame(a: GroupRating, b: GroupRating) = a == b
        }
    }
}
