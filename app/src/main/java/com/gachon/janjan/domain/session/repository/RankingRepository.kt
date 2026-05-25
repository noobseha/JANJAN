package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.RankingPeriod
import com.gachon.janjan.domain.session.model.RankingPeriodData
import com.gachon.janjan.domain.session.model.RankingStoreStat
import com.gachon.janjan.domain.session.model.RankingUserStat
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId

class RankingRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    private val zoneId: ZoneId = ZoneId.systemDefault()

    suspend fun loadRankings(currentUserId: String): Map<RankingPeriod, RankingPeriodData> {
        val recordDocs = db.collectionGroup(FirestorePaths.DRINK_RECORDS)
            .get()
            .await()
            .documents

        val records = recordDocs.mapNotNull { doc ->
            val userId = doc.stringValue("userId").orEmpty()
            val recordedAt = doc.timestampMillisOrZero("recordedAt")
            if (userId.isBlank() || recordedAt <= 0L) return@mapNotNull null

            val drinkType = doc.stringValue("drinkType").orEmpty()
            val sojuDelta = doc.intValue("sojuCountDelta").takeIf { it > 0 }
                ?: if (drinkType == "soju") 1 else 0
            val beerDelta = doc.intValue("beerCountDelta").takeIf { it > 0 }
                ?: if (drinkType == "beer") 1 else 0
            if (sojuDelta <= 0 && beerDelta <= 0) return@mapNotNull null

            RawDrinkRecord(
                userId = userId,
                sessionPath = doc.reference.parent.parent?.path.orEmpty(),
                recordedAtMillis = recordedAt,
                sojuDelta = sojuDelta,
                beerDelta = beerDelta
            )
        }

        val userProfiles = loadUserProfiles(records.map { it.userId }.toSet())
        val sessionDocs = loadSessionDocs(recordDocs.mapNotNull { it.reference.parent.parent })
        val today = LocalDate.now(zoneId)

        return RankingPeriod.entries.associateWith { period ->
            val startMillis = period.startMillis(today)
            buildPeriodData(
                records = records.filter { it.recordedAtMillis >= startMillis },
                currentUserId = currentUserId,
                userProfiles = userProfiles,
                sessionDocs = sessionDocs
            )
        }
    }

    private suspend fun loadUserProfiles(userIds: Set<String>): Map<String, String> =
        userIds.associateWith { userId ->
            val doc = runCatching {
                db.collection(FirestorePaths.USERS).document(userId).get().await()
            }.getOrNull()
            doc.displayName().ifBlank { "사용자 ${userId.takeLast(4)}" }
        }

    private suspend fun loadSessionDocs(
        refs: List<com.google.firebase.firestore.DocumentReference>
    ): Map<String, DocumentSnapshot> =
        refs.distinctBy { it.path }.mapNotNull { ref ->
            val doc = runCatching { ref.get().await() }.getOrNull()
            doc?.let { ref.path to it }
        }.toMap()

    private fun buildPeriodData(
        records: List<RawDrinkRecord>,
        currentUserId: String,
        userProfiles: Map<String, String>,
        sessionDocs: Map<String, DocumentSnapshot>
    ): RankingPeriodData {
        val userCounts = linkedMapOf<String, CountAccumulator>()
        val storeCounts = linkedMapOf<String, CountAccumulator>()

        records.forEach { record ->
            userCounts.getOrPut(record.userId) { CountAccumulator() }
                .add(record.sojuDelta, record.beerDelta)

            val session = sessionDocs[record.sessionPath]?.toSessionModel()
            val storeKey = session?.storeId?.takeIf { it.isNotBlank() }
                ?: session?.storeName?.takeIf { it.isNotBlank() }
                ?: "unknown_store"
            storeCounts.getOrPut(storeKey) {
                CountAccumulator(
                    label = session?.storeName?.takeIf { it.isNotBlank() } ?: "알 수 없는 가게"
                )
            }.add(record.sojuDelta, record.beerDelta)
        }

        val users = userCounts.map { (userId, counts) ->
            RankingUserStat(
                userId = userId,
                userName = userProfiles[userId].orEmpty().ifBlank { "사용자 ${userId.takeLast(4)}" },
                sojuCount = counts.soju,
                beerCount = counts.beer,
                isMe = userId == currentUserId
            )
        }

        val stores = storeCounts.map { (storeId, counts) ->
            RankingStoreStat(
                storeId = storeId,
                storeName = counts.label.ifBlank { "알 수 없는 가게" },
                sojuCount = counts.soju,
                beerCount = counts.beer
            )
        }

        return RankingPeriodData(users = users, stores = stores)
    }

    private fun RankingPeriod.startMillis(today: LocalDate): Long {
        val startDate = when (this) {
            RankingPeriod.DAILY -> today
            RankingPeriod.WEEKLY -> today.minusDays(6)
            RankingPeriod.MONTHLY -> today.withDayOfMonth(1)
        }
        return startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun DocumentSnapshot?.displayName(): String {
        if (this == null || !exists()) return ""
        return stringValue("nickname")
            ?: stringValue("name")
            ?: stringValue("login_id")?.substringBefore("@")
            ?: ""
    }

    private fun DocumentSnapshot.timestampMillisOrZero(field: String): Long =
        when (val value = get(field)) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> 0L
        }

    private data class RawDrinkRecord(
        val userId: String,
        val sessionPath: String,
        val recordedAtMillis: Long,
        val sojuDelta: Int,
        val beerDelta: Int
    )

    private data class CountAccumulator(
        val label: String = "",
        var soju: Int = 0,
        var beer: Int = 0
    ) {
        fun add(sojuDelta: Int, beerDelta: Int) {
            soju += sojuDelta
            beer += beerDelta
        }
    }
}
