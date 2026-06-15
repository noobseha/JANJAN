package com.gachon.janjan.domain.session.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class SessionParticipant(
    @DocumentId val participantId: String = "",
    val userId: String = "",
    val userName: String = "",
    val imageUrl: String = "",
    val glassColor: String? = null,
    val glassMappingType: String = "color",
    val physicalGlassId: String? = null,
    val mappedScreenColorHex: String? = null,
    val glassMappedAt: Timestamp? = null,
    val sojuDrinkCount: Int = 0,
    val beerDrinkCount: Int = 0,
    val lastDrinkType: String? = null,
    val lastDrinkAt: Timestamp? = null,
    val foodTotal: Int = 0,
    val sojuTotal: Int = 0,
    val beerTotal: Int = 0,
    val totalPrice: Int = 0,
    val paidStatus: Boolean = false,
    val pendingApproval: Boolean = false,
    val paymentMethod: String = "",
    @ServerTimestamp val joinedAt: Timestamp? = null
)
