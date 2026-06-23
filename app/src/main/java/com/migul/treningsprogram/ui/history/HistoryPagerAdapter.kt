package com.migul.treningsprogram.ui.history

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class HistoryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 4
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> HistoryRecapFragment()
        1 -> HistoryStatsFragment()
        2 -> HistoryProgressFragment()
        else -> HistoryLogFragment()
    }
}
