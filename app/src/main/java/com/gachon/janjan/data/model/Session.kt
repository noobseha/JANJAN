package com.gachon.janjan.data.model

import com.google.firebase.firestore.DocumentId

data class Session(
    @DocumentId // Firestore의 문서 ID를 자동으로 매핑
    val id: String = "",
    val sessionId: String = "",
    val storeId: String = "",
    val tableId: String = "",
    val tableNumber: Int = 0,
    val inviteCode: String = "",
    val status: String = "",
    val startedAt: Long = 0,
    val endedAt: Long = 0,
    val storeName: String = "",
    val imageUrl: String = "",
    val totalSojuDrinkCount: Int = 0,
    val totalBeerDrinkCount: Int = 0,
    val totalPrice: Int = 0,
    val totalSojuPrice: Int = 0,
    val totalBeerPrice: Int = 0,
    val totalFoodPrice: Int = 0
)
