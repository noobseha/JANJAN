package com.gachon.janjan.domain.camera.repository

import com.gachon.janjan.domain.camera.model.CameraDeviceConfig
import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class CameraDeviceRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    suspend fun registerDevice(config: CameraDeviceConfig) {
        require(config.deviceId.isNotBlank()) { "카메라 장치 ID가 없습니다." }
        require(config.storeId.isNotBlank()) { "매장 ID를 입력해주세요." }
        require(config.tableId.isNotBlank()) { "테이블 ID를 입력해주세요." }

        val now = FieldValue.serverTimestamp()
        val devicePayload = mapOf(
            "cameraDeviceId" to config.deviceId,
            "storeId" to config.storeId,
            "tableId" to config.tableId,
            "tableNumber" to config.tableNumber,
            "cameraName" to cameraName(config.tableNumber),
            "cameraSource" to "androidDevice",
            "cameraEnabled" to true,
            "cameraStatus" to STATUS_WAITING,
            "assignedSessionId" to "",
            "lastSeenAt" to now,
            "updatedAt" to now
        )
        db.collection(FirestorePaths.CAMERA_DEVICES).document(config.deviceId)
            .set(devicePayload, SetOptions.merge())
            .await()

        db.collection(FirestorePaths.STORES).document(config.storeId)
            .collection(FirestorePaths.TABLE_CAMERA_MAPPINGS).document(config.tableId)
            .set(
                devicePayload + mapOf(
                    "sessionId" to "",
                    "cameraIp" to "",
                    "cameraStreamUrl" to ""
                ),
                SetOptions.merge()
            )
            .await()
    }

    fun listenTableSession(
        config: CameraDeviceConfig,
        onSessionChanged: (String) -> Unit
    ): ListenerRegistration {
        return db.collection(FirestorePaths.STORES).document(config.storeId)
            .collection(FirestorePaths.TABLES).document(config.tableId)
            .addSnapshotListener { snapshot, _ ->
                onSessionChanged(snapshot?.getString("activeSessionId").orEmpty())
            }
    }

    suspend fun updateDeviceStatus(
        config: CameraDeviceConfig,
        status: String,
        activeSessionId: String
    ) {
        if (config.deviceId.isBlank()) return
        val statusPayload = mapOf(
            "cameraStatus" to status,
            "assignedSessionId" to activeSessionId,
            "sessionId" to activeSessionId,
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection(FirestorePaths.CAMERA_DEVICES).document(config.deviceId)
            .set(statusPayload, SetOptions.merge())
            .await()

        if (config.storeId.isBlank() || config.tableId.isBlank()) return
        db.collection(FirestorePaths.STORES).document(config.storeId)
            .collection(FirestorePaths.TABLE_CAMERA_MAPPINGS).document(config.tableId)
            .set(
                statusPayload,
                SetOptions.merge()
            )
            .await()
    }

    companion object {
        const val STATUS_WAITING = "waiting"
        const val STATUS_RECOGNIZING = "recognizing"

        private fun cameraName(tableNumber: Int): String =
            if (tableNumber > 0) {
                "${tableNumber}번 테이블 JanJan Camera"
            } else {
                "JanJan Camera"
            }
    }
}
