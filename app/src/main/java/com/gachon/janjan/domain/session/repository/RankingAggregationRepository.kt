package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId

class RankingAggregationRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul")

    suspend fun aggregateClosedSession(sessionId: String) {
        if (sessionId.isBlank()) return

        val sessionDoc = findSessionDocument(sessionId) ?: return
        if (sessionDoc.getString("status") != "closed") return

        val endedAtMillis = sessionDoc.timestampMillis("endedAt")
            .takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val dateKey = Instant.ofEpochMilli(endedAtMillis)
            .atZone(zoneId)
            .toLocalDate()
            .toString()
        val storeId = sessionDoc.stringValue("storeId").ifBlank { "unknown_store" }
        val storeName = sessionDoc.stringValue("storeName").ifBlank { "Unknown store" }
        val fallbackCounts = loadGlassMappingCounts(sessionDoc)
        val participants = sessionDoc.reference
            .collection(FirestorePaths.PARTICIPANTS)
            .get()
            .await()
            .documents

        participants.forEach { participant ->
            aggregateParticipant(
                sessionId = sessionDoc.id,
                participant = participant,
                fallbackCounts = fallbackCounts,
                dateKey = dateKey,
                storeId = storeId,
                storeName = storeName,
                endedAtMillis = endedAtMillis
            )
        }
    }

    private suspend fun aggregateParticipant(
        sessionId: String,
        participant: DocumentSnapshot,
        fallbackCounts: Map<String, DrinkCounts>,
        dateKey: String,
        storeId: String,
        storeName: String,
        endedAtMillis: Long
    ) {
        val userId = participant.stringValue("userId").ifBlank { participant.id }
        if (userId.isBlank()) return

        val participantCounts = DrinkCounts(
            soju = participant.intValue("sojuDrinkCount"),
            beer = participant.intValue("beerDrinkCount")
        )
        val counts = participantCounts.takeIf { it.total > 0 }
            ?: fallbackCounts[userId]
            ?: DrinkCounts()
        if (counts.total <= 0) return

        val userName = participant.stringValue("userName").ifBlank { "User ${userId.takeLast(4)}" }
        val imageUrl = participant.stringValue("imageUrl")
        val contributionId = "${sessionId}_${userId}".toSafeDocId()
        val dailyStatId = "${dateKey}_${userId}".toSafeDocId()
        val storeStatId = "${dateKey}_${storeId}_${userId}".toSafeDocId()
        val contributionRef = db.collection(FirestorePaths.RANKING_CONTRIBUTIONS).document(contributionId)
        val dailyStatRef = db.collection(FirestorePaths.DRINK_DAILY_STATS).document(dailyStatId)
        val storeStatRef = db.collection(FirestorePaths.DRINK_STORE_DAILY_STATS).document(storeStatId)
        val endedAt = Timestamp(endedAtMillis / 1000, ((endedAtMillis % 1000) * 1_000_000).toInt())

        db.runTransaction { transaction ->
            val contribution = transaction.get(contributionRef)
            if (contribution.exists()) {
                return@runTransaction null
            }

            val countUpdates = mapOf(
                "sojuCount" to FieldValue.increment(counts.soju.toLong()),
                "beerCount" to FieldValue.increment(counts.beer.toLong()),
                "totalCount" to FieldValue.increment(counts.total.toLong()),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            transaction.set(
                dailyStatRef,
                mapOf(
                    "dateKey" to dateKey,
                    "userId" to userId,
                    "userName" to userName,
                    "imageUrl" to imageUrl
                ) + countUpdates,
                com.google.firebase.firestore.SetOptions.merge()
            )

            transaction.set(
                storeStatRef,
                mapOf(
                    "dateKey" to dateKey,
                    "storeId" to storeId,
                    "storeName" to storeName,
                    "userId" to userId,
                    "userName" to userName,
                    "imageUrl" to imageUrl
                ) + countUpdates,
                com.google.firebase.firestore.SetOptions.merge()
            )

            transaction.set(
                contributionRef,
                mapOf(
                    "sessionId" to sessionId,
                    "participantId" to participant.id,
                    "dateKey" to dateKey,
                    "storeId" to storeId,
                    "storeName" to storeName,
                    "userId" to userId,
                    "userName" to userName,
                    "imageUrl" to imageUrl,
                    "sojuCount" to counts.soju,
                    "beerCount" to counts.beer,
                    "totalCount" to counts.total,
                    "endedAt" to endedAt,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.await()
    }

    private suspend fun loadGlassMappingCounts(sessionDoc: DocumentSnapshot): Map<String, DrinkCounts> {
        val mappings = sessionDoc.reference
            .collection(FirestorePaths.GLASS_MAPPINGS)
            .get()
            .await()
            .documents
        val counts = linkedMapOf<String, DrinkCounts>()

        mappings.forEach { mapping ->
            val userId = mapping.stringValue("userId")
            val drinkType = mapping.stringValue("drinkType")
            val drinkCount = mapping.intValue("drinkCount")
            if (userId.isBlank() || drinkCount <= 0) return@forEach

            val current = counts[userId] ?: DrinkCounts()
            counts[userId] = when (drinkType) {
                "beer" -> current.copy(beer = current.beer + drinkCount)
                else -> current.copy(soju = current.soju + drinkCount)
            }
        }

        return counts
    }

    private suspend fun findSessionDocument(sessionId: String): DocumentSnapshot? {
        val direct = db.collection(FirestorePaths.SESSIONS).document(sessionId).get().await()
        if (direct.exists()) return direct

        return db.collection(FirestorePaths.SESSIONS)
            .whereEqualTo("sessionId", sessionId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
    }

    private fun DocumentSnapshot.stringValue(field: String): String =
        when (val value = get(field)) {
            is String -> value
            is Number -> value.toLong().toString()
            else -> ""
        }

    private fun DocumentSnapshot.intValue(field: String): Int =
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

    private fun String.toSafeDocId(): String =
        trim()
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(140)
            .ifBlank { "unknown" }

    private data class DrinkCounts(
        val soju: Int = 0,
        val beer: Int = 0
    ) {
        val total: Int get() = soju + beer
    }
}
