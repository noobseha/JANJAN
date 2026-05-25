package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Locale

class DetectionEventRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    suspend fun insertDetection(
        sessionId: String,
        glassId: String,
        drinkType: String = "soju"
    ): String {
        val ref = db.collection(FirestorePaths.detectionEvents(sessionId)).document()
        ref.set(
            hashMapOf(
                "eventType" to "pour",
                "glassId" to glassId.trim(),
                "drinkType" to drinkType,
                "detectedAt" to FieldValue.serverTimestamp(),
                "releasedAt" to null
            )
        ).await()
        return ref.id
    }

    suspend fun insertColorGlassProximity(
        sessionId: String,
        screenColorHex: String,
        physicalGlassId: String,
        drinkType: String = "soju"
    ): String {
        val ref = db.collection(FirestorePaths.detectionEvents(sessionId)).document()
        ref.set(
            hashMapOf(
                "eventType" to "colorGlassProximity",
                "screenColorHex" to screenColorHex.normalizeHexColor(),
                "physicalGlassId" to physicalGlassId.trim(),
                "drinkType" to drinkType,
                "detectedAt" to FieldValue.serverTimestamp(),
                "releasedAt" to null
            )
        ).await()
        return ref.id
    }

    suspend fun updateReleased(sessionId: String, eventId: String) {
        db.document("${FirestorePaths.detectionEvents(sessionId)}/$eventId")
            .update("releasedAt", FieldValue.serverTimestamp())
            .await()
    }

    private fun String.normalizeHexColor(): String {
        val compact = trim().removePrefix("#").lowercase(Locale.US)
        return "#$compact"
    }
}
