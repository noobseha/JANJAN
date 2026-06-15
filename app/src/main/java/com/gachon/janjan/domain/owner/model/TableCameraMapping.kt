package com.gachon.janjan.domain.owner.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class TableCameraMapping(
    @DocumentId val mappingId: String = "",
    val storeId: String = "",
    val tableId: String = "",
    val tableNumber: Int = 0,
    val sessionId: String = "",
    val cameraDeviceId: String = "",
    val cameraName: String = "",
    val cameraIp: String = "",
    val cameraStreamUrl: String = "",
    val cameraStatus: String = "idle",
    val cameraEnabled: Boolean = false,
    val cameraSource: String = "",
    val assignedSessionId: String = "",
    val lastSeenAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isActive: Boolean
        get() = cameraEnabled && (cameraDeviceId.isNotBlank() || cameraIp.isNotBlank())

    val effectiveSessionId: String
        get() = assignedSessionId.ifBlank { sessionId }
}
