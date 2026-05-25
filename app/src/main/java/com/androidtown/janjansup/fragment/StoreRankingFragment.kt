package com.androidtown.janjansup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidtown.janjansup.R
import com.androidtown.janjansup.adapter.RankingAdapter
import com.androidtown.janjansup.adapter.StoreAdapter
import com.androidtown.janjansup.api.KakaoPlace
import com.androidtown.janjansup.api.RetrofitClient
import com.androidtown.janjansup.databinding.FragmentStoreRankingBinding
import com.androidtown.janjansup.databinding.BottomSheetStoreBinding
import com.androidtown.janjansup.model.RankingModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StoreRankingFragment : Fragment() {

    private var _binding: FragmentStoreRankingBinding? = null
    private val binding get() = _binding!!
    private lateinit var storeAdapter: StoreAdapter
    private lateinit var rankingAdapter: RankingAdapter
    private var searchJob: Job? = null
    private val db = FirebaseFirestore.getInstance()

    private val KAKAO_API_KEY = "KakaoAK 1b2dfe1f935252a3ab71eab6dda2d0c4"
    private var filter = "total"
    private var selectedStore: KakaoPlace? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreRankingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupSearch()
        setupFilters()

        binding.cardSelectedStore.setOnClickListener {
            selectedStore?.let { showBottomSheet(it) }
        }
    }

    private fun setupAdapters() {
        storeAdapter = StoreAdapter { store ->
            showStoreRanking(store)
        }
        binding.rvStores.adapter = storeAdapter
        binding.rvStores.layoutManager = LinearLayoutManager(requireContext())

        rankingAdapter = RankingAdapter { }
        binding.rvStoreRanking.adapter = rankingAdapter
        binding.rvStoreRanking.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupFilters() {
        updateFilterButtons("total")

        binding.btnAll.setOnClickListener {
            filter = "total"
            updateFilterButtons("total")
            selectedStore?.let { loadStoreRanking(it.id) }
        }
        binding.btnSoju.setOnClickListener {
            filter = "soju"
            updateFilterButtons("soju")
            selectedStore?.let { loadStoreRanking(it.id) }
        }
        binding.btnBeer.setOnClickListener {
            filter = "beer"
            updateFilterButtons("beer")
            selectedStore?.let { loadStoreRanking(it.id) }
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

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            performSearch()
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().trim()
                searchJob?.cancel()
                if (query.length >= 2) {
                    searchJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(300)
                        performSearch()
                    }
                } else if (query.isEmpty()) {
                    storeAdapter.submitList(emptyList())
                    binding.rvStores.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.cardSelectedStore.visibility = View.GONE
                    binding.rvStoreRanking.visibility = View.GONE
                    selectedStore = null
                }
            }
        })
    }

    private fun performSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.kakaoApi.searchPlaces(
                    apiKey = KAKAO_API_KEY,
                    query = query
                )
                if (_binding == null) return@launch

                if (response.documents.isEmpty()) {
                    binding.rvStores.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvStores.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                    binding.cardSelectedStore.visibility = View.GONE
                    binding.rvStoreRanking.visibility = View.GONE
                    storeAdapter.submitList(response.documents)
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Toast.makeText(requireContext(), "검색 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showStoreRanking(store: KakaoPlace) {
        if (_binding == null) return
        selectedStore = store

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)

        binding.rvStores.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.cardSelectedStore.visibility = View.VISIBLE
        binding.rvStoreRanking.visibility = View.VISIBLE

        binding.tvSelectedStoreName.text = store.place_name
        binding.tvSelectedStoreAddress.text = store.address_name

        loadStoreRanking(store.id)
    }

    private fun loadStoreRanking(placeId: String) {
        db.collection("juseop").get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener

                val rankingList = mutableListOf<RankingModel>()

                for (doc in snapshot.documents) {
                    val uid = doc.getString("uid") ?: continue
                    val nickname = doc.getString("nickname") ?: ""
                    val profileImageUrl = doc.getString("profileImageUrl") ?: ""
                    val storeRecords = doc.get("storeRecords") as? List<Map<String, Any>> ?: continue
                    val record = storeRecords.find { it["placeId"] == placeId } ?: continue

                    val soju = (record["soju"] as? Long)?.toDouble() ?: 0.0
                    val beer = (record["beer"] as? Long)?.toDouble() ?: 0.0

                    val count = when (filter) {
                        "soju" -> soju
                        "beer" -> beer
                        else -> soju + beer
                    }

                    rankingList.add(
                        RankingModel(
                            userId = uid,
                            userName = nickname,
                            profileImageUrl = profileImageUrl,
                            totalDrinks = count,
                            sojuCount = soju,
                            beerCount = beer
                        )
                    )
                }

                val sorted = rankingList
                    .sortedByDescending { it.totalDrinks }
                    .mapIndexed { index, model -> model.copy(rank = index + 1) }

                if (_binding == null) return@addOnSuccessListener
                rankingAdapter.submitList(sorted)
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                Toast.makeText(requireContext(), "랭킹 로드 실패", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showBottomSheet(store: KakaoPlace) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetStoreBinding.inflate(layoutInflater)

        sheetBinding.tvStoreName.text = store.place_name
        sheetBinding.tvStoreCategory.text = store.category_name
        sheetBinding.tvStoreAddress.text = store.road_address_name.ifEmpty { store.address_name }
        sheetBinding.tvStorePhone.text = store.phone.ifEmpty { "정보 없음" }

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}