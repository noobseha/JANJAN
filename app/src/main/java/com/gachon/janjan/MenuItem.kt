package com.gachon.janjan

data class MenuItem(
    val id: String = "",
    val menuId: String = "",
    val storeId: String = "",
    val name: String = "",
    val price: Int = 0,
    val category: String = "",
    val imageUrl: String = "",
    val isSoldOut: Boolean = false,
    val displayOrder: Int = 0,
    val isActive: Boolean = true
)
