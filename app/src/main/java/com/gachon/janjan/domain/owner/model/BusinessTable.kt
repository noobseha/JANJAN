package com.gachon.janjan.domain.owner.model

data class BusinessTable(
    val tableId: String = "",
    val tableNumber: Int = 0,
    val label: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val activeSessionId: String = ""
) {
    val displayName: String
        get() = label.ifBlank {
            if (tableNumber > 0) "${tableNumber}번 테이블" else tableId.ifBlank { "테이블" }
        }
}
