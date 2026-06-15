package com.gachon.janjan.ui.order

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.gachon.janjan.MenuCategories
import com.gachon.janjan.data.model.MenuItem
import com.gachon.janjan.data.model.Order
import com.gachon.janjan.data.model.OrderItem
import com.gachon.janjan.data.model.Session
import com.gachon.janjan.data.repository.OrderRepository

class OrderViewModel : ViewModel() {

    private val repository = OrderRepository()

    private val _currentSession = MutableLiveData<Session>()
    val currentSession: LiveData<Session> = _currentSession

    private var allMenuItems = listOf<MenuItem>()
    private var currentCategory = MenuCategories.ALL
    private var currentSessionId: String = ""

    private val _menuItems = MutableLiveData<List<MenuItem>>()
    val menuItems: LiveData<List<MenuItem>> = _menuItems

    private val _totalSelectedCount = MutableLiveData(0)
    val totalSelectedCount: LiveData<Int> = _totalSelectedCount

    private val _totalPrice = MutableLiveData(0)
    val totalPrice: LiveData<Int> = _totalPrice

    private val _orderSuccessEvent = MutableLiveData<Boolean?>()
    val orderSuccessEvent: LiveData<Boolean?> = _orderSuccessEvent

    fun loadData(sessionId: String) {
        currentSessionId = sessionId

        repository.getSessionWithStoreDetails(sessionId) { session ->
            session?.let { _currentSession.value = it }

            repository.getMenuItems(session?.storeId) { items ->
                if (items != null) {
                    allMenuItems = items
                    filterByCategory(MenuCategories.ALL)
                    updateTotalCount()
                }
            }
        }
    }

    fun filterByCategory(category: String) {
        currentCategory = MenuCategories.normalize(category)
        _menuItems.value = if (currentCategory == MenuCategories.ALL) {
            allMenuItems
        } else {
            allMenuItems.filter { it.category == currentCategory }
        }
    }

    fun increaseQuantity(menuId: String) {
        allMenuItems = allMenuItems.map { menu ->
            if (menu.id == menuId && !menu.isSoldOut) {
                menu.copy(quantity = menu.quantity + 1)
            } else {
                menu
            }
        }
        filterByCategory(currentCategory)
        updateTotalCount()
    }

    fun decreaseQuantity(menuId: String) {
        allMenuItems = allMenuItems.map { menu ->
            if (menu.id == menuId && menu.quantity > 0) {
                menu.copy(quantity = menu.quantity - 1)
            } else {
                menu
            }
        }
        filterByCategory(currentCategory)
        updateTotalCount()
    }

    private fun updateTotalCount() {
        val totalCount = allMenuItems.sumOf { it.quantity }
        val priceSum = allMenuItems.sumOf { it.quantity * it.price }

        _totalSelectedCount.value = totalCount
        _totalPrice.value = priceSum
    }

    fun resetOrderEvent() {
        _orderSuccessEvent.value = null
    }

    fun submitOrder(userId: String, sessionId: String = currentSessionId) {
        val cartItems = allMenuItems.filter { it.quantity > 0 && !it.isSoldOut }
        if (cartItems.isEmpty()) return
        if (sessionId.isBlank()) {
            _orderSuccessEvent.value = false
            return
        }

        val orderItems = cartItems.map { menu ->
            OrderItem(
                menuItemId = menu.id,
                itemName = menu.name,
                category = MenuCategories.normalize(menu.category),
                unitPrice = menu.price,
                quantity = menu.quantity,
                subtotal = menu.price * menu.quantity
            )
        }
        val order = Order(
            sessionId = sessionId,
            storeId = _currentSession.value?.storeId.orEmpty(),
            tableId = _currentSession.value?.tableId.orEmpty(),
            tableNumber = _currentSession.value?.tableNumber ?: 0,
            userId = userId,
            totalPrice = orderItems.sumOf { it.subtotal },
            items = orderItems
        )

        val totalPriceAdded = orderItems.sumOf { it.subtotal }
        val totalSojuAdded = orderItems
            .filter { MenuCategories.normalize(it.category) == MenuCategories.SOJU }
            .sumOf { it.quantity }

        repository.submitOrderToFirebase(order, userId, totalSojuAdded, totalPriceAdded) { isSuccess ->
            _orderSuccessEvent.value = isSuccess
        }
    }

    fun getCartSummaryItems(): List<com.gachon.janjan.domain.session.model.OrderSummaryItem> {
        return allMenuItems.filter { it.quantity > 0 && !it.isSoldOut }.map { menu ->
            com.gachon.janjan.domain.session.model.OrderSummaryItem(
                name = menu.name,
                quantity = menu.quantity,
                amount = menu.price * menu.quantity,
                category = MenuCategories.normalize(menu.category)
            )
        }
    }
}
