package com.gachon.janjan.data.model

data class Order(
    val id: String = "",             // 주문 고유 ID (Firebase에서 생성 시 자동 부여 가능)
    val sessionId: String = "",      // 현재 속한 방(테이블) ID
    val userId: String = "",         // 주문한 사람 ID
    val timestamp: Long = System.currentTimeMillis(), // 주문 시간 (정렬 기준)
    val items: List<OrderItem> = emptyList() // 🔥 이 주문에 포함된 상세 메뉴들
)