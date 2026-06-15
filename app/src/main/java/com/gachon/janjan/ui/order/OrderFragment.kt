package com.gachon.janjan.ui.order

import android.os.Bundle
import android.widget.Toast
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.gachon.janjan.R
import com.gachon.janjan.databinding.FragmentOrderBinding
import com.gachon.janjan.domain.session.viewmodel.SessionViewModel
import java.text.DecimalFormat
import android.transition.Slide
import android.transition.TransitionManager
import android.view.Gravity
import kotlinx.coroutines.launch
import com.gachon.janjan.MenuCategories

class OrderFragment : Fragment(R.layout.fragment_order) {
    private var _binding: FragmentOrderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OrderViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by activityViewModels()
    private var activeSessionId: String = ""

    // 🔥 1. 현재 선택된 카테고리를 추적하는 변수 (반드시 클래스 바로 아래에 선언!)
    private var currentSelectedCategory = MenuCategories.ALL

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOrderBinding.bind(view)

        // 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 1. 어댑터 설정
        val menuAdapter = MenuAdapter(
            onPlusClick = { id -> viewModel.increaseQuantity(id) },
            onMinusClick = { id -> viewModel.decreaseQuantity(id) }
        )

        binding.rvMenuList.apply {
            adapter = menuAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // 2. 데이터 관찰
        viewModel.menuItems.observe(viewLifecycleOwner) { items ->
            menuAdapter.submitList(items)
        }

        // DB에서 가져온 세션/가게 정보 반영
        viewModel.currentSession.observe(viewLifecycleOwner) { session ->
            session?.let {
                binding.tvStoreName.text = it.storeName.ifBlank { "가게 정보 없음" }
                binding.tvTableName.text = "테이블 ${it.tableNumber.takeIf { number -> number > 0 } ?: it.tableId}"
                binding.tvStatus.text = if (!it.status.isNullOrEmpty()) it.status else "연결됨"

                if (it.imageUrl.isNotEmpty()) {
                    com.bumptech.glide.Glide.with(this)
                        .load(it.imageUrl)
                        .circleCrop()
                        .placeholder(R.drawable.bg_circle_white)
                        .error(R.drawable.ic_default)
                        .into(binding.ivStoreLogo)
                }
            }
        }

        // 장바구니 수량에 따른 하단 버튼 업데이트
        viewModel.totalPrice.observe(viewLifecycleOwner) { price ->
            val count = viewModel.totalSelectedCount.value ?: 0
            val formattedPrice = DecimalFormat("#,###").format(price)

            binding.btnOrder.apply {
                if (count > 0) {
                    text = "${count}개의 메뉴 주문하기 | 총 ${formattedPrice}원"
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.main_mint))
                    isEnabled = true
                } else {
                    text = "메뉴를 선택해주세요"
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_gray))
                    isEnabled = false
                }
            }
        }

        // 주문하기 버튼 클릭 후 status 화면으로 이동
        binding.btnOrder.setOnClickListener {
            sessionViewModel.setLastOrderedItems(viewModel.getCartSummaryItems())
            viewModel.submitOrder(
                userId = sessionViewModel.currentUserId,
                sessionId = activeSessionId
            )
        }

        // 🔥 필터 아이콘 클릭 이벤트
        binding.ivFilterToggle.setOnClickListener {
            val transition = Slide(Gravity.END)
            transition.duration = 300
            transition.addTarget(binding.layoutCategoryFilter)
            TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)

            // 창을 열거나 닫을 때 무조건 상태를 "전체"로 초기화
            currentSelectedCategory = MenuCategories.ALL
            resetCategoryUI()
            viewModel.filterByCategory(MenuCategories.ALL)

            if (binding.layoutCategoryFilter.visibility == View.VISIBLE) {
                binding.layoutCategoryFilter.visibility = View.GONE
            } else {
                binding.layoutCategoryFilter.visibility = View.VISIBLE
            }
        }

        // 🔥 카테고리 클릭 리스너 설정 (토글 함수 연결)
        binding.tvCategoryFood.setOnClickListener { toggleCategory(binding.tvCategoryFood, MenuCategories.FOOD) }
        binding.tvCategorySoju.setOnClickListener { toggleCategory(binding.tvCategorySoju, MenuCategories.SOJU) }
        binding.tvCategoryBeer.setOnClickListener { toggleCategory(binding.tvCategoryBeer, MenuCategories.BEER) }
        binding.tvCategoryDrink.setOnClickListener { toggleCategory(binding.tvCategoryDrink, MenuCategories.DRINK) }

        // 🔥 앱 처음 켰을 때 초기 UI 상태 (모두 회색)
        resetCategoryUI()

        observeActiveSession()

        // 🔥 뷰모델이 주문 성공했다고 신호를 보내면, 그때 화면 이동!
        viewModel.orderSuccessEvent.observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess == true) {
                // 주문 성공 토스트
                Toast.makeText(requireContext(), "주문이 완료되었습니다!", Toast.LENGTH_SHORT).show()
                sessionViewModel.loadOrderSummaries(activeSessionId)
                val needsColorMapping = sessionViewModel.participants.value
                    .firstOrNull { it.userId == sessionViewModel.currentUserId }
                    ?.glassColor
                    .isNullOrBlank()
                if (needsColorMapping) {
                    findNavController().navigate(
                        R.id.glassColorFragment,
                        Bundle().apply {
                            putString("sessionId", activeSessionId)
                            putBoolean("showDone", true)
                        }
                    )
                } else {
                    val returnedHome = findNavController().popBackStack(R.id.sessionHomeFragment, false)
                    if (!returnedHome) {
                        findNavController().navigate(R.id.sessionHomeFragment)
                    }
                }
                viewModel.resetOrderEvent()
            } else if (isSuccess == false) {
                Toast.makeText(requireContext(), "주문에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                viewModel.resetOrderEvent()
            }
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
                    viewModel.loadData(resolvedSessionId)
                }
            }
        }
    }

    // onviewcreated 외부 로직
    // 카테고리 껐다 켰다 하는 토글 로직
    private fun toggleCategory(selectedView: android.widget.TextView, category: String) {
        if (currentSelectedCategory == category) {
            // 이미 켜진 걸 또 누름 -> 필터 해제(전체 메뉴)
            currentSelectedCategory = MenuCategories.ALL
            resetCategoryUI()
            viewModel.filterByCategory(MenuCategories.ALL)
        } else {
            // 새로운 걸 누름 -> 필터 적용
            currentSelectedCategory = category
            updateCategoryUI(selectedView)
            viewModel.filterByCategory(category)
        }
    }

    // 누른 버튼 하나만 민트색으로 칠하는 함수
    private fun updateCategoryUI(selectedView: android.widget.TextView) {
        resetCategoryUI() // 일단 다 회색으로 만들고
        val selectedBg = R.drawable.bg_category_selected
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.white)
        selectedView.apply { setBackgroundResource(selectedBg); setTextColor(selectedColor) }
    }

    // 모든 버튼을 회색(미선택)으로 초기화하는 함수
    private fun resetCategoryUI() {
        val unselectedBg = R.drawable.bg_category_unselected
        val unselectedColor = ContextCompat.getColor(requireContext(), R.color.light_gray)
        binding.tvCategoryFood.apply { setBackgroundResource(unselectedBg); setTextColor(unselectedColor) }
        binding.tvCategorySoju.apply { setBackgroundResource(unselectedBg); setTextColor(unselectedColor) }
        binding.tvCategoryBeer.apply { setBackgroundResource(unselectedBg); setTextColor(unselectedColor) }
        binding.tvCategoryDrink.apply { setBackgroundResource(unselectedBg); setTextColor(unselectedColor) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
