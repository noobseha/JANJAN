package com.gachon.janjan.ui.status

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.gachon.janjan.R
import com.gachon.janjan.databinding.FragmentStatusBinding
import java.text.DecimalFormat

class StatusFragment : Fragment(R.layout.fragment_status) {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StatusViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStatusBinding.bind(view)

        setupObservers()
        setupClickListeners()
        startAutoRefresh()
    }

    private fun setupObservers() {
        // 상단 노란 박스 정보 동기화
        viewModel.userName.observe(viewLifecycleOwner) { name -> binding.tvUserName.text = "${name}님"}
        viewModel.storeInfo.observe(viewLifecycleOwner) { info -> binding.tvStoreAndTable.text = info }
        viewModel.mySojuCount.observe(viewLifecycleOwner) { count -> binding.tvSojuCount.text = "${count}잔" }
        viewModel.myBeerCount.observe(viewLifecycleOwner) { count -> binding.tvBeerCount.text = "${count}잔" }
        viewModel.myExpectedPrice.observe(viewLifecycleOwner) { price ->
            binding.tvTotalPrice.text = "${DecimalFormat("#,###").format(price)}원"
        }
        viewModel.myCardColor.observe(viewLifecycleOwner) { colorHex ->
            binding.cardStatus.setCardBackgroundColor(Color.parseColor(colorHex))
        }

        // 술 먹고 있는 친구 목록 UI
        viewModel.activeFriends.observe(viewLifecycleOwner) { friends ->
            // 1. 다루기 쉽게 화면의 뷰들을 리스트로 묶어버림
            val cards = listOf(binding.cardFriend1, binding.cardFriend2, binding.cardFriend3, binding.cardFriend4)
            val nameViews = listOf(binding.tvName1, binding.tvName2, binding.tvName3, binding.tvName4)
            val infoViews = listOf(binding.tvInfo1, binding.tvInfo2, binding.tvInfo3, binding.tvInfo4)
            val dotViews = listOf(binding.ivDot1, binding.ivDot2, binding.ivDot3, binding.ivDot4)

            // 2. 0부터 3까지 (총 4개 자리) 반복하면서 데이터 채우기
            for (i in 0..3) {
                if (i < friends.size) {
                    // 데이터가 있는 자리: 카드 보여주고 데이터 세팅
                    cards[i].visibility = View.VISIBLE

                    val friend = friends[i]
                    nameViews[i].text = friend.name
                    infoViews[i].text = if (friend.isOnline) {
                        "${friend.storeName} · ${friend.drinkCount}잔"
                    } else {
                        "오프라인 · 마지막 접속 1시간 전"
                    }
                    dotViews[i].visibility = if (friend.isOnline) View.VISIBLE else View.GONE

                } else {
                    // 데이터가 없는 자리: 카드를 화면에서 아예 없앰 (공간도 차지 안 함)
                    cards[i].visibility = View.GONE
                }
            }
        }

        // 🔥 2. 최근 술자리 UI 업데이트 (직전 세션)
        viewModel.recentSession.observe(viewLifecycleOwner) { session ->
            if (session != null) {
                binding.cardRecentSection.visibility = View.VISIBLE
                binding.tvRecentStore.text = session.storeName
                binding.tvRecentDetails.text = "${session.date} · ${session.headCount}명 · ${DecimalFormat("#,###").format(session.totalPrice)}원"
            } else {
                // 직전 술자리 내역이 없으면 통째로 안 보이게 처리
                binding.cardRecentSection.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnActionOrder.setOnClickListener {
            findNavController().popBackStack()
        }

        // 🔥 추가 주문하기를 누르면 다시 주문(Order) 창으로 뒤로가기 처리
        binding.btnActionOrder.setOnClickListener {
            findNavController().popBackStack()
        }

        // 정산하기 버튼 (나중에 settlement 화면 네비게이션 액션 ID 입력하면 됨)
        binding.btnSettlement.setOnClickListener {
            binding.btnSettlement.isEnabled = false
            viewModel.createSettlementFromCurrentSession { settlementId ->
                binding.btnSettlement.isEnabled = true
                if (settlementId != null) {
                    val bundle = Bundle().apply {
                        putString("settlementId", settlementId)
                    }
                    findNavController().navigate(R.id.action_status_to_settlement, bundle)
                } else {
                    android.widget.Toast.makeText(requireContext(), "정산 생성 실패. 다시 시도해주세요.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 최근 술자리 전체보기 버튼
        binding.tvRecentViewAll.setOnClickListener {
            // TODO: 전체 내역 페이지나 다이얼로그 띄우기
        }
    }
    private fun startAutoRefresh() {
        //Status 화면 꺼지면 알아서 루프를 멈춤
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) { // 화면이 살아있는 동안 무한 반복
                val currentUserId = "user_123" // TODO: 실제 유저 ID로 바꾸기
                viewModel.refreshData("session_001", currentUserId)

                delay(5000L) // 5000밀리초(5초) 대기 후 다시 위로 올라가서 실행
            }
        }
    }
        override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}