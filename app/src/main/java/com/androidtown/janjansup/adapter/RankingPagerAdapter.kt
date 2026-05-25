package com.androidtown.janjansup.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.androidtown.janjansup.fragment.FriendRankingFragment
import com.androidtown.janjansup.fragment.StoreRankingFragment
import com.androidtown.janjansup.fragment.TimeRankingFragment

class RankingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TimeRankingFragment.newInstance("daily")
            1 -> TimeRankingFragment.newInstance("weekly")
            2 -> TimeRankingFragment.newInstance("monthly")
            3 -> StoreRankingFragment()
            4 -> FriendRankingFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}