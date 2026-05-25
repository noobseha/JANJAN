package com.androidtown.janjansup.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.androidtown.janjansup.R
import com.androidtown.janjansup.adapter.RankingAdapter
import com.androidtown.janjansup.databinding.FragmentTimeRankingBinding
import com.androidtown.janjansup.model.RankingModel
import com.google.firebase.firestore.FirebaseFirestore

class TimeRankingFragment : Fragment(R.layout.fragment_time_ranking) {

    private var _binding: FragmentTimeRankingBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: RankingAdapter
    private val db = FirebaseFirestore.getInstance()

    private var period: String = "daily"
    private var filter: String = "total"

    private val myUid = "user015"
    private var myRankModel: RankingModel? = null

    companion object {
        fun newInstance(period: String): TimeRankingFragment {
            return TimeRankingFragment().apply {
                arguments = Bundle().apply {
                    putString("period", period)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTimeRankingBinding.bind(view)

        period = arguments?.getString("period") ?: "daily"

        adapter = RankingAdapter { _ -> }

        binding.rvRanking.adapter = adapter
        binding.rvRanking.layoutManager = LinearLayoutManager(requireContext())

        setupFilterButtons()

        binding.rvRanking.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateMyCardVisibility()
            }
        })

        loadRanking()
    }

    private fun setupFilterButtons() {
        updateFilterButtons("total")

        binding.btnAll.setOnClickListener {
            filter = "total"
            updateFilterButtons("total")
            loadRanking()
        }
        binding.btnSoju.setOnClickListener {
            filter = "soju"
            updateFilterButtons("soju")
            loadRanking()
        }
        binding.btnBeer.setOnClickListener {
            filter = "beer"
            updateFilterButtons("beer")
            loadRanking()
        }
    }

    private fun updateFilterButtons(selected: String) {
        binding.btnAll.setBackgroundResource(
            if (selected == "total") R.drawable.btn_filter_selected else R.drawable.btn_filter_unselected
        )
        binding.btnSoju.setBackgroundResource(
            if (selected == "soju") R.drawable.btn_filter_selected else R.drawable.btn_filter_unselected
        )
        binding.btnBeer.setBackgroundResource(
            if (selected == "beer") R.drawable.btn_filter_selected else R.drawable.btn_filter_unselected
        )
    }

    private fun loadRanking() {
        db.collection("juseop").get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener

                val list = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("uid") ?: return@mapNotNull null
                    val nickname = doc.getString("nickname") ?: ""
                    val profileImageUrl = doc.getString("profileImageUrl") ?: ""

                    val soju = when (period) {
                        "daily" -> (doc.getLong("dailySoju") ?: 0L).toDouble()
                        "weekly" -> (doc.getLong("weeklySoju") ?: 0L).toDouble()
                        "monthly" -> (doc.getLong("monthlySoju") ?: 0L).toDouble()
                        else -> 0.0
                    }
                    val beer = when (period) {
                        "daily" -> (doc.getLong("dailyBeer") ?: 0L).toDouble()
                        "weekly" -> (doc.getLong("weeklyBeer") ?: 0L).toDouble()
                        "monthly" -> (doc.getLong("monthlyBeer") ?: 0L).toDouble()
                        else -> 0.0
                    }

                    val count = when (filter) {
                        "soju" -> soju
                        "beer" -> beer
                        else -> soju + beer
                    }

                    RankingModel(
                        userId = uid,
                        userName = nickname,
                        profileImageUrl = profileImageUrl,
                        totalDrinks = count,
                        sojuCount = soju,
                        beerCount = beer
                    )
                }
                    .sortedByDescending { it.totalDrinks }
                    .mapIndexed { index, model -> model.copy(rank = index + 1) }

                if (_binding == null) return@addOnSuccessListener

                myRankModel = list.find { it.userId == myUid }

                if (list.size >= 1) bindRank1(list[0])
                if (list.size >= 2) bindRank2(list[1])
                if (list.size >= 3) bindRank3(list[2])

                myRankModel?.let { bindMyCard(it) }

                adapter.submitList(list.drop(3)) {
                    binding.rvRanking.post {
                        if (_binding != null) updateMyCardVisibility()
                    }
                }
            }
    }

    private fun bindRank1(item: RankingModel) {
        if (_binding == null) return
        binding.tvRank1Name.text = item.userName
        binding.tvRank1Cups.text = "${item.totalDrinks.toInt()}잔"
        binding.tvRank1Initial.text = item.userName.firstOrNull()?.toString() ?: "?"
    }

    private fun bindRank2(item: RankingModel) {
        if (_binding == null) return
        binding.tvRank2Name.text = item.userName
        binding.tvRank2Cups.text = "${item.totalDrinks.toInt()}잔"
        binding.tvRank2Initial.text = item.userName.firstOrNull()?.toString() ?: "?"
    }

    private fun bindRank3(item: RankingModel) {
        if (_binding == null) return
        binding.tvRank3Name.text = item.userName
        binding.tvRank3Cups.text = "${item.totalDrinks.toInt()}잔"
        binding.tvRank3Initial.text = item.userName.firstOrNull()?.toString() ?: "?"
    }

    private fun bindMyCard(item: RankingModel) {
        if (_binding == null) return
        val label = "#${item.rank}"
        val name = "${item.userName} (나)"
        val cups = "${item.totalDrinks.toInt()}잔"
        val initial = item.userName.firstOrNull()?.toString() ?: "?"

        binding.tvMyRankTopLabel.text = label
        binding.tvMyRankTopName.text = name
        binding.tvMyRankTopCups.text = cups
        binding.tvMyRankTopInitial.text = initial

        binding.tvMyRankBottomLabel.text = label
        binding.tvMyRankBottomName.text = name
        binding.tvMyRankBottomCups.text = cups
        binding.tvMyRankBottomInitial.text = initial
    }

    private fun updateMyCardVisibility() {
        if (_binding == null) return
        val myRank = myRankModel?.rank ?: return

        if (myRank <= 3) {
            binding.cardMyRankTop.visibility = View.GONE
            binding.cardMyRankBottom.visibility = View.GONE
            return
        }

        val layoutManager = binding.rvRanking.layoutManager as LinearLayoutManager
        val myPositionInList = myRank - 4

        if (layoutManager.findFirstVisibleItemPosition() == RecyclerView.NO_POSITION) {
            binding.cardMyRankTop.visibility = View.GONE
            binding.cardMyRankBottom.visibility = View.VISIBLE
            return
        }

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        when {
            myPositionInList in firstVisible..lastVisible -> {
                binding.cardMyRankTop.visibility = View.GONE
                binding.cardMyRankBottom.visibility = View.GONE
            }
            myPositionInList < firstVisible -> {
                binding.cardMyRankTop.visibility = View.VISIBLE
                binding.cardMyRankBottom.visibility = View.GONE
            }
            else -> {
                binding.cardMyRankTop.visibility = View.GONE
                binding.cardMyRankBottom.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}