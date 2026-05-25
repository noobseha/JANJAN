package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.data.model.Session
import com.gachon.janjan.domain.session.model.OrderSummaryItem
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

class SessionRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    private val sessionsRef = db.collection(FirestorePaths.SESSIONS)

    suspend fun createSession(
        storeId: String,
        storeName: String,
        tableId: String,
        tableNumber: Int,
        inviteCode: String?
    ): String {
        val docRef = sessionsRef.document()
        val normalizedInviteCode = inviteCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        docRef.set(
            hashMapOf(
                "sessionId" to docRef.id,
                "storeId" to storeId,
                "storeName" to storeName,
                "tableId" to tableId,
                "tableNumber" to tableNumber,
                "inviteCode" to normalizedInviteCode,
                "status" to "active",
                "startedAt" to FieldValue.serverTimestamp(),
                "endedAt" to null,
                "totalSojuDrinkCount" to 0,
                "totalBeerDrinkCount" to 0
            )
        ).await()
        return docRef.id
    }

    suspend fun findByInviteCode(code: String): Session? {
        val normalized = code.trim().uppercase()
        if (normalized.length < 4) return null
        val snap = sessionsRef
            .whereEqualTo("inviteCode", normalized)
            .whereEqualTo("status", "active")
            .limit(1)
            .get()
            .await()
        return snap.documents.firstOrNull()?.toSessionModel()
    }

    suspend fun getSession(sessionId: String): Session? {
        val doc = sessionsRef.document(sessionId).get().await()
        if (doc.exists()) return doc.toSessionModel()

        val byField = sessionsRef
            .whereEqualTo("sessionId", sessionId)
            .limit(1)
            .get()
            .await()
        return byField.documents.firstOrNull()?.toSessionModel()
    }

    fun listenToSession(sessionId: String, onUpdate: (Session?) -> Unit): ListenerRegistration =
        sessionsRef.document(sessionId).addSnapshotListener { snap, _ ->
            onUpdate(snap?.toSessionModel())
        }

    suspend fun findLatestActiveSessionForUser(userId: String): Session? {
        val participations = db.collectionGroup(FirestorePaths.PARTICIPANTS)
            .whereEqualTo("userId", userId)
            .get()
            .await()

        val sessionRefs = participations.documents
            .mapNotNull { it.reference.parent.parent }
            .distinctBy { it.path }

        return sessionRefs
            .mapNotNull { it.get().await().toSessionModel() }
            .filter { it.status == "active" || it.status == "settling" }
            .maxByOrNull { it.startedAt }
    }

    suspend fun loadOrderSummaries(sessionId: String): List<OrderSummaryItem> {
        val orderDocs = db.collection(FirestorePaths.ORDERS)
            .whereEqualTo("sessionId", sessionId)
            .get()
            .await()

        val grouped = linkedMapOf<String, OrderSummaryItem>()
        orderDocs.documents.forEach { doc ->
            val rawItems = doc.get("items") as? List<*> ?: return@forEach
            rawItems.forEach { raw ->
                val item = raw as? Map<*, *> ?: return@forEach
                val name = item["itemName"].asString().ifBlank { item["name"].asString() }
                if (name.isBlank()) return@forEach
                val quantity = item["quantity"].asInt().coerceAtLeast(0)
                val amount = item["subtotal"].asInt().takeIf { it > 0 }
                    ?: item["amount"].asInt().takeIf { it > 0 }
                    ?: (item["unitPrice"].asInt() * quantity)
                val previous = grouped[name]
                grouped[name] = if (previous == null) {
                    OrderSummaryItem(name, quantity, amount)
                } else {
                    previous.copy(
                        quantity = previous.quantity + quantity,
                        amount = previous.amount + amount
                    )
                }
            }
        }
        return grouped.values.toList()
    }
}
