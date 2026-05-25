package com.gachon.janjan.domain.session.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class BottleDetectionEvent(
    @DocumentId val eventId: String = "",
    val eventType: String = "pour",
    val glassId: String = "",
    val screenColorHex: String? = null,
    val physicalGlassId: String? = null,
    val drinkType: String = "soju",
    @ServerTimestamp val detectedAt: Timestamp? = null,
    val releasedAt: Timestamp? = null
)
