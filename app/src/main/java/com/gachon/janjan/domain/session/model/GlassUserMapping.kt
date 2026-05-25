package com.gachon.janjan.domain.session.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class GlassUserMapping(
    @DocumentId val mappingId: String = "",
    val userId: String = "",
    val glassId: String = "",
    val drinkType: String = "soju",
    val drinkCount: Int = 0,
    val sojuDrinkCount: Int = 0,
    val beerDrinkCount: Int = 0,
    val mappingSource: String = "colorPending",
    val screenColorHex: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    val mappedAt: Timestamp? = null
)
