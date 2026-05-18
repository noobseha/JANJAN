package com.gachon.janjan.data.model

// 술 먹고 있는 온라인 친구 정보
data class ActiveFriend(
    val userId: String = "",
    val name: String = "",
    val storeName: String = "", // 마시고 있는 가게 이름 (isActive가 true일 때만 의미 있음)
    val drinkCount: Int = 0,    // 마신 잔 수
    val isOnline: Boolean = true
)

// 최근 술자리 정보 (현재 세션 직전 세션)
data class RecentSession(
    val sessionId: String = "",
    val storeName: String = "",
    val date: String = "",       // yyyy.MM.dd 형식
    val headCount: Int = 0,
    val totalPrice: Int = 0
)