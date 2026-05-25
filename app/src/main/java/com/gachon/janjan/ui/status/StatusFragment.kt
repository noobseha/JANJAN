package com.gachon.janjan.ui.status

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.gachon.janjan.R
import com.gachon.janjan.databinding.FragmentStatusBinding
import com.gachon.janjan.domain.session.viewmodel.SessionViewModel
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class StatusFragment : Fragment(R.layout.fragment_status) {
    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StatusViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by activityViewModels()
    private var activeSessionId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStatusBinding.bind(view)

        setupObservers()
        setupClickListeners()
        observeActiveSession()
    }

    private fun setupObservers() {
        viewModel.userName.observe(viewLifecycleOwner) { name -> binding.tvUserName.text = "${name}님" }
        viewModel.storeInfo.observe(viewLifecycleOwner) { info -> binding.tvStoreAndTable.text = info }
        viewModel.mySojuCount.observe(viewLifecycleOwner) { count -> binding.tvSojuCount.text = "${count}잔" }
        viewModel.myBeerCount.observe(viewLifecycleOwner) { count -> binding.tvBeerCount.text = "${count}잔" }
        viewModel.myExpectedPrice.observe(viewLifecycleOwner) { price ->
            binding.tvTotalPrice.text = "${DecimalFormat("#,###").format(price)}원"
        }
        viewModel.myCardColor.observe(viewLifecycleOwner) { colorHex ->
            val parsedColor = runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.parseColor("#5AC4AF"))
            binding.cardStatus.setCardBackgroundColor(parsedColor)
        }

        viewModel.activeFriends.observe(viewLifecycleOwner) { friends ->
            binding.cardSocialSection.visibility = if (friends.isEmpty()) View.GONE else View.VISIBLE

            val cards = listOf(binding.cardFriend1, binding.cardFriend2, binding.cardFriend3, binding.cardFriend4)
            val nameViews = listOf(binding.tvName1, binding.tvName2, binding.tvName3, binding.tvName4)
            val infoViews = listOf(binding.tvInfo1, binding.tvInfo2, binding.tvInfo3, binding.tvInfo4)
            val dotViews = listOf(binding.ivDot1, binding.ivDot2, binding.ivDot3, binding.ivDot4)

            for (i in 0..3) {
                if (i < friends.size) {
                    cards[i].visibility = View.VISIBLE
                    val friend = friends[i]
                    nameViews[i].text = friend.name
                    infoViews[i].text = if (friend.isOnline) {
                        "${friend.storeName} · ${friend.drinkCount}잔"
                    } else {
                        "오프라인"
                    }
                    dotViews[i].visibility = if (friend.isOnline) View.VISIBLE else View.GONE
                } else {
                    cards[i].visibility = View.GONE
                }
            }
        }

        viewModel.recentSession.observe(viewLifecycleOwner) { session ->
            if (session != null) {
                binding.cardRecentSection.visibility = View.VISIBLE
                binding.tvRecentStore.text = session.storeName
                binding.tvRecentDetails.text =
                    "${session.date} · ${session.headCount}명 · ${DecimalFormat("#,###").format(session.totalPrice)}원"
            } else {
                binding.cardRecentSection.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnActionOrder.setOnClickListener {
            if (activeSessionId.isBlank()) return@setOnClickListener
            findNavController().navigate(
                R.id.orderFragment,
                Bundle().apply { putString("sessionId", activeSessionId) }
            )
        }

        binding.btnActionGlassMapping.setOnClickListener {
            if (activeSessionId.isBlank()) return@setOnClickListener
            findNavController().navigate(
                R.id.glassColorFragment,
                Bundle().apply {
                    putString("sessionId", activeSessionId)
                    putBoolean("showDone", false)
                }
            )
        }

        binding.btnSettlement.setOnClickListener {
            if (activeSessionId.isBlank()) return@setOnClickListener
            binding.btnSettlement.isEnabled = false
            viewModel.startSettlement(activeSessionId) { started ->
                if (!started) {
                    binding.btnSettlement.isEnabled = true
                    Toast.makeText(requireContext(), "정산 시작에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    return@startSettlement
                }
                viewModel.createSettlementFromCurrentSession { settlementId ->
                    binding.btnSettlement.isEnabled = true
                    if (settlementId != null) {
                        Toast.makeText(requireContext(), "정산이 시작되었습니다.", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(
                            R.id.action_status_to_settlement,
                            Bundle().apply { putString("settlementId", settlementId) }
                        )
                    } else {
                        Toast.makeText(requireContext(), "정산 생성 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.tvRecentViewAll.setOnClickListener {
            findNavController().navigate(R.id.sessionHomeFragment)
        }
    }

    private fun observeActiveSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.activeSessionId.collect { sessionId ->
                    val resolvedSessionId = sessionId.ifBlank {
                        arguments?.getString("sessionId").orEmpty()
                    }
                    if (resolvedSessionId.isBlank() || resolvedSessionId == activeSessionId) {
                        return@collect
                    }
                    activeSessionId = resolvedSessionId
                    refreshCurrentSession()
                }
            }
        }
    }

    private fun refreshCurrentSession() {
        if (activeSessionId.isBlank()) return
        viewModel.refreshData(
            sessionId = activeSessionId,
            userId = sessionViewModel.currentUserId
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
