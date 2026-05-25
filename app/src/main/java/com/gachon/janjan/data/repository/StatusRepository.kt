package com.gachon.janjan.data.repository

import android.util.Log
import com.gachon.janjan.data.model.SessionState
import com.google.firebase.firestore.FirebaseFirestore

class StatusRepository {
    private val db = FirebaseFirestore.getInstance()

    fun checkAndJoinSession(sessionId: String, userId: String, onComplete: () -> Unit) {
        db.collection("sessions")
            .whereEqualTo("sessionId", sessionId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val docRef = querySnapshot.documents[0].reference
                    val sessionState = querySnapshot.documents[0].toObject(SessionState::class.java)
                    if (sessionState != null) {
                        val isAlreadyParticipant = sessionState.participants.any { it.userId == userId }
                        if (!isAlreadyParticipant) {
                            db.collection("users").document(userId).get()
                                .addOnSuccessListener { userDoc ->
                                    val nickname = userDoc.getString("nickname") ?: userDoc.getString("name") ?: "손님"
                                    val newParticipant = hashMapOf(
                                        "userId" to userId,
                                        "userName" to nickname,
                                        "sojuCupCount" to 0,
                                        "beerCupCount" to 0,
                                        "joinedAt" to com.google.firebase.Timestamp.now()
                                    )
                                    docRef.update("participants", com.google.firebase.firestore.FieldValue.arrayUnion(newParticipant))
                                        .addOnSuccessListener {
                                            Log.d("JANJAN_BUG", "✅ 세션 참가 완료: $nickname")
                                            onComplete()
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("JANJAN_BUG", "❌ 세션 참가 실패: ${e.message}")
                                            onComplete()
                                        }
                                }
                                .addOnFailureListener {
                                    onComplete()
                                }
                        } else {
                            onComplete()
                        }
                    } else {
                        onComplete()
                    }
                } else {
                    onComplete()
                }
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun getSessionData(sessionId: String, onComplete: (SessionState?) -> Unit) {
        // 🔥 문서 이름이 아니라, 문서 안의 "sessionId" 필드값으로 검색!
        db.collection("sessions")
            .whereEqualTo("sessionId", sessionId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    // 검색된 첫 번째 문서(진짜 데이터)를 SessionState 객체로 변환
                    val sessionState = querySnapshot.documents[0].toObject(SessionState::class.java)
                    onComplete(sessionState)
                } else {
                    Log.e("JANJAN_BUG", "Status 화면: sessionId가 '$sessionId'인 문서를 못 찾음!")
                    onComplete(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "Status 데이터 불러오기 실패: ${e.message}")
                onComplete(null)
            }
    }
}