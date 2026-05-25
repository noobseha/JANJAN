package com.gachon.janjan.data.repository

import android.util.Log
import com.gachon.janjan.data.model.Settlement
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class SettlementRepository {
    private val db = FirebaseFirestore.getInstance()

    // 1. 새로운 정산 문서를 생성하고 Firebase에 저장
    fun createSettlement(settlement: Settlement, onComplete: (String?) -> Unit) {
        val ref = db.collection("settlements").document()
        val finalizedSettlement = settlement.copy(settlementId = ref.id)

        ref.set(finalizedSettlement)
            .addOnSuccessListener {
                onComplete(ref.id)
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "정산 문서 생성 실패: ${e.message}")
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
                            it.copy(paidStatus = paidStatus)
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
}
