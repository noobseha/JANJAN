package com.gachon.janjan.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class PaymentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    data class PaymentUpdateResult(
        val sessionClosed: Boolean = false
    )

    suspend fun completeSettlement(
        sessionId: String,
        userId: String,
        paymentMethod: String
    ): PaymentUpdateResult {
        if (sessionId.isBlank()) error("sessionId is blank")
        if (userId.isBlank()) error("userId is blank")

        val settlementDoc = findSettlementDocument(sessionId)
            ?: error("Settlement not found for session: $sessionId")
        val isDirectPay = paymentMethod == DIRECT_PAYMENT_METHOD
        return updateParticipantPaymentState(
            settlementRef = settlementDoc.reference,
            userId = userId,
            paidStatus = !isDirectPay,
            pendingApproval = isDirectPay,
            paymentMethod = paymentMethod
        )
    }

    suspend fun updateParticipantApproval(
        settlementId: String,
        userId: String,
        paidStatus: Boolean
    ): PaymentUpdateResult {
        if (settlementId.isBlank()) error("settlementId is blank")
        if (userId.isBlank()) error("userId is blank")

        return updateParticipantPaymentState(
            settlementRef = db.collection("settlements").document(settlementId),
            userId = userId,
            paidStatus = paidStatus,
            pendingApproval = false,
            paymentMethod = if (paidStatus) DIRECT_PAYMENT_METHOD else ""
        )
    }

    private suspend fun updateParticipantPaymentState(
        settlementRef: DocumentReference,
        userId: String,
        paidStatus: Boolean,
        pendingApproval: Boolean,
        paymentMethod: String
    ): PaymentUpdateResult = runCatching {
        db.runTransaction { transaction ->
            val settlementSnap = transaction.get(settlementRef)
            if (!settlementSnap.exists()) {
                error("Settlement not found: ${settlementRef.id}")
            }
            val settlementData = settlementSnap.data.orEmpty()
            val sessionId = settlementSnap.getString("sessionId").orEmpty()
                .ifBlank { error("settlement.sessionId is blank") }
            val sessionRef = db.collection("sessions").document(sessionId)
            val sessionSnap = transaction.get(sessionRef)
            if (!sessionSnap.exists()) {
                error("Session not found: $sessionId")
            }

            val participantMaps = settlementSnap.getParticipantMaps()
            val updatedAtMs = System.currentTimeMillis()
            val updatedParticipants = participantMaps.map { participant ->
                if (participant["userId"].asString() == userId) {
                    participant.toMutableMap().apply {
                        put("paidStatus", paidStatus)
                        put("pendingApproval", pendingApproval)
                        put("paymentMethod", paymentMethod)
                        if (paidStatus) {
                            put("paidAtMs", updatedAtMs)
                        }
                        if (pendingApproval) {
                            put("pendingApprovalAtMs", updatedAtMs)
                        }
                    }
                } else {
                    participant
                }
            }
            val hasTarget = participantMaps.any { it["userId"].asString() == userId }
            if (!hasTarget) {
                error("Participant not found in settlement: $userId")
            }

            val allPaid = updatedParticipants.isNotEmpty() &&
                updatedParticipants.all { it["paidStatus"] as? Boolean == true }
            transaction.update(
                settlementRef,
                mapOf(
                    "participants" to updatedParticipants,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            transaction.set(
                sessionRef.collection("participants").document(userId),
                mapOf(
                    "paidStatus" to paidStatus,
                    "pendingApproval" to pendingApproval,
                    "paymentMethod" to paymentMethod,
                    "updatedAt" to FieldValue.serverTimestamp()
                ) + if (paidStatus) {
                    mapOf("paidAt" to FieldValue.serverTimestamp())
                } else if (pendingApproval) {
                    mapOf("pendingApprovalAt" to FieldValue.serverTimestamp())
                } else {
                    emptyMap()
                },
                SetOptions.merge()
            )

            val wasAlreadyClosed = sessionSnap.getString("status") == "closed"
            if (allPaid && !wasAlreadyClosed) {
                closeSessionInTransaction(
                    transaction = transaction,
                    sessionRef = sessionRef,
                    sessionSnap = sessionSnap,
                    settlementData = settlementData,
                    participantCount = updatedParticipants.size
                )
            }
            PaymentUpdateResult(sessionClosed = allPaid)
        }.await()
    }.onFailure { e ->
        Log.e("JANJAN_BUG", "결제 상태 업데이트 실패: ${e.message}")
    }.getOrThrow()

    private fun closeSessionInTransaction(
        transaction: com.google.firebase.firestore.Transaction,
        sessionRef: DocumentReference,
        sessionSnap: DocumentSnapshot,
        settlementData: Map<String, Any>,
        participantCount: Int
    ) {
        val storeId = sessionSnap.getString("storeId").orEmpty()
        val tableId = sessionSnap.getString("tableId").orEmpty()
        val now = FieldValue.serverTimestamp()
        transaction.update(
            sessionRef,
            mapOf(
                "status" to "closed",
                "endedAt" to now,
                "salesRecordedAt" to now,
                "updatedAt" to now
            )
        )

        if (storeId.isNotBlank() && tableId.isNotBlank()) {
            transaction.set(
                db.collection("stores").document(storeId)
                    .collection("tables").document(tableId),
                mapOf(
                    "activeSessionId" to "",
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            transaction.set(
                db.collection("stores").document(storeId)
                    .collection("sessions").document(sessionRef.id),
                buildStoreSessionSummary(
                    sessionId = sessionRef.id,
                    sessionSnap = sessionSnap,
                    settlementData = settlementData,
                    participantCount = participantCount,
                    now = now
                ),
                SetOptions.merge()
            )
        }
    }

    private fun buildStoreSessionSummary(
        sessionId: String,
        sessionSnap: DocumentSnapshot,
        settlementData: Map<String, Any>,
        participantCount: Int,
        now: Any
    ): Map<String, Any?> {
        val totalPrice = settlementData["totalPrice"].asInt()
            .takeIf { it > 0 } ?: sessionSnap.getIntValue("totalPrice")
        return mapOf(
            "sessionId" to sessionId,
            "storeId" to sessionSnap.getString("storeId").orEmpty(),
            "storeName" to sessionSnap.getString("storeName").orEmpty(),
            "tableId" to sessionSnap.getString("tableId").orEmpty(),
            "tableNumber" to sessionSnap.getIntValue("tableNumber"),
            "totalPrice" to totalPrice,
            "totalSojuPrice" to sessionSnap.getIntValue("totalSojuPrice"),
            "totalBeerPrice" to sessionSnap.getIntValue("totalBeerPrice"),
            "totalFoodPrice" to sessionSnap.getIntValue("totalFoodPrice"),
            "participantCount" to participantCount,
            "status" to "closed",
            "startedAt" to sessionSnap.get("startedAt"),
            "endedAt" to now,
            "updatedAt" to now
        )
    }

    private suspend fun findSettlementDocument(sessionId: String): DocumentSnapshot? =
        db.collection("settlements")
            .whereEqualTo("sessionId", sessionId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()

    private fun DocumentSnapshot.getParticipantMaps(): List<Map<String, Any?>> {
        val rawParticipants = get("participants") as? List<*> ?: return emptyList()
        return rawParticipants.mapNotNull { raw ->
            (raw as? Map<*, *>)?.mapKeys { it.key.toString() }
        }
    }

    private fun DocumentSnapshot.getIntValue(field: String): Int =
        when (val value = get(field)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }

    private fun Any?.asInt(): Int =
        when (this) {
            is Number -> toInt()
            is String -> toIntOrNull() ?: 0
            else -> 0
        }

    private fun Any?.asString(): String =
        when (this) {
            is String -> this
            is Number -> toLong().toString()
            else -> ""
        }

    companion object {
        const val DIRECT_PAYMENT_METHOD = "직접 결제"
    }
}
