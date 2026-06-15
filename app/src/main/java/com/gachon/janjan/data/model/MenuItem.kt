package com.gachon.janjan.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class MenuItem(
    @DocumentId
    val id: String = "",
    val menuId: String = "",
    val storeId: String = "",
    val name: String = "",
    val price: Int = 0,
    val category: String = "",
    val imageUrl: String = "",
    var isSoldOut: Boolean = false,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,

    // UI에서만 사용하는 변수 (Firestore에 저장되지 않도록 @Exclude 처리)
    @get:Exclude
    var quantity: Int = 0
)
