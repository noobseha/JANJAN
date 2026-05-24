package com.gachon.janjan

data class NotificationItem(
    val id: String = "",
    val tableNumber: Int = 0,
    val memberCount: Int = 0,
    val message: String = "",
    val createdAt: com.google.firebase.Timestamp? = null
)