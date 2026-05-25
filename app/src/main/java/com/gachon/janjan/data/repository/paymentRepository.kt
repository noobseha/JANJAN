package com.gachon.janjan.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class PaymentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun completeSettlement(sessionId: String) {
        if (sessionId.isBlank()) error("sessionId is blank")

        val direct = db.collection("sessions").document(sessionId).get().await()
        val sessionRef = if (direct.exists()) {
            direct.reference
        } else {
            db.collection("sessions")
                .whereEqualTo("sessionId", sessionId)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()
                ?.reference
                ?: error("Session not found: $sessionId")
        }

        runCatching {
            sessionRef.update(
                mapOf(
                    "status" to "closed",
                    "endedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }.onFailure { e ->
            Log.e("JANJAN_BUG", "결제 완료 상태 업데이트 실패: ${e.message}")
            throw e
        }
    }
}
