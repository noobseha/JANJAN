package com.gachon.janjan.data.repository

import android.util.Log
import com.gachon.janjan.data.model.Participant
import com.gachon.janjan.data.model.RecentSession
import com.gachon.janjan.data.model.SessionState
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusRepository {
    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)

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
        db.collection("sessions").document(sessionId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    buildSessionState(document, onComplete)
                } else {
                    db.collection("sessions")
                        .whereEqualTo("sessionId", sessionId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            val sessionDoc = querySnapshot.documents.firstOrNull()
                            if (sessionDoc == null) {
                                Log.e("JANJAN_BUG", "Status 화면: sessionId가 '$sessionId'인 문서를 못 찾음!")
                                onComplete(null)
                            } else {
                                buildSessionState(sessionDoc, onComplete)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("JANJAN_BUG", "Status 데이터 불러오기 실패: ${e.message}")
                            onComplete(null)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "Status 데이터 불러오기 실패: ${e.message}")
                onComplete(null)
            }
    }

    fun startSettlement(sessionId: String, onComplete: (Boolean) -> Unit) {
        if (sessionId.isBlank()) {
            onComplete(false)
            return
        }

        db.collection("sessions").document(sessionId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    updateSettlementStatus(document, onComplete)
                } else {
                    db.collection("sessions")
                        .whereEqualTo("sessionId", sessionId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            val sessionDoc = querySnapshot.documents.firstOrNull()
                            if (sessionDoc == null) {
                                Log.e("JANJAN_BUG", "정산 시작 실패: sessionId가 '$sessionId'인 문서를 못 찾음")
                                onComplete(false)
                            } else {
                                updateSettlementStatus(sessionDoc, onComplete)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("JANJAN_BUG", "정산 세션 쿼리 실패: ${e.message}")
                            onComplete(false)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "정산 세션 조회 실패: ${e.message}")
                onComplete(false)
            }
    }

    private fun buildSessionState(
        sessionDoc: DocumentSnapshot,
        onComplete: (SessionState?) -> Unit
    ) {
        val sessionRef = sessionDoc.reference
        sessionRef.collection("participants").get()
            .addOnSuccessListener { participantSnapshot ->
                sessionRef.collection("glassMappings").get()
                    .addOnSuccessListener { mappingSnapshot ->
                        val mappings = mappingSnapshot.documents
                        val participants = participantSnapshot.documents.map { participantDoc ->
                            val userId = participantDoc.getStringValue("userId")
                            val sojuCount = mappings
                                .filter { it.getString("userId") == userId && it.getString("drinkType") == "soju" }
                                .sumOf { it.getIntValue("drinkCount") }
                            val beerCount = mappings
                                .filter { it.getString("userId") == userId && it.getString("drinkType") == "beer" }
                                .sumOf { it.getIntValue("drinkCount") }

                            Participant(
                                userId = userId,
                                userName = participantDoc.getString("userName").orEmpty().ifBlank { "사용자" },
                                glassColor = participantDoc.getString("glassColor"),
                                sojuCount = sojuCount,
                                beerCount = beerCount,
                                joinedAt = when (val joinedAt = participantDoc.get("joinedAt")) {
                                    is Timestamp -> joinedAt.seconds
                                    is Number -> joinedAt.toLong()
                                    else -> 0L
                                }
                            )
                        }

                        val tableId = sessionDoc.getStringValue("tableId")
                        val tableNumber = sessionDoc.getIntValue("tableNumber").takeIf { it > 0 }
                            ?: tableId.toIntOrNull()
                            ?: 0
                        val totalSojuCount = mappings
                            .filter { it.getString("drinkType") == "soju" }
                            .sumOf { it.getIntValue("drinkCount") }
                        val totalBeerCount = mappings
                            .filter { it.getString("drinkType") == "beer" }
                            .sumOf { it.getIntValue("drinkCount") }

                        onComplete(
                            SessionState(
                                sessionId = sessionDoc.getString("sessionId").orEmpty().ifBlank { sessionDoc.id },
                                storeName = sessionDoc.getString("storeName").orEmpty().ifBlank { "알 수 없는 가게" },
                                tableId = tableNumber,
                                tableNumber = tableNumber,
                                totalFoodPrice = sessionDoc.getIntValue("totalFoodPrice"),
                                totalSojuPrice = sessionDoc.getIntValue("totalSojuPrice"),
                                totalBeerPrice = sessionDoc.getIntValue("totalBeerPrice"),
                                totalSojuCount = totalSojuCount,
                                totalBeerCount = totalBeerCount,
                                participants = participants
                            )
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.e("JANJAN_BUG", "Status 술잔 매핑 조회 실패: ${e.message}")
                        onComplete(null)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "Status 참가자 조회 실패: ${e.message}")
                onComplete(null)
            }
    }

    private fun updateSettlementStatus(
        sessionDoc: DocumentSnapshot,
        onComplete: (Boolean) -> Unit
    ) {
        sessionDoc.reference.update(
            mapOf(
                "status" to "settling",
                "endedAt" to FieldValue.serverTimestamp()
            )
        )
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "정산 상태 업데이트 실패: ${e.message}")
                onComplete(false)
            }
    }

    fun getRecentSession(
        userId: String,
        currentSessionId: String,
        onComplete: (RecentSession?) -> Unit
    ) {
        if (userId.isBlank()) {
            onComplete(null)
            return
        }

        db.collectionGroup("participants")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { participations ->
                val sessionRefs = participations.documents
                    .mapNotNull { it.reference.parent.parent }
                    .distinctBy { it.path }
                    .filter { it.id != currentSessionId }

                if (sessionRefs.isEmpty()) {
                    onComplete(null)
                    return@addOnSuccessListener
                }

                Tasks.whenAllSuccess<DocumentSnapshot>(sessionRefs.map { it.get() })
                    .addOnSuccessListener { sessionDocs ->
                        val latest = sessionDocs
                            .filter { it.exists() }
                            .filter { doc ->
                                doc.getString("sessionId").orEmpty().ifBlank { doc.id } != currentSessionId
                            }
                            .maxByOrNull { it.timestampMillis("startedAt") }

                        if (latest == null) {
                            onComplete(null)
                            return@addOnSuccessListener
                        }

                        latest.reference.collection("participants")
                            .get()
                            .addOnSuccessListener { participants ->
                                val totalPrice = latest.getIntValue("totalPrice").takeIf { it > 0 }
                                    ?: latest.getIntValue("totalFoodPrice") +
                                    latest.getIntValue("totalSojuPrice") +
                                    latest.getIntValue("totalBeerPrice")

                                onComplete(
                                    RecentSession(
                                        sessionId = latest.getString("sessionId").orEmpty().ifBlank { latest.id },
                                        storeName = latest.getString("storeName").orEmpty()
                                            .ifBlank { "알 수 없는 가게" },
                                        date = latest.dateLabel("startedAt"),
                                        headCount = participants.size(),
                                        totalPrice = totalPrice
                                    )
                                )
                            }
                            .addOnFailureListener { e ->
                                Log.e("JANJAN_BUG", "최근 술자리 참가자 조회 실패: ${e.message}")
                                onComplete(null)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("JANJAN_BUG", "최근 술자리 세션 조회 실패: ${e.message}")
                        onComplete(null)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "최근 술자리 조회 실패: ${e.message}")
                onComplete(null)
            }
    }

    private fun DocumentSnapshot.getStringValue(field: String): String =
        when (val value = get(field)) {
            is String -> value
            is Number -> value.toLong().toString()
            else -> ""
        }

    private fun DocumentSnapshot.getIntValue(field: String): Int =
        when (val value = get(field)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }

    private fun DocumentSnapshot.timestampMillis(field: String): Long =
        when (val value = get(field)) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> 0L
        }

    private fun DocumentSnapshot.dateLabel(field: String): String {
        val millis = timestampMillis(field).takeIf { it > 0 } ?: System.currentTimeMillis()
        return dateFormat.format(Date(millis))
    }
}
