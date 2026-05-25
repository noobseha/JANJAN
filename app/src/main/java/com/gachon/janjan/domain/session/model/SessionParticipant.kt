package com.gachon.janjan.domain.session.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class SessionParticipant(
    @DocumentId val participantId: String = "",
    val userId: String = "",
    val userName: String = "",
    val glassColor: String? = null,
    val glassMappingType: String = "color",
    val physicalGlassId: String? = null,
    val mappedScreenColorHex: String? = null,
    val glassMappedAt: Timestamp? = null,
    val sojuDrinkCount: Int = 0,
    val beerDrinkCount: Int = 0,
    val lastDrinkType: String? = null,
    val lastDrinkAt: Timestamp? = null,
    @ServerTimestamp val joinedAt: Timestamp? = null
)
