package com.gachon.janjan.domain.owner.repository

import com.gachon.janjan.TableInviteCodes
import com.gachon.janjan.domain.owner.model.BusinessTable
import com.gachon.janjan.domain.owner.model.TableCameraMapping
import com.gachon.janjan.domain.session.FirebaseConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

class BusinessCameraRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    fun listenTables(
        storeId: String,
        storeName: String,
        onUpdate: (List<BusinessTable>) -> Unit
    ): ListenerRegistration =
        db.collection("stores")
            .document(storeId)
            .collection("tables")
            .addSnapshotListener { snap, _ ->
                val tables = snap?.documents
                    ?.map { doc ->
                        BusinessTable(
                            tableId = doc.getString("tableId").orEmpty().ifBlank { doc.id },
                            tableNumber = doc.getLong("tableNumber")?.toInt()
                                ?: doc.getString("tableNumber")?.toIntOrNull()
                                ?: doc.id.filter { it.isDigit() }.toIntOrNull()
                                ?: 0,
                            label = doc.getString("label").orEmpty(),
                            storeId = storeId,
                            storeName = doc.getString("storeName").orEmpty().ifBlank { storeName },
                            activeSessionId = doc.getString("activeSessionId").orEmpty()
                        )
                    }
                    ?.sortedWith(compareBy<BusinessTable> { it.tableNumber }.thenBy { it.tableId })
                    .orEmpty()
                onUpdate(tables.ifEmpty { defaultTables(storeId, storeName) })
            }

    fun listenCameraMappings(
        storeId: String,
        onUpdate: (List<TableCameraMapping>) -> Unit
    ): ListenerRegistration =
        db.collection("stores")
            .document(storeId)
            .collection("tableCameraMappings")
            .addSnapshotListener { snap, _ ->
                onUpdate(snap?.toObjects(TableCameraMapping::class.java).orEmpty())
            }

    suspend fun saveCameraMapping(
        storeId: String,
        storeName: String,
        table: BusinessTable,
        cameraName: String,
        cameraIp: String,
        cameraStreamUrl: String,
        ownerUserId: String
    ): String {
        val normalizedIp = cameraIp.trim()
        val normalizedStreamUrl = cameraStreamUrl.trim().ifBlank {
            normalizedIp.toDefaultStreamUrl()
        }
        val normalizedCameraName = cameraName.trim().ifBlank { "${table.displayName} 카메라" }
        val activeSession = ensureActiveSession(storeId, storeName, table)
        val sessionId = activeSession.sessionId
        val cameraDeviceId = "camera_${normalizedIp.replace(Regex("[^A-Za-z0-9]"), "_")}"
            .lowercase(Locale.US)
        val tableId = table.tableId.ifBlank { "table_${table.tableNumber}" }
        val tableNumber = table.tableNumber.takeIf { it > 0 }
            ?: tableId.filter { it.isDigit() }.toIntOrNull()
            ?: 0
        val now = FieldValue.serverTimestamp()
        val mappingPayload = mapOf(
            "storeId" to storeId,
            "storeName" to storeName,
            "tableId" to tableId,
            "tableNumber" to tableNumber,
            "sessionId" to sessionId,
            "inviteCode" to activeSession.inviteCode,
            "cameraDeviceId" to cameraDeviceId,
            "cameraName" to normalizedCameraName,
            "cameraIp" to normalizedIp,
            "cameraStreamUrl" to normalizedStreamUrl,
            "cameraStatus" to "activationRequested",
            "cameraEnabled" to true,
            "ownerUserId" to ownerUserId,
            "updatedAt" to now
        )

        val batch = db.batch()
        val storeRef = db.collection("stores").document(storeId)
        val tableRef = storeRef
            .collection("tables").document(tableId)
        val storeMappingRef = storeRef
            .collection("tableCameraMappings").document(tableId)
        val sessionMappingRef = db.collection("sessions").document(sessionId)
            .collection("cameraMappings").document(tableId)
        val cameraDeviceRef = db.collection("cameraDevices").document(cameraDeviceId)

        batch.set(
            storeRef,
            mapOf(
                "storeId" to storeId,
                "name" to storeName,
                "updatedAt" to now
            ),
            SetOptions.merge()
        )
        batch.set(
            tableRef,
            mapOf(
                "storeId" to storeId,
                "storeName" to storeName,
                "tableId" to tableId,
                "tableNumber" to tableNumber,
                "label" to table.displayName,
                "activeSessionId" to sessionId,
                "inviteCode" to activeSession.inviteCode,
                "updatedAt" to now
            ),
            SetOptions.merge()
        )
        batch.set(storeMappingRef, mappingPayload, SetOptions.merge())
        batch.set(sessionMappingRef, mappingPayload, SetOptions.merge())
        batch.set(
            cameraDeviceRef,
            mappingPayload + mapOf(
                "assignedSessionId" to sessionId,
                "assignedTableId" to tableId,
                "command" to "startCamera",
                "requestedAt" to now
            ),
            SetOptions.merge()
        )
        batch.commit().await()
        return sessionId
    }

    private suspend fun ensureActiveSession(
        storeId: String,
        storeName: String,
        table: BusinessTable
    ): ActiveSessionInfo {
        val tableId = table.tableId.ifBlank { "table_${table.tableNumber}" }
        val tableRef = db.collection("stores").document(storeId)
            .collection("tables").document(tableId)
        val tableInviteCode = tableRef.get().await().getString("inviteCode").orEmpty()
        val existing = db.collection("sessions")
            .whereEqualTo("storeId", storeId)
            .whereEqualTo("tableId", tableId)
            .whereEqualTo("status", "active")
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        if (existing != null) {
            val sessionInviteCode = tableInviteCode.ifBlank { existing.getString("inviteCode").orEmpty() }
            if (sessionInviteCode.isNotBlank()) {
                existing.reference.set(
                    mapOf(
                        "inviteCode" to sessionInviteCode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            }
            return ActiveSessionInfo(
                sessionId = existing.getString("sessionId").orEmpty().ifBlank { existing.id },
                inviteCode = sessionInviteCode
            )
        }

        val tableNumber = table.tableNumber.takeIf { it > 0 }
            ?: tableId.filter { it.isDigit() }.toIntOrNull()
            ?: 0
        val sessionRef = db.collection("sessions").document()
        val inviteCode = tableInviteCode.ifBlank { TableInviteCodes.generate() }
        sessionRef.set(
            mapOf(
                "sessionId" to sessionRef.id,
                "storeId" to storeId,
                "storeName" to storeName,
                "tableId" to tableId,
                "tableNumber" to tableNumber,
                "inviteCode" to inviteCode,
                "status" to "active",
                "startedAt" to FieldValue.serverTimestamp(),
                "endedAt" to null,
                "participantCount" to 0,
                "totalSojuPrice" to 0,
                "totalBeerPrice" to 0,
                "totalFoodPrice" to 0,
                "totalPrice" to 0,
                "totalSojuDrinkCount" to 0,
                "totalBeerDrinkCount" to 0,
                "orderCount" to 0,
                "lastOrderAt" to null,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return ActiveSessionInfo(sessionId = sessionRef.id, inviteCode = inviteCode)
    }

    private data class ActiveSessionInfo(
        val sessionId: String,
        val inviteCode: String
    )

    private fun defaultTables(storeId: String, storeName: String): List<BusinessTable> =
        (1..3).map { number ->
            BusinessTable(
                tableId = "table_$number",
                tableNumber = number,
                label = "${number}번 테이블",
                storeId = storeId,
                storeName = storeName
            )
        }

    private fun String.toDefaultStreamUrl(): String =
        if (startsWith("http://") || startsWith("https://")) {
            this
        } else {
            "http://$this:8080/video"
        }

    private fun buildInviteCode(tableNumber: Int): String {
        val suffix = System.currentTimeMillis().toString(36).takeLast(4).uppercase(Locale.US)
        val prefix = if (tableNumber > 0) "T$tableNumber" else "TB"
        return (prefix + suffix).takeLast(6).uppercase(Locale.US)
    }
}
