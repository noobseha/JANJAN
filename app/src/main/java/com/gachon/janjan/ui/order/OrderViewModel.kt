package com.gachon.janjan.ui.order

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.gachon.janjan.data.model.MenuItem
import com.gachon.janjan.data.model.Order
import com.gachon.janjan.data.model.OrderItem
import com.gachon.janjan.data.model.Session
import com.gachon.janjan.data.repository.OrderRepository

class OrderViewModel : ViewModel() {

    private val repository = OrderRepository()

    // 상단 세션 정보
    private val _currentSession = MutableLiveData<Session>()
    val currentSession: LiveData<Session> = _currentSession

    // 🔥 1. 전체 메뉴 원본 백업 (이게 진짜 장바구니 역할도 함)
    private var allMenuItems = listOf<MenuItem>()

    // 현재 보고 있는 카테고리 기억용 (기본값: food)
    private var currentCategory = "all"

    // 🔥 2. 화면에 보여줄 필터링된 메뉴 리스트 (중복 선언 제거!)
    private val _menuItems = MutableLiveData<List<MenuItem>>()
    val menuItems: LiveData<List<MenuItem>> = _menuItems

    // 하단 총 개수 및 금액
    private val _totalSelectedCount = MutableLiveData(0)
    val totalSelectedCount: LiveData<Int> = _totalSelectedCount

    private val _totalPrice = MutableLiveData(0)
    val totalPrice: LiveData<Int> = _totalPrice

    // 🔥 3. loadData 하나로 깔끔하게 통합
    fun loadData(storeId: Long, sessionId: String) {
        // 세션 정보 불러오기
        repository.getSessionWithStoreDetails(sessionId) { session ->
            session?.let { _currentSession.value = it }
        }

        // 메뉴 리스트 불러오기
        repository.getMenuItems(storeId) { items ->
            if (items != null) {
                // DB에서 가져온 데이터를 '원본 백업'에 먼저 저장!
                allMenuItems = items
                // 데이터를 다 가져왔으니 기본 카테고리(안주)로 화면 띄우기
                filterByCategory("all")
            }
        }
    }

    // 필터 함수
    fun filterByCategory(category: String) {
        currentCategory = category // 내가 지금 무슨 카테고리 보고 있는지 기억
        if (allMenuItems.isEmpty()) return

        if (category == "all") {
            _menuItems.value = allMenuItems
        } else {
            val filteredList = allMenuItems.filter { it.category == category }
            _menuItems.value = filteredList
        }
    }

    // '+' 버튼 클릭 시 (🔥 화면이 아니라 '원본 데이터'의 수량을 올려야 함)
    fun increaseQuantity(menuId: String) {
        allMenuItems = allMenuItems.map { menu ->
            if (menu.id == menuId) menu.copy(quantity = menu.quantity + 1) else menu
        }
        filterByCategory(currentCategory) // 수량 올리고 나서 현재 화면 새로고침
        updateTotalCount()
    }

    // '-' 버튼 클릭 시
    fun decreaseQuantity(menuId: String) {
        allMenuItems = allMenuItems.map { menu ->
            if (menu.id == menuId && menu.quantity > 0) {
                menu.copy(quantity = menu.quantity - 1)
            } else menu
        }
        filterByCategory(currentCategory) // 수량 내리고 나서 현재 화면 새로고침
        updateTotalCount()
    }

    // 수량/금액 계산
    private fun updateTotalCount() {
        // 🔥 계산도 '현재 화면에 뜬 메뉴'가 아니라 '원본(장바구니 전체)' 기준으로 해야 해!
        val totalCount = allMenuItems.sumOf { it.quantity }
        val priceSum = allMenuItems.sumOf { it.quantity * it.price }

        _totalSelectedCount.value = totalCount
        _totalPrice.value = priceSum
    }

    // 주문 성공 여부를 프래그먼트에 알려줄 변수
    private val _orderSuccessEvent = MutableLiveData<Boolean?>()
    val orderSuccessEvent: LiveData<Boolean?> = _orderSuccessEvent

    fun resetOrderEvent() {
        _orderSuccessEvent.value = null
    }

    // 주문하기 버튼을 눌렀을 때 실행될 함수
    // OrderViewModel.kt 내부

    fun submitOrder(userId: String) {
        val cartItems = allMenuItems.filter { it.quantity > 0 }
        if (cartItems.isEmpty()) return

        val orderItems = cartItems.map { menu ->
            OrderItem(
                menuItemId = menu.id.toLongOrNull() ?: 0L,
                itemName = menu.name,
                category = menu.category, // "food", "soju", "beer" 등
                unitPrice = menu.price,
                quantity = menu.quantity,
                subtotal = menu.price * menu.quantity
            )
        }
        // 🔥🔥지금은 임시 테스트용! 나중에 바꿔야 해
        val currentSessionId = "session_001"

        val order = Order(
            sessionId = currentSessionId,
            userId = userId,
            items = orderItems
        )

        // 🔥 1. 이번 주문의 총 금액 계산하기
        val totalPriceAdded = orderItems.sumOf { it.subtotal }

        // 🔥 2. 이번 주문에 포함된 소주 개수 계산하기 (category가 "soju"인 것만 합침)
        // (주의: 카테고리 이름이 실제 DB와 맞는지 꼭 확인해!)
        val totalSojuAdded = orderItems.filter { it.category == "soju" }.sumOf { it.quantity }

        // 🔥 3. 레포지토리에 바뀐 규칙대로 매개변수 4개를 꽉꽉 채워서 전달!
        repository.submitOrderToFirebase(order, userId, totalSojuAdded, totalPriceAdded) { isSuccess ->
            if (isSuccess) {
                _orderSuccessEvent.value = true
            } else {
                _orderSuccessEvent.value = false
            }
        }
    }
}