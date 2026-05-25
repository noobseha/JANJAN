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
                    beerCount = beer
                )
            }

            val mySoju = mappings
                .filter { it.userId == userId && it.drinkType == "soju" }
                .sumOf { it.drinkCount }
            val myBeer = mappings
                .filter { it.userId == userId && it.drinkType == "beer" }
                .sumOf { it.drinkCount }

            DrinkHistoryItem(
                sessionId = session.sessionId,
                storeName = session.storeName.ifBlank { "알 수 없는 가게" },
                startedAt = session.startedAt.toTimestampOrNow(),
                endedAt = session.endedAt.toTimestampOrNull(),
                participantCount = participants.size(),
                mySojuCount = mySoju,
                myBeerCount = myBeer,
                myAmount = findMySettlementAmount(session.sessionId, userId),
                participants = participantSummaries
            )
        }.sortedByDescending { it.startedAt.seconds }
    }

    suspend fun getHealthSummary(userId: String): HealthSummary {
        val records = db.collectionGroup(FirestorePaths.DRINK_RECORDS)
            .whereEqualTo("userId", userId)
            .get()
            .await()

        var soju = 0
        var beer = 0
        var weekly = 0
        val today = LocalDate.now(zoneId)
        val calendar = linkedMapOf<LocalDate, Pair<Int, Int>>()

        records.documents.forEach { doc ->
            when (doc.stringValue("drinkType")) {
                "soju" -> soju++
                "beer" -> beer++
            }
            val date = doc.getTimestamp("recordedAt")?.toLocalDate() ?: return@forEach
            if (!date.isBefore(today.minusDays(6))) weekly++
            val current = calendar[date] ?: (0 to 0)
            calendar[date] = when (doc.stringValue("drinkType")) {
                "soju" -> current.copy(first = current.first + 1)
                "beer" -> current.copy(second = current.second + 1)
                else -> current
            }
        }

        val sessions = db.collectionGroup(FirestorePaths.PARTICIPANTS)
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .size()

        val spending = db.collectionGroup(FirestorePaths.SETTLEMENT_ITEMS)
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .sumOf { it.get("amount").asInt() }

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
        val directItems = db.collection(FirestorePaths.SETTLEMENTS)
            .document(sessionId)
            .collection(FirestorePaths.SETTLEMENT_ITEMS)
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents

        if (directItems.isNotEmpty()) {
            return directItems.sumOf { it.get("amount").asInt() }
        }

        val settlements = db.collection(FirestorePaths.SETTLEMENTS)
            .whereEqualTo("sessionId", sessionId)
            .get()
            .await()

        return settlements.documents.sumOf { settlement ->
            settlement.reference.collection(FirestorePaths.SETTLEMENT_ITEMS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .sumOf { it.get("amount").asInt() }
        }
    }

    private fun Timestamp.toLocalDate(): LocalDate =
        Instant.ofEpochSecond(seconds, nanoseconds.toLong())
            .atZone(zoneId)
            .toLocalDate()

    private fun Long.toTimestampOrNow(): Timestamp =
        takeIf { it > 0L }?.let { Timestamp(java.util.Date(it)) } ?: Timestamp.now()

    private fun Long.toTimestampOrNull(): Timestamp? =
        takeIf { it > 0L }?.let { Timestamp(java.util.Date(it)) }
}
