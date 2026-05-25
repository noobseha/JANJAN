package com.gachon.janjan.domain.session.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class DrinkCounter(
    @DocumentId val counterId: String = "",
    val userId: String = "",
    val participantId: String = "",
    val sojuCount: Int = 0,
    val beerCount: Int = 0,
    val totalCount: Int = 0,
    val lastDrinkType: String = "",
    val lastGlassId: String = "",
    val lastDetectionEventId: String = "",
    val updatedAt: Timestamp? = null
)
