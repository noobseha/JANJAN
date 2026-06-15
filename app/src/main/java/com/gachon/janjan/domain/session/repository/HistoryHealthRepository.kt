package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.CalendarDrinkDay
import com.gachon.janjan.domain.session.model.DrinkHistoryItem
import com.gachon.janjan.domain.session.model.DrinkParticipantSummary
import com.gachon.janjan.domain.session.model.HealthSummary
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryHealthRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    private val zoneId: ZoneId = ZoneId.systemDefault()

    suspend fun getMyHistory(userId: String): List<DrinkHistoryItem> {
        val participations = db.collectionGroup(FirestorePaths.PARTICIPANTS)
            .whereEqualTo("userId", userId)
            .get()
            .await()

        val sessionRefs = participations.documents
            .mapNotNull { it.reference.parent.parent }
            .distinctBy { it.path }

        return sessionRefs.mapNotNull { sessionRef ->
            try {
                val session = sessionRef.get().await().toSessionModel() ?: return@mapNotNull null
                val participants = sessionRef.collection(FirestorePaths.PARTICIPANTS).get().await()
                val mappings = sessionRef.collection(FirestorePaths.GLASS_MAPPINGS).get().await()
                    .toObjects(com.gachon.janjan.domain.session.model.GlassUserMapping::class.java)

                val participantSummaries = participants.documents.map { participantDoc ->
                    val participantUserId = participantDoc.stringValue("userId").orEmpty()
                    val soju = mappings
                        .filter { it.userId == participantUserId && it.drinkType == "soju" }
                        .sumOf { it.drinkCount }
                    val beer = mappings
                        .filter { it.userId == participantUserId && it.drinkType == "beer" }
                        .sumOf { it.drinkCount }
                    DrinkParticipantSummary(
                        name = participantDoc.stringValue("userName").orEmpty().ifBlank { "참여자" },
                        sojuCount = soju,
                        beerCount = beer,
                        imageUrl = participantDoc.stringValue("imageUrl").orEmpty()
                    )
                }

                val mySoju = mappings
                    .filter { it.userId == userId && it.drinkType == "soju" }
                    .sumOf { it.drinkCount }
                val myBeer = mappings
                    .filter { it.userId == userId && it.drinkType == "beer" }
                    .sumOf { it.drinkCount }

                val amount = runCatching { findMySettlementAmount(session.sessionId, userId) }
                    .onFailure { android.util.Log.e("JANJAN_BUG", "findMySettlementAmount failed: ${it.message}") }
                    .getOrDefault(0)

                DrinkHistoryItem(
                    sessionId = session.sessionId,
                    storeName = session.storeName.ifBlank { "알 수 없는 가게" },
                    startedAt = session.startedAt.toTimestampOrNow(),
                    endedAt = session.endedAt.toTimestampOrNull(),
                    participantCount = participants.size(),
                    mySojuCount = mySoju,
                    myBeerCount = myBeer,
                    myAmount = amount,
                    participants = participantSummaries
                )
            } catch (e: Exception) {
                android.util.Log.e("JANJAN_BUG", "Failed to parse history item for ${sessionRef.path}: ${e.message}")
                null
            }
        }.sortedByDescending { it.startedAt.seconds }
    }

    suspend fun getHealthSummary(userId: String): HealthSummary {
        val participantDocs = db.collectionGroup(FirestorePaths.PARTICIPANTS)
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
        val sessionRefs = participantDocs
            .mapNotNull { it.reference.parent.parent }
            .distinctBy { it.path }
        val sessionDocs = sessionRefs.mapNotNull { ref ->
            val doc = runCatching { ref.get().await() }.getOrNull()
            doc?.let { ref.path to it }
        }.toMap()

        var soju = 0
        var beer = 0
        var weekly = 0
        val today = LocalDate.now(zoneId)
        val calendar = linkedMapOf<LocalDate, Pair<Int, Int>>()

        participantDocs.forEach { doc ->
            val sojuCount = doc.intValue("sojuDrinkCount")
            val beerCount = doc.intValue("beerDrinkCount")
            if (sojuCount <= 0 && beerCount <= 0) return@forEach

            soju += sojuCount
            beer += beerCount

            val sessionPath = doc.reference.parent.parent?.path.orEmpty()
            val sessionDoc = sessionDocs[sessionPath]
            val countedAt = doc.timestampMillis("lastDrinkAt").takeIf { it > 0L }
                ?: sessionDoc?.timestampMillis("endedAt")?.takeIf { it > 0L }
                ?: sessionDoc?.timestampMillis("startedAt")?.takeIf { it > 0L }
                ?: return@forEach
            val date = countedAt.toLocalDate()
            val totalCount = sojuCount + beerCount
            if (!date.isBefore(today.minusDays(6))) weekly += totalCount
            val current = calendar[date] ?: (0 to 0)
            calendar[date] = current.copy(
                first = current.first + sojuCount,
                second = current.second + beerCount
            )
        }

        val sessions = participantDocs.size

        var spending = 0
        for (sessionRef in sessionRefs) {
            val sessionId = sessionRef.id
            spending += findMySettlementAmount(sessionId, userId)
        }

        val calendarDays = calendar.map { (date, counts) ->
            CalendarDrinkDay(date, counts.first, counts.second)
        }

        return HealthSummary.calculate(
            soju = soju,
            beer = beer,
            sessions = sessions,
            spending = spending,
            weeklyDrinkCount = weekly,
            calendarDays = calendarDays
        )
    }

    private suspend fun findMySettlementAmount(sessionId: String, userId: String): Int {
        val settlements = db.collection(FirestorePaths.SETTLEMENTS)
            .whereEqualTo("sessionId", sessionId)
            .get()
            .await()

        if (settlements.isEmpty) return 0

        var totalAmount = 0
        for (doc in settlements.documents) {
            try {
                val settlement = doc.toObject(com.gachon.janjan.data.model.Settlement::class.java) ?: continue
                val myParticipant = settlement.participants.find { it.userId == userId }
                if (myParticipant != null) {
                    totalAmount += myParticipant.mytotal
                }
            } catch (e: Exception) {
                android.util.Log.e("JANJAN_BUG", "Error parsing settlement ${doc.id}: ${e.message}")
            }
        }
        return totalAmount
    }

    private fun Timestamp.toLocalDate(): LocalDate =
        Instant.ofEpochSecond(seconds, nanoseconds.toLong())
            .atZone(zoneId)
            .toLocalDate()

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this)
            .atZone(zoneId)
            .toLocalDate()

    private fun Long.toTimestampOrNow(): Timestamp =
        takeIf { it > 0L }?.let { Timestamp(java.util.Date(it)) } ?: Timestamp.now()

    private fun Long.toTimestampOrNull(): Timestamp? =
        takeIf { it > 0L }?.let { Timestamp(java.util.Date(it)) }
}
