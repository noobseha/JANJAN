package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.SessionParticipant
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ParticipantRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    suspend fun joinSession(
        sessionId: String,
        userId: String,
        userName: String,
        imageUrl: String = ""
    ): String {
        val participantRef = db.collection(FirestorePaths.participants(sessionId)).document(userId)
        val existing = participantRef.get().await()
        if (existing.exists()) {
            participantRef.set(
                mapOf(
                    "userId" to userId,
                    "userName" to userName.ifBlank { "사용자" },
                    "imageUrl" to imageUrl
                ),
                SetOptions.merge()
            ).await()
        } else {
            participantRef.set(
                mapOf(
                    "userId" to userId,
                    "userName" to userName.ifBlank { "사용자" },
                    "imageUrl" to imageUrl,
                    "glassColor" to null,
                    "glassMappingType" to "color",
                    "glassMappingStatus" to "pending",
                    "sojuDrinkCount" to 0,
                    "beerDrinkCount" to 0,
                    "lastDrinkType" to null,
                    "lastDrinkAt" to null,
                    "foodTotal" to 0,
                    "sojuTotal" to 0,
                    "beerTotal" to 0,
                    "totalPrice" to 0,
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection(FirestorePaths.SESSIONS).document(sessionId)
                .set(
                    mapOf(
                        "participantCount" to FieldValue.increment(1),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
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
                    "glassMappingType" to "color",
                    "glassMappingStatus" to "pending"
                ),
                SetOptions.merge()
            )
            .await()
    }

    fun listenParticipants(sessionId: String): Flow<List<SessionParticipant>> = callbackFlow {
        val ref = db.collection(FirestorePaths.participants(sessionId))
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { it.toObject(SessionParticipant::class.java) }
                trySend(list)
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun updateParticipantTotals(
        sessionId: String,
        userId: String,
        foodTotal: Int,
        sojuTotal: Int,
        beerTotal: Int,
        totalPrice: Int
    ) {
        db.collection(FirestorePaths.participants(sessionId)).document(userId)
            .set(
                mapOf(
                    "foodTotal" to foodTotal,
                    "sojuTotal" to sojuTotal,
                    "beerTotal" to beerTotal,
                    "totalPrice" to totalPrice
                ),
                SetOptions.merge()
            ).await()
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
