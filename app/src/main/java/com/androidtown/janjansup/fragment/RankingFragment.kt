package com.androidtown.janjansup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.androidtown.janjansup.adapter.RankingPagerAdapter
import com.androidtown.janjansup.databinding.FragmentRankingBinding
import com.google.android.material.tabs.TabLayoutMediator

class RankingFragment : Fragment() {

    // ViewBinding 사용 (메모리 누수 방지를 위해 nullable로 선언)
    private var _binding: FragmentRankingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRankingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
    }

    private fun setupViewPager() {
        // ViewPager2 어댑터 연결
        val pagerAdapter = RankingPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        // TabLayout과 ViewPager2 연동
        val tabTitles = listOf("일간", "주간", "월간", "가게별", "친구")

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 프래그먼트 뷰가 소멸될 때 바인딩 해제
        _binding = null
    }
}