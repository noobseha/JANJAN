package com.gachon.janjan.ui.settlement

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gachon.janjan.R
import com.gachon.janjan.data.model.Settlement
import com.gachon.janjan.data.repository.PaymentRepository
import com.gachon.janjan.databinding.FragmentSettlementBinding
import com.gachon.janjan.domain.session.viewmodel.SessionViewModel
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class SettlementFragment : Fragment(R.layout.fragment_settlement) {

    private var _binding: FragmentSettlementBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SettlementViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by activityViewModels()
    private var settlementId: String? = null
    private val currentUserId: String
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettlementBinding.bind(view)

        settlementId = arguments?.getString("settlementId")
        if (settlementId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "정산 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        setupClickListeners()
        setupObservers()

        // 실시간 Firestore 정산 문서 구독 시작
        viewModel.startObservingSettlement(settlementId!!)
    }

    private fun setupClickListeners() {
        // 상단 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 하단 정산 완료 버튼 클릭 시
        binding.btnSettlement.setOnClickListener {
            val settlement = viewModel.settlementData.value ?: return@setOnClickListener
            val me = settlement.participants.find { it.userId == currentUserId } ?: return@setOnClickListener
            val priceText = binding.tvMyPrice.text.toString()
            val myPrice = priceText.replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0

            val dialog = PaymentMethod(price = myPrice, storeName = settlement.storeName) { selectedPayMethod ->
                if (selectedPayMethod in listOf("토스페이", "네이버페이", "카카오페이", "직접 결제", "카드 결제")) {
                    val isDirectPayment = selectedPayMethod == PaymentRepository.DIRECT_PAYMENT_METHOD
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("결제 완료!")
                        .setMessage(
                            if (isDirectPayment) {
                                "${myPrice}원이 직접 결제로 요청되었습니다.\n사장님의 승인을 기다리고 있습니다."
                            } else {
                                "${myPrice}원이 결제되었습니다.\n내 결제 상태를 저장합니다."
                            }
                        )
                        .setPositiveButton("확인") { _, _ ->
                            sessionViewModel.completeSettlement(settlement.sessionId, selectedPayMethod) { success ->
                                if (success) {
                                    findNavController().popBackStack(R.id.sessionHomeFragment, false)
                                } else {
                                    Toast.makeText(requireContext(), "결제 상태 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .show()
                }
            }
            dialog.show(parentFragmentManager, "PaymentMethodDialog")
        }
    }

    private fun setupObservers() {
        viewModel.settlementData.observe(viewLifecycleOwner) { settlement ->
            if (settlement != null) {
                updateUI(settlement)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sessionViewModel.externalPaymentCompleteEvent.collect { method ->
                val settlement = viewModel.settlementData.value ?: return@collect
                val me = settlement.participants.find { it.userId == currentUserId } ?: return@collect
                val isDirectPayment = method == PaymentRepository.DIRECT_PAYMENT_METHOD
                
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("결제 완료!")
                    .setMessage(
                        if (isDirectPayment) {
                            "${me.mytotal}원이 직접 결제로 요청되었습니다.\n사장님의 승인을 기다리고 있습니다."
                        } else {
                            "${me.mytotal}원이 결제되었습니다.\n내 결제 상태를 저장합니다."
                        }
                    )
                    .setPositiveButton("확인") { _, _ ->
                        sessionViewModel.completeSettlement(settlement.sessionId, method) { success ->
                            if (success) {
                                findNavController().popBackStack(R.id.sessionHomeFragment, false)
                            } else {
                                Toast.makeText(requireContext(), "결제 상태 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun updateUI(settlement: Settlement) {
        val decFormat = DecimalFormat("#,###")

        // 1. 상단 기본 텍스트 정보 매핑
        binding.tvStoreAndTable.text = "${settlement.storeName} · 테이블 ${settlement.tableId}"
        binding.tvTotalAmount.text = "${decFormat.format(settlement.totalPrice)}원"
        binding.tvTimeInfo.text = settlement.timeInfo

        // 2. 나의 정산 요약 카드 업데이트
        val me = settlement.participants.find { it.userId == currentUserId }
        if (me != null) {
            binding.tvMyName.text = "${me.userName} (나)"
            binding.tvMyDrinkInfo.text = "소주 ${me.sojuCupCount}잔 · 맥주 ${me.beerCupCount}잔 마심"
            binding.tvMyPrice.text = "${decFormat.format(me.mytotal)}원"
        }

        // 3. 참가자 전체 목록 뷰 바인딩 및 렌더링
        val userLayouts = listOf(
            binding.layoutUser1, binding.layoutUser2, binding.layoutUser3, binding.layoutUser4,
            binding.layoutUser5, binding.layoutUser6, binding.layoutUser7, binding.layoutUser8
        )
        val nameViews = listOf(
            binding.tvName1, binding.tvName2, binding.tvName3, binding.tvName4,
            binding.tvName5, binding.tvName6, binding.tvName7, binding.tvName8
        )
        val drinkViews = listOf(
            binding.tvDrinkCount1, binding.tvDrinkCount2, binding.tvDrinkCount3, binding.tvDrinkCount4,
            binding.tvDrinkCount5, binding.tvDrinkCount6, binding.tvDrinkCount7, binding.tvDrinkCount8
        )
        val priceViews = listOf(
            binding.tvPrice1, binding.tvPrice2, binding.tvPrice3, binding.tvPrice4,
            binding.tvPrice5, binding.tvPrice6, binding.tvPrice7, binding.tvPrice8
        )
        val statusViews = listOf(
            binding.tvStatus1, binding.tvStatus2, binding.tvStatus3, binding.tvStatus4,
            binding.tvStatus5, binding.tvStatus6, binding.tvStatus7, binding.tvStatus8
        )
        val dividers = listOf(
            binding.divider1, binding.divider2, binding.divider3, binding.divider4,
            binding.divider5, binding.divider6, binding.divider7
        )

        val participants = settlement.participants

        for (i in 0..7) {
            if (i < participants.size) {
                userLayouts[i].visibility = View.VISIBLE
                if (i < dividers.size) dividers[i].visibility = View.VISIBLE

                val user = participants[i]
                nameViews[i].text = user.userName
                drinkViews[i].text = "소주 ${user.sojuCupCount}잔 · 맥주 ${user.beerCupCount}잔"
                priceViews[i].text = "${decFormat.format(user.mytotal)}원"

                if (user.paidStatus) {
                    statusViews[i].text = "완료"
                    statusViews[i].setTextColor(Color.parseColor("#6ED7BB")) // 초록/민트 계열 색상
                } else if (user.pendingApproval) {
                    statusViews[i].text = "승인대기"
                    statusViews[i].setTextColor(Color.parseColor("#F59E0B"))
                } else {
                    statusViews[i].text = "미완료"
                    statusViews[i].setTextColor(Color.parseColor("#999999")) // 연한 회색 색상
                }

                // 총무 확인 기능 제거 (유저 터치 클릭 리스너 설정하지 않음)
                userLayouts[i].setOnClickListener(null)

            } else {
                userLayouts[i].visibility = View.GONE
                if (i < dividers.size) dividers[i].visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
