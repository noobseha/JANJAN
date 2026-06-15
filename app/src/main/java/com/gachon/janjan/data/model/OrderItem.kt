package com.gachon.janjan.data.model

data class OrderItem(
    val menuItemId: String = "",     // 메뉴 고유 ID
    val itemName: String = "",       // 메뉴 이름 (예: 김치찌개, 참이슬)
    val category: String = "",       // 카테고리 (food, soju, beer)
    val unitPrice: Int = 0,          // 단가
    val quantity: Int = 0,           // 수량
    val subtotal: Int = 0            // 이 메뉴의 총액 (단가 * 수량)
)
