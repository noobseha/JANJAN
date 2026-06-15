package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.TableInviteCodes
import com.gachon.janjan.MenuCategories
import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.data.model.Session
import com.gachon.janjan.domain.session.model.OrderSummaryItem
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

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
            newSessionPayload(
                sessionId = docRef.id,
                storeId = storeId,
                storeName = storeName,
                tableId = tableId,
                tableNumber = tableNumber,
                inviteCode = normalizedInviteCode.orEmpty()
            )
        ).await()
        return docRef.id
    }

    suspend fun findByInviteCode(code: String): Session? {
        val rawCode = code.normalizeInviteCode()
        val normalized = rawCode.uppercase(Locale.US)
        if (normalized.length < 4) return null
        val codeCandidates = listOf(normalized, rawCode).distinct()

        findSessionsByInviteCode(codeCandidates).pickJoinableSession()?.let {
            return it
        }

        findSessionFromCameraDevices(codeCandidates, normalized)?.let {
            return it
        }

        findSessionFromTableCameraMappings(codeCandidates, normalized)?.let {
            return it
        }

        findSessionFromTableDocuments(codeCandidates, normalized)?.let {
            return it
        }

        findFallbackSessionByInviteCode(normalized)?.let {
            return it
        }

        return null
    }

    private suspend fun findSessionsByInviteCode(codeCandidates: List<String>): List<Session> {
        val sessions = mutableListOf<Session>()
        codeCandidates.forEach { candidate ->
            sessions += sessionsRef
                .whereEqualTo("inviteCode", candidate)
                .limit(10)
                .get()
                .await()
                .documents
                .mapNotNull { it.toSessionModel() }
        }
        return sessions.distinctBy { it.sessionId }
    }

    private suspend fun findFallbackSessionByInviteCode(normalized: String): Session? {
        val fallbackSessions = sessionsRef
            .limit(200)
            .get()
            .await()
            .documents
            .mapNotNull { it.toSessionModel() }
            .filter { it.inviteCode.normalizeInviteCode().uppercase(Locale.US) == normalized }

        return fallbackSessions.pickJoinableSession()
    }

    private suspend fun findSessionFromCameraDevices(
        codeCandidates: List<String>,
        normalized: String
    ): Session? {
        val exactMatches = runCatching {
            val docs = mutableListOf<DocumentSnapshot>()
            codeCandidates.forEach { candidate ->
                docs += db.collection(FirestorePaths.CAMERA_DEVICES)
                    .whereEqualTo("inviteCode", candidate)
                    .limit(10)
                    .get()
                    .await()
                    .documents
            }
            docs
        }.getOrDefault(emptyList())

        resolveSessionFromInviteDocuments(exactMatches, normalized)?.let {
            return it
        }

        val fallbackMatches = runCatching {
            db.collection(FirestorePaths.CAMERA_DEVICES)
                .limit(200)
                .get()
                .await()
                .documents
                .filter { it.normalizedInviteCode() == normalized }
        }.getOrDefault(emptyList())

        return resolveSessionFromInviteDocuments(fallbackMatches, normalized)
    }

    private suspend fun findSessionFromTableCameraMappings(
        codeCandidates: List<String>,
        normalized: String
    ): Session? {
        val exactMatches = runCatching {
            val docs = mutableListOf<DocumentSnapshot>()
            codeCandidates.forEach { candidate ->
                docs += db.collectionGroup(FirestorePaths.TABLE_CAMERA_MAPPINGS)
                    .whereEqualTo("inviteCode", candidate)
                    .limit(10)
                    .get()
                    .await()
                    .documents
            }
            docs
        }.getOrDefault(emptyList())

        resolveSessionFromInviteDocuments(exactMatches, normalized)?.let {
            return it
        }

        val fallbackMatches = runCatching {
            db.collectionGroup(FirestorePaths.TABLE_CAMERA_MAPPINGS)
                .limit(200)
                .get()
                .await()
                .documents
                .filter { it.normalizedInviteCode() == normalized }
        }.getOrDefault(emptyList())

        return resolveSessionFromInviteDocuments(fallbackMatches, normalized)
    }

    private suspend fun findSessionFromTableDocuments(
        codeCandidates: List<String>,
        normalized: String
    ): Session? {
        val exactMatches = runCatching {
            val docs = mutableListOf<DocumentSnapshot>()
            codeCandidates.forEach { candidate ->
                docs += db.collectionGroup(FirestorePaths.TABLES)
                    .whereEqualTo("inviteCode", candidate)
                    .limit(10)
                    .get()
                    .await()
                    .documents
            }
            docs
        }.getOrDefault(emptyList())

        resolveSessionFromInviteDocuments(exactMatches, normalized)?.let {
            return it
        }

        val fallbackMatches = runCatching {
            db.collectionGroup(FirestorePaths.TABLES)
                .limit(200)
                .get()
                .await()
                .documents
                .filter { it.normalizedInviteCode() == normalized }
        }.getOrDefault(emptyList())

        return resolveSessionFromInviteDocuments(fallbackMatches, normalized)
    }

    private suspend fun resolveSessionFromInviteDocuments(
        docs: List<DocumentSnapshot>,
        normalized: String
    ): Session? {
        docs.distinctBy { it.reference.path }.forEach { doc ->
            materializeSessionFromInviteDocument(doc, normalized)?.takeIf { it.isJoinable() }?.let {
                return it
            }
        }
        return null
    }

    private suspend fun materializeSessionFromInviteDocument(
        doc: DocumentSnapshot,
        normalized: String
    ): Session? {
        val inviteCode = doc.normalizedInviteCode().ifBlank { normalized }
        val rawTableId = doc.stringValue("tableId")
            ?: doc.stringValue("assignedTableId")
            ?: ""
        val tableNumber = doc.intValue("tableNumber").takeIf { it > 0 }
            ?: rawTableId.filter { it.isDigit() }.toIntOrNull()
            ?: 0
        val tableId = rawTableId.ifBlank {
            tableNumber.takeIf { it > 0 }?.let { "table_$it" }.orEmpty()
        }
        val storeId = doc.stringValue("storeId")
            ?: doc.reference.parent.parent?.id
            ?: doc.stringValue("ownerUserId")
            ?: ""
        val storeName = doc.stringValue("storeName")
            ?: doc.stringValue("name")
            ?: ""

        val candidateSessionIds = listOfNotNull(
            doc.stringValue("activeSessionId"),
            doc.stringValue("sessionId"),
            doc.stringValue("assignedSessionId")
        ).filter { it.isNotBlank() }.distinct()

        candidateSessionIds.firstNotNullOfOrNull { candidateId ->
            getSession(candidateId)?.takeIf { it.isJoinable() }
        }?.let { activeSession ->
            val sessionRef = sessionsRef.document(activeSession.sessionId)
            sessionRef.set(
                mapOf(
                    "sessionId" to activeSession.sessionId,
                    "storeId" to activeSession.storeId.ifBlank { storeId },
                    "storeName" to activeSession.storeName.ifBlank { storeName },
                    "tableId" to activeSession.tableId.ifBlank { tableId },
                    "tableNumber" to (activeSession.tableNumber.takeIf { it > 0 } ?: tableNumber),
                    "inviteCode" to activeSession.inviteCode.ifBlank { inviteCode },
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            val refreshed = sessionRef.get().await().toSessionModel() ?: activeSession
            writeTableActiveSession(
                storeId = refreshed.storeId.ifBlank { storeId },
                storeName = refreshed.storeName.ifBlank { storeName },
                tableId = refreshed.tableId.ifBlank { tableId },
                tableNumber = refreshed.tableNumber.takeIf { it > 0 } ?: tableNumber,
                sessionId = refreshed.sessionId,
                inviteCode = refreshed.inviteCode.ifBlank { inviteCode }
            )
            return refreshed
        }

        val sessionRef = sessionsRef.document()
        val sessionId = sessionRef.id
        sessionRef.set(
            newSessionPayload(
                sessionId = sessionId,
                storeId = storeId,
                storeName = storeName,
                tableId = tableId,
                tableNumber = tableNumber,
                inviteCode = inviteCode
            )
        ).await()

        writeTableActiveSession(
            storeId = storeId,
            storeName = storeName,
            tableId = tableId,
            tableNumber = tableNumber,
            sessionId = sessionId,
            inviteCode = inviteCode
        )

        return sessionRef.get().await().toSessionModel()
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

    suspend fun syncTableActiveSession(session: Session) {
        val storeId = session.storeId.ifBlank { return }
        val tableId = session.tableId.ifBlank {
            session.tableNumber.takeIf { it > 0 }?.let { "table_$it" } ?: return
        }
        writeTableActiveSession(
            storeId = storeId,
            storeName = session.storeName,
            tableId = tableId,
            tableNumber = session.tableNumber,
            sessionId = session.sessionId,
            inviteCode = session.inviteCode
        )
    }

    private suspend fun writeTableActiveSession(
        storeId: String,
        storeName: String,
        tableId: String,
        tableNumber: Int,
        sessionId: String,
        inviteCode: String
    ) {
        if (storeId.isBlank() || tableId.isBlank()) return
        db.collection(FirestorePaths.STORES).document(storeId)
            .collection(FirestorePaths.TABLES).document(tableId)
            .set(
                mapOf(
                    "storeId" to storeId,
                    "storeName" to storeName,
                    "tableId" to tableId,
                    "tableNumber" to tableNumber,
                    "activeSessionId" to sessionId,
                    "inviteCode" to inviteCode,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    private fun newSessionPayload(
        sessionId: String,
        storeId: String,
        storeName: String,
        tableId: String,
        tableNumber: Int,
        inviteCode: String
    ): Map<String, Any?> = mapOf(
        "sessionId" to sessionId,
        "storeId" to storeId,
        "storeName" to storeName,
        "tableId" to tableId,
        "tableNumber" to tableNumber,
        "inviteCode" to TableInviteCodes.normalize(inviteCode),
        "status" to "active",
        "startedAt" to FieldValue.serverTimestamp(),
        "endedAt" to null,
        "participantCount" to 0,
        "totalPrice" to 0,
        "totalSojuPrice" to 0,
        "totalBeerPrice" to 0,
        "totalFoodPrice" to 0,
        "totalSojuDrinkCount" to 0,
        "totalBeerDrinkCount" to 0,
        "orderCount" to 0,
        "lastOrderAt" to null,
        "updatedAt" to FieldValue.serverTimestamp()
    )

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

    suspend fun loadOrderSummaries(sessionId: String, storeId: String? = null): List<OrderSummaryItem> {
        val orderDocs = db.collection(FirestorePaths.ORDERS)
            .whereEqualTo("sessionId", sessionId)
            .get()
            .await()

        return buildOrderSummaries(orderDocs.documents, loadCategoryMap(storeId))
    }

    fun listenOrderSummaries(
        sessionId: String,
        onUpdate: (List<OrderSummaryItem>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration =
        db.collection(FirestorePaths.ORDERS)
            .whereEqualTo("sessionId", sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                onUpdate(buildOrderSummaries(snapshot?.documents.orEmpty(), emptyMap()))
            }

    private suspend fun loadCategoryMap(storeId: String?): Map<String, String> {
        if (storeId.isNullOrBlank()) return emptyMap()
        val menuItemsDocs = db.collection(FirestorePaths.STORES).document(storeId)
            .collection("menuItems").get().await()
        return menuItemsDocs.documents.mapNotNull { doc ->
            val name = doc.getString("name").orEmpty()
            val category = MenuCategories.normalize(doc.getString("category").orEmpty())
            if (name.isBlank()) null else name to category
        }.toMap()
    }

    private fun buildOrderSummaries(
        orderDocs: List<DocumentSnapshot>,
        categoryMap: Map<String, String>
    ): List<OrderSummaryItem> {
        val grouped = linkedMapOf<String, OrderSummaryItem>()
        orderDocs.forEach { doc ->
            val rawItems = doc.get("items") as? List<*> ?: return@forEach
            rawItems.forEach { raw ->
                val item = raw as? Map<*, *> ?: return@forEach
                val name = item["itemName"].asString().ifBlank { item["name"].asString() }
                if (name.isBlank()) return@forEach
                val quantity = item["quantity"].asInt().coerceAtLeast(0)
                val amount = item["subtotal"].asInt().takeIf { it > 0 }
                    ?: item["amount"].asInt().takeIf { it > 0 }
                    ?: (item["unitPrice"].asInt() * quantity)
                val category = MenuCategories.normalize(item["category"].asString())
                    .ifBlank { categoryMap[name] ?: "" }
                val previous = grouped[name]
                grouped[name] = if (previous == null) {
                    OrderSummaryItem(name, quantity, amount, category)
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

    private fun String.normalizeInviteCode(): String =
        TableInviteCodes.normalize(this)

    private fun DocumentSnapshot.normalizedInviteCode(): String =
        stringValue("inviteCode").orEmpty()
            .normalizeInviteCode()
            .uppercase(Locale.US)

    private fun List<Session>.pickJoinableSession(): Session? =
        firstOrNull { it.status == "active" || it.status == "settling" }
            ?: firstOrNull { it.isJoinable() }

    private fun Session.isJoinable(): Boolean =
        status.isBlank() || status == "active" || status == "settling"
}
