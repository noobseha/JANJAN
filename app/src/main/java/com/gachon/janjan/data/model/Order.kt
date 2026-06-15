package com.gachon.janjan.data.model

data class Order(
    val id: String = "",
    val sessionId: String = "",
    val storeId: String = "",
    val tableId: String = "",
    val tableNumber: Int = 0,
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val totalPrice: Int = 0,
    val items: List<OrderItem> = emptyList()
)
