package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.GlassUserMapping
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.util.Locale

class GlassMappingRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    suspend fun createPendingColorMapping(
        sessionId: String,
        userId: String,
        screenColorHex: String,
        drinkType: String
    ): String {
        val normalizedColor = screenColorHex.normalizeHexColor()
        val pendingGlassId = "glass_pending_color_${normalizedColor.removePrefix("#")}"
        return createMapping(
            sessionId = sessionId,
            userId = userId,
            glassId = pendingGlassId,
            drinkType = drinkType,
            mappingSource = "colorPending",
            screenColorHex = normalizedColor
        )
    }

    suspend fun createMapping(
        sessionId: String,
        userId: String,
        glassId: String,
        drinkType: String,
        mappingSource: String = "colorPending",
        screenColorHex: String? = null
    ): String {
        val collection = db.collection(FirestorePaths.glassMappings(sessionId))
        val existing = collection
            .whereEqualTo("userId", userId)
            .whereEqualTo("drinkType", drinkType)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()

        if (existing != null) {
            existing.reference.update(
                mapOf(
                    "glassId" to glassId,
                    "drinkType" to drinkType,
                    "mappingSource" to mappingSource,
                    "screenColorHex" to screenColorHex
                )
            ).await()
            return existing.id
        }

        val ref = collection.document()
        ref.set(
            hashMapOf(
                "userId" to userId,
                "glassId" to glassId,
                "drinkType" to drinkType,
                "drinkCount" to 0,
                "mappingSource" to mappingSource,
                "screenColorHex" to screenColorHex,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return ref.id
    }

    fun listenToMappings(
        sessionId: String,
        onUpdate: (List<GlassUserMapping>) -> Unit
    ): ListenerRegistration =
        db.collection(FirestorePaths.glassMappings(sessionId))
            .addSnapshotListener { snap, _ ->
                onUpdate(snap?.toObjects(GlassUserMapping::class.java).orEmpty())
            }

    private fun String.normalizeHexColor(): String {
        val compact = trim().removePrefix("#").lowercase(Locale.US)
        return "#$compact"
    }
}
