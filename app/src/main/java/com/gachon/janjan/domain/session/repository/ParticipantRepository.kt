package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.SessionParticipant
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class ParticipantRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    suspend fun joinSession(
        sessionId: String,
        userId: String,
        userName: String
    ): String {
        val participantRef = db.collection(FirestorePaths.participants(sessionId)).document(userId)
        val existing = participantRef.get().await()
        if (existing.exists()) {
            participantRef.set(
                mapOf(
                    "userId" to userId,
                    "userName" to userName.ifBlank { "사용자" }
                ),
                SetOptions.merge()
            ).await()
        } else {
            participantRef.set(
                mapOf(
                    "userId" to userId,
                    "userName" to userName.ifBlank { "사용자" },
                    "glassColor" to null,
                    "glassMappingType" to "color",
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        return participantRef.id
    }

    suspend fun updateGlassColor(
        sessionId: String,
        userId: String,
        glassColor: String
    ) {
        db.collection(FirestorePaths.participants(sessionId)).document(userId)
            .set(
                mapOf(
                    "userId" to userId,
                    "glassColor" to glassColor,
                    "glassMappingType" to "color"
                ),
                SetOptions.merge()
            )
            .await()
    }

    fun listenParticipants(
        sessionId: String,
        onUpdate: (List<SessionParticipant>) -> Unit
    ): ListenerRegistration =
        db.collection(FirestorePaths.participants(sessionId))
            .addSnapshotListener { snap, _ ->
                onUpdate(snap?.toObjects(SessionParticipant::class.java).orEmpty())
            }
}
