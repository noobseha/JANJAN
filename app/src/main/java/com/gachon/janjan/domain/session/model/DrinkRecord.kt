package com.gachon.janjan.domain.session.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class DrinkRecord(
    @DocumentId val recordId: String = "",
    val participantId: String = "",
    val userId: String = "",
    val drinkType: String = "",
    val glassId: String = "",
    val detectionEventId: String = "",
    val durationMs: Long = 0,
    val sojuCountDelta: Int = 0,
    val beerCountDelta: Int = 0,
    @ServerTimestamp val recordedAt: Timestamp? = null
)
