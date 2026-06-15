package com.gachon.janjan

data class SettlementParticipant(
    val userId: String = "",
    val userName: String = "",
    val myTotal: Long = 0,
    val paidStatus: Boolean = false,
    val pendingApproval: Boolean = false,
    val beerCupCount: Int = 0,
    val sojuCupCount: Int = 0
)

data class Settlement(
    val id: String = "",
    val sessionId: String = "",
    val tableId: Int = 0,
    val totalPrice: Long = 0,
    val timeInfo: String = "",
    val participants: List<SettlementParticipant> = emptyList()
)
