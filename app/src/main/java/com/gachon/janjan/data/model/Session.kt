package com.gachon.janjan.data.model

import com.google.firebase.firestore.DocumentId

data class Session(
    @DocumentId // Firestore의 문서 ID를 자동으로 매핑
    val id: String = "",
    val storeId: Long = 0,
    val tableId: Long = 0,
    val inviteCode: String = "",
    val status: String = "",
    val startedAt: Long= 0,
    val storeName: String = "",
    val imageUrl: String = ""
)