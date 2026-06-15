package com.gachon.janjan.data.repository

import android.util.Log
import com.gachon.janjan.data.model.Settlement
import com.gachon.janjan.data.model.SettlementParticipant
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class SettlementRepository {
    private val db = FirebaseFirestore.getInstance()

    // 1. 같은 sessionId의 정산 문서는 재사용하고, 금액은 최신 주문 기준으로 갱신
    fun createSettlement(settlement: Settlement, onComplete: (String?) -> Unit) {
        if (settlement.sessionId.isBlank()) {
            onComplete(null)
            return
        }

        val settlementsRef = db.collection("settlements")
        settlementsRef
            .whereEqualTo("sessionId", settlement.sessionId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val existingDoc = snapshot.documents.firstOrNull()
                if (existingDoc == null) {
                    val ref = settlementsRef.document()
                    val finalizedSettlement = settlement.copy(settlementId = ref.id)
                    ref.set(finalizedSettlement)
                        .addOnSuccessListener { onComplete(ref.id) }
                        .addOnFailureListener { e ->
                            Log.e("JANJAN_BUG", "정산 문서 생성 실패: ${e.message}")
                            onComplete(null)
                        }
                    return@addOnSuccessListener
                }

                val existingSettlement = existingDoc.toObject(Settlement::class.java)
                val mergedSettlement = settlement.copy(
                    settlementId = existingDoc.id,
                    participants = mergeParticipants(
                        incoming = settlement.participants,
                        existing = existingSettlement?.participants.orEmpty()
                    )
                )
                existingDoc.reference
                    .set(mergedSettlement, SetOptions.merge())
                    .addOnSuccessListener { onComplete(existingDoc.id) }
                    .addOnFailureListener { e ->
                        Log.e("JANJAN_BUG", "정산 문서 갱신 실패: ${e.message}")
                        onComplete(null)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "기존 정산 문서 조회 실패: ${e.message}")
                onComplete(null)
            }
    }

    // 2. 특정 정산 문서를 실시간 구독 (총무가 송금 완료 처리 시 실시간 변경 반영 위함)
    fun observeSettlement(settlementId: String, onUpdate: (Settlement?) -> Unit): ListenerRegistration {
        return db.collection("settlements").document(settlementId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("JANJAN_BUG", "정산 데이터 구독 실패: ${error.message}")
                    onUpdate(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val settlement = snapshot.toObject(Settlement::class.java)
                    onUpdate(settlement)
                } else {
                    onUpdate(null)
                }
            }
    }

    // 3. 특정 유저의 송금 완료 여부(paidStatus) 업데이트
    fun updatePaidStatus(settlementId: String, userId: String, paidStatus: Boolean, onComplete: (Boolean) -> Unit) {
        val docRef = db.collection("settlements").document(settlementId)
        
        docRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val settlement = snapshot.toObject(Settlement::class.java)
                if (settlement != null) {
                    // 참가자 목록 중 특정 유저의 paidStatus 수정
                    val updatedParticipants = settlement.participants.map {
                        if (it.userId == userId) {
                            it.copy(
                                paidStatus = paidStatus,
                                pendingApproval = false
                            )
                        } else {
                            it
                        }
                    }
                    docRef.update("participants", updatedParticipants)
                        .addOnSuccessListener { onComplete(true) }
                        .addOnFailureListener { onComplete(false) }
                } else {
                    onComplete(false)
                }
            } else {
                onComplete(false)
            }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    private fun mergeParticipants(
        incoming: List<SettlementParticipant>,
        existing: List<SettlementParticipant>
    ): List<SettlementParticipant> {
        val existingByUserId = existing.associateBy { it.userId }
        return incoming.map { participant ->
            val previous = existingByUserId[participant.userId]
            if (previous == null) {
                participant
            } else {
                participant.copy(
                    paidStatus = previous.paidStatus,
                    pendingApproval = previous.pendingApproval,
                    paymentMethod = previous.paymentMethod
                )
            }
        }
    }
}
