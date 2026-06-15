package com.gachon.janjan.data.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AccountDeletionRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    suspend fun deletePersonalData(userId: String) {
        require(userId.isNotBlank()) { "사용자 정보를 확인하지 못했습니다." }

        val deletedUserId = anonymousId("deleted_user")
        anonymizePersonalSessionData(userId, deletedUserId)

        updateDocuments(
            db.collection(FirestorePaths.ORDERS)
                .whereEqualTo("userId", userId),
            mapOf("userId" to deletedUserId)
        )
        anonymizeSettlementItems(userId, deletedUserId)
        anonymizeSettlementParticipants(userId, deletedUserId)
        deleteDocuments(
            db.collection(FirestorePaths.FRIENDSHIPS)
                .whereArrayContains("memberIds", userId)
        )

        db.collection(FirestorePaths.USER_APP_SETTINGS).document(userId).delete().await()
        db.collection(FirestorePaths.USERS).document(userId).delete().await()
    }

    suspend fun deleteBusinessData(storeId: String) {
        require(storeId.isNotBlank()) { "매장 정보를 확인하지 못했습니다." }

        val deletedStoreId = anonymousId("deleted_store")
        val storeRef = db.collection(FirestorePaths.STORES).document(storeId)
        val storeDoc = storeRef.get().await()
        val menuDocs = storeRef.collection(MENU_ITEMS).get().await().documents

        deleteBusinessImages(
            storeId = storeId,
            storeImageUrl = storeDoc.getString("imageUrl"),
            menuImageUrls = menuDocs.mapNotNull { it.getString("imageUrl") }
        )

        val sessionDocs = db.collection(FirestorePaths.SESSIONS)
            .whereEqualTo("storeId", storeId)
            .get()
            .await()
            .documents

        sessionDocs.forEach { sessionDoc ->
            sessionDoc.reference.update(
                mapOf(
                    "storeId" to deletedStoreId,
                    "storeName" to DELETED_STORE_NAME
                )
            ).await()
            deleteCollection(sessionDoc.reference.collection(FirestorePaths.CAMERA_MAPPINGS))
            deleteCollection(sessionDoc.reference.collection(FirestorePaths.CV_FRAMES))
            deleteCollection(sessionDoc.reference.collection(FirestorePaths.CV_PAIR_STATES))
            deleteCollection(sessionDoc.reference.collection(FirestorePaths.DETECTION_EVENTS))
            anonymizeSettlementStore(
                sessionDoc.getString("sessionId").orEmpty().ifBlank { sessionDoc.id }
            )
        }

        updateDocuments(
            db.collection(FirestorePaths.ORDERS)
                .whereEqualTo("storeId", storeId),
            mapOf("storeId" to deletedStoreId)
        )
        deleteDocuments(
            db.collection(FirestorePaths.CAMERA_DEVICES)
                .whereEqualTo("storeId", storeId)
        )

        KNOWN_STORE_SUBCOLLECTIONS.forEach { collection ->
            deleteCollection(storeRef.collection(collection))
        }
        storeRef.delete().await()
    }

    private suspend fun anonymizePersonalSessionData(
        userId: String,
        deletedUserId: String
    ) {
        val sessions = db.collection(FirestorePaths.SESSIONS)
            .get()
            .await()
            .documents

        sessions.forEach { session ->
            session.reference.collection(FirestorePaths.PARTICIPANTS)
                .get()
                .await()
                .documents
                .filter { it.id == userId || it.getString("userId") == userId }
                .forEach { participant ->
                    val anonymizedData = participant.data.orEmpty().toMutableMap().apply {
                        this["userId"] = deletedUserId
                        this["userName"] = DELETED_ACCOUNT_NAME
                    }
                    participant.reference.parent.document(deletedUserId).set(anonymizedData).await()
                    participant.reference.delete().await()
                }

            session.reference.collection(FirestorePaths.GLASS_MAPPINGS)
                .get()
                .await()
                .documents
                .filter { it.getString("userId") == userId }
                .forEach { mapping ->
                    mapping.reference.update("userId", deletedUserId).await()
                }
        }
    }

    private suspend fun anonymizeSettlementItems(
        userId: String,
        deletedUserId: String
    ) {
        db.collection(FirestorePaths.SETTLEMENTS)
            .get()
            .await()
            .documents
            .forEach { settlement ->
                settlement.reference.collection(FirestorePaths.SETTLEMENT_ITEMS)
                    .get()
                    .await()
                    .documents
                    .filter { it.getString("userId") == userId }
                    .forEach { item ->
                        item.reference.update(
                            mapOf(
                                "userId" to deletedUserId,
                                "userName" to DELETED_ACCOUNT_NAME
                            )
                        ).await()
                    }
            }
    }

    private suspend fun anonymizeSettlementParticipants(
        userId: String,
        deletedUserId: String
    ) {
        db.collection(FirestorePaths.SETTLEMENTS)
            .get()
            .await()
            .documents
            .forEach { settlement ->
                val rawParticipants = settlement.get("participants") as? List<*> ?: return@forEach
                var changed = false
                val anonymizedParticipants = rawParticipants.map { rawParticipant ->
                    val participant = rawParticipant as? Map<*, *> ?: return@map rawParticipant
                    if (participant["userId"] != userId) return@map rawParticipant

                    changed = true
                    participant.entries.associate { (key, value) -> key.toString() to value } +
                        mapOf(
                            "userId" to deletedUserId,
                            "userName" to DELETED_ACCOUNT_NAME
                        )
                }
                if (changed) {
                    settlement.reference.update("participants", anonymizedParticipants).await()
                }
            }
    }

    private suspend fun anonymizeSettlementStore(sessionId: String) {
        val refs = linkedSetOf<DocumentReference>()
        val direct = db.collection(FirestorePaths.SETTLEMENTS).document(sessionId).get().await()
        if (direct.exists()) refs += direct.reference
        db.collection(FirestorePaths.SETTLEMENTS)
            .whereEqualTo("sessionId", sessionId)
            .get()
            .await()
            .documents
            .forEach { refs += it.reference }

        refs.forEach { ref ->
            ref.update("storeName", DELETED_STORE_NAME).await()
        }
    }

    private suspend fun deleteBusinessImages(
        storeId: String,
        storeImageUrl: String?,
        menuImageUrls: List<String>
    ) {
        deleteStorageObjectIfExists(storage.reference.child("store_images/$storeId.jpg"))
        (menuImageUrls + listOfNotNull(storeImageUrl))
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { url ->
                runCatching { storage.getReferenceFromUrl(url) }
                    .getOrNull()
                    ?.let { deleteStorageObjectIfExists(it) }
            }
    }

    private suspend fun deleteStorageObjectIfExists(ref: com.google.firebase.storage.StorageReference) {
        try {
            ref.delete().await()
        } catch (error: StorageException) {
            if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error
        }
    }

    private suspend fun updateDocuments(query: Query, updates: Map<String, Any>) {
        query.get().await().documents.forEach { document ->
            document.reference.update(updates).await()
        }
    }

    private suspend fun deleteDocuments(query: Query) {
        query.get().await().documents.forEach { document ->
            document.reference.delete().await()
        }
    }

    private suspend fun deleteCollection(collection: com.google.firebase.firestore.CollectionReference) {
        collection.get().await().documents.forEach { document ->
            document.reference.delete().await()
        }
    }

    private fun anonymousId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"

    companion object {
        private const val MENU_ITEMS = "menuItems"
        private const val NOTIFICATIONS = "notifications"
        private const val STORE_SESSIONS = "sessions"
        private val KNOWN_STORE_SUBCOLLECTIONS = listOf(
            FirestorePaths.TABLES,
            FirestorePaths.TABLE_CAMERA_MAPPINGS,
            MENU_ITEMS,
            NOTIFICATIONS,
            STORE_SESSIONS
        )

        const val DELETED_ACCOUNT_NAME = "탈퇴한 계정"
        const val DELETED_STORE_NAME = "탈퇴한 매장"
    }
}
