package com.migul.treningsprogram.ui.history

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Body-progress batch 2026-08-04 (brief 02) adds "Body" as a fifth tab, placed AFTER Progress
 * because the two are the screen's progress-over-time pair. Recap stays at index 0 —
 * [HistoryFragment.RECAP_TAB] and every deep link into it depend on that.
 */
class HistoryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 5
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> HistoryRecapFragment()
        1 -> HistoryStatsFragment()
        2 -> HistoryProgressFragment()
        3 -> BodyProgressFragment()
        else -> HistoryLogFragment()
    }
}
