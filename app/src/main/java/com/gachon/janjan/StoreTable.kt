package com.gachon.janjan

data class StoreTable(
    val id: String = "",
    val tableNumber: Int = 0,
    val ipAddress: String = "",
    val isActive: Boolean = true,
    val activeSessionId: String = "",
    val inviteCode: String = ""
)
