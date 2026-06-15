package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.ActivityVisibility
import com.gachon.janjan.domain.session.model.FriendContext
import com.gachon.janjan.domain.session.model.RankingFriendshipStatus
import com.gachon.janjan.domain.session.model.RankingPeriod
import com.gachon.janjan.domain.session.model.RankingPeriodData
import com.gachon.janjan.domain.session.model.RankingStoreOption
import com.gachon.janjan.domain.session.model.RankingStoreStat
import com.gachon.janjan.domain.session.model.RankingUserStat
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class RankingRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db,
    private val friendRepository: FriendRepository = FriendRepository(db),
    private val kakaoStoreSearchClient: KakaoStoreSearchClient = KakaoStoreSearchClient()
) {
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul")

    suspend fun loadRankings(currentUserId: String): Map<RankingPeriod, RankingPeriodData> {
        val today = LocalDate.now(zoneId)
        val monthlyStart = RankingPeriod.MONTHLY.startDate(today)
        val dailyRecords = loadDailyRecords(monthlyStart, today)
        val storeRecords = loadStoreDailyRecords(monthlyStart, today)
        val recordUserIds = (dailyRecords.map { it.userId } + storeRecords.map { it.userId }).toSet()
        val friendContext = friendRepository.loadFriendContext(currentUserId, recordUserIds)
        val activityVisibilities = loadActivityVisibilities(recordUserIds)
        val visibleUserIds = recordUserIds.filter { userId ->
            canViewActivity(
                userId = userId,
                currentUserId = currentUserId,
                visibility = activityVisibilities[userId] ?: ActivityVisibility.PUBLIC,
                acceptedFriendIds = friendContext.acceptedFriendIds
            )
        }.toSet()
        val visibleDailyRecords = dailyRecords.filter { it.userId in visibleUserIds }
        val visibleStoreRecords = storeRecords.filter { it.userId in visibleUserIds }
        val userProfiles = loadUserProfiles(visibleUserIds)

        return RankingPeriod.entries.associateWith { period ->
            val startKey = period.startDate(today).toString()
            buildPeriodData(
                records = visibleDailyRecords.filter { it.dateKey >= startKey },
                storeRecords = visibleStoreRecords.filter { it.dateKey >= startKey },
                currentUserId = currentUserId,
                userProfiles = userProfiles,
                friendContext = friendContext
            )
        }
    }

    suspend fun loadStoreOptions(): List<RankingStoreOption> =
        db.collection(FirestorePaths.STORES)
            .get()
            .await()
            .documents
            .filter { it.getBoolean("isActive") != false }
            .map { doc ->
                RankingStoreOption(
                    id = doc.id,
                    rankingStoreId = doc.id,
                    externalId = doc.stringValue("kakaoPlaceId").orEmpty(),
                    name = doc.displayName().ifBlank { "Store ${doc.id.takeLast(4)}" },
                    address = doc.stringValue("address")
                        ?: doc.stringValue("roadAddress")
                        ?: doc.stringValue("jibunAddress")
                        ?: "",
                    roadAddress = doc.stringValue("roadAddress").orEmpty(),
                    jibunAddress = doc.stringValue("jibunAddress").orEmpty(),
                    category = doc.stringValue("category")
                        ?: doc.stringValue("kakaoCategory")
                        ?: "",
                    phone = doc.stringValue("phone").orEmpty(),
                    placeUrl = doc.stringValue("kakaoPlaceUrl").orEmpty()
                )
            }
            .sortedBy { it.name }

    suspend fun searchStoreOptions(query: String): List<RankingStoreOption> {
        val cleanedQuery = query.trim()
        if (cleanedQuery.length < 2) return emptyList()

        val registeredStores = loadStoreOptions()
        val kakaoStores = runCatching {
            kakaoStoreSearchClient.search(cleanedQuery)
        }.getOrDefault(emptyList())
        val results = kakaoStores.map { place ->
            val matchedStore = registeredStores.findBestMatch(place)
            RankingStoreOption(
                id = matchedStore?.id ?: "kakao_${place.id.ifBlank { place.name.toSafeStoreKey() }}",
                rankingStoreId = matchedStore?.rankingStoreId.orEmpty(),
                externalId = place.id,
                name = place.name,
                address = place.address,
                roadAddress = place.roadAddress,
                jibunAddress = place.jibunAddress,
                category = place.category,
                phone = place.phone,
                placeUrl = place.placeUrl
            )
        }.toMutableList()

        val existingRankingIds = results.map { it.rankingStoreId }.filter { it.isNotBlank() }.toSet()
        registeredStores
            .filter { store ->
                store.rankingStoreId !in existingRankingIds &&
                    (store.name.contains(cleanedQuery, ignoreCase = true) ||
                        store.category.contains(cleanedQuery, ignoreCase = true) ||
                        store.addressCandidates().any { it.contains(cleanedQuery, ignoreCase = true) } ||
                        store.phone.normalizePhone().contains(cleanedQuery.normalizePhone()))
            }
            .forEach(results::add)

        return results.distinctBy { it.id }.take(20)
    }

    private suspend fun loadDailyRecords(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RawDailyRecord> =
        db.collection(FirestorePaths.DRINK_DAILY_STATS)
            .whereGreaterThanOrEqualTo("dateKey", startDate.toString())
            .whereLessThanOrEqualTo("dateKey", endDate.toString())
            .get()
            .await()
            .documents
            .mapNotNull { it.toDailyRecord() }

    private suspend fun loadStoreDailyRecords(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RawStoreDailyRecord> =
        db.collection(FirestorePaths.DRINK_STORE_DAILY_STATS)
            .whereGreaterThanOrEqualTo("dateKey", startDate.toString())
            .whereLessThanOrEqualTo("dateKey", endDate.toString())
            .get()
            .await()
            .documents
            .mapNotNull { it.toStoreDailyRecord() }

    private suspend fun loadUserProfiles(userIds: Set<String>): Map<String, UserProfileInfo> =
        userIds.associateWith { userId ->
            val doc = runCatching {
                db.collection(FirestorePaths.USERS).document(userId).get().await()
            }.getOrNull()
            UserProfileInfo(
                name = doc?.displayName()?.ifBlank { "User ${userId.takeLast(4)}" }
                    ?: "User ${userId.takeLast(4)}",
                imageUrl = doc?.getString("imageUrl") ?: ""
            )
        }

    private fun buildPeriodData(
        records: List<RawDailyRecord>,
        storeRecords: List<RawStoreDailyRecord>,
        currentUserId: String,
        userProfiles: Map<String, UserProfileInfo>,
        friendContext: FriendContext
    ): RankingPeriodData {
        val userCounts = linkedMapOf<String, CountAccumulator>()
        val storeCounts = linkedMapOf<String, CountAccumulator>()
        val storeUserCounts = linkedMapOf<String, LinkedHashMap<String, CountAccumulator>>()

        records.forEach { record ->
            userCounts.getOrPut(record.userId) {
                CountAccumulator(label = record.userName, imageUrl = record.imageUrl)
            }.add(record.sojuDelta, record.beerDelta)
        }

        storeRecords.forEach { record ->
            storeCounts.getOrPut(record.storeId) {
                CountAccumulator(label = record.storeName)
            }.add(record.sojuDelta, record.beerDelta)

            storeUserCounts.getOrPut(record.storeId) { linkedMapOf() }
                .getOrPut(record.userId) {
                    CountAccumulator(label = record.userName, imageUrl = record.imageUrl)
                }.add(record.sojuDelta, record.beerDelta)
        }

        val users = userCounts.map { (userId, counts) ->
            toUserStat(
                userId = userId,
                counts = counts,
                currentUserId = currentUserId,
                userProfiles = userProfiles,
                friendContext = friendContext
            )
        }

        val stores = storeCounts.map { (storeId, counts) ->
            RankingStoreStat(
                storeId = storeId,
                storeName = counts.label.ifBlank { "Store ${storeId.takeLast(4)}" },
                sojuCount = counts.soju,
                beerCount = counts.beer
            )
        }

        val storeUsersByStoreId = storeUserCounts.mapValues { (_, perUserCounts) ->
            perUserCounts.map { (userId, counts) ->
                toUserStat(
                    userId = userId,
                    counts = counts,
                    currentUserId = currentUserId,
                    userProfiles = userProfiles,
                    friendContext = friendContext
                )
            }
        }

        return RankingPeriodData(
            users = users,
            stores = stores,
            storeUsersByStoreId = storeUsersByStoreId
        )
    }

    private fun toUserStat(
        userId: String,
        counts: CountAccumulator,
        currentUserId: String,
        userProfiles: Map<String, UserProfileInfo>,
        friendContext: FriendContext
    ): RankingUserStat =
        RankingUserStat(
            userId = userId,
            userName = userProfiles[userId]?.name?.takeIf { it.isNotBlank() }
                ?: counts.label.ifBlank { "User ${userId.takeLast(4)}" },
            imageUrl = userProfiles[userId]?.imageUrl?.takeIf { it.isNotBlank() }
                ?: counts.imageUrl,
            sojuCount = counts.soju,
            beerCount = counts.beer,
            isMe = userId == currentUserId,
            friendshipStatus = if (userId == currentUserId) {
                RankingFriendshipStatus.FRIEND
            } else {
                friendContext.statuses[userId] ?: RankingFriendshipStatus.CAN_REQUEST
            }
        )

    private suspend fun loadActivityVisibilities(userIds: Set<String>): Map<String, ActivityVisibility> =
        userIds.associateWith { userId ->
            val doc = runCatching {
                db.collection(FirestorePaths.USER_APP_SETTINGS).document(userId).get().await()
            }.getOrNull()
            ActivityVisibility.fromStorage(
                value = doc?.getString("activity_visibility"),
                legacyPrivate = doc?.getBoolean("is_private_account")
            )
        }

    private fun canViewActivity(
        userId: String,
        currentUserId: String,
        visibility: ActivityVisibility,
        acceptedFriendIds: Set<String>
    ): Boolean {
        if (userId == currentUserId) return true
        return when (visibility) {
            ActivityVisibility.PUBLIC -> true
            ActivityVisibility.FRIENDS -> userId in acceptedFriendIds
            ActivityVisibility.PRIVATE -> false
        }
    }

    private fun RankingPeriod.startDate(today: LocalDate): LocalDate =
        when (this) {
            RankingPeriod.DAILY -> today
            RankingPeriod.WEEKLY -> today.minusDays(6)
            RankingPeriod.MONTHLY -> today.withDayOfMonth(1)
        }

    private fun DocumentSnapshot?.displayName(): String {
        if (this == null || !exists()) return ""
        return stringValue("nickname")
            ?: stringValue("name")
            ?: stringValue("storeName")
            ?: stringValue("loginId")?.substringBefore("@")
            ?: ""
    }

    private fun DocumentSnapshot.toDailyRecord(): RawDailyRecord? {
        val dateKey = stringValue("dateKey").orEmpty()
        val userId = stringValue("userId").orEmpty()
        val sojuCount = intValue("sojuCount")
        val beerCount = intValue("beerCount")
        if (dateKey.isBlank() || userId.isBlank() || sojuCount + beerCount <= 0) {
            return null
        }

        return RawDailyRecord(
            dateKey = dateKey,
            userId = userId,
            userName = stringValue("userName").orEmpty(),
            imageUrl = stringValue("imageUrl").orEmpty(),
            sojuDelta = sojuCount,
            beerDelta = beerCount
        )
    }

    private fun DocumentSnapshot.toStoreDailyRecord(): RawStoreDailyRecord? {
        val dateKey = stringValue("dateKey").orEmpty()
        val storeId = stringValue("storeId").orEmpty()
        val userId = stringValue("userId").orEmpty()
        val sojuCount = intValue("sojuCount")
        val beerCount = intValue("beerCount")
        if (dateKey.isBlank() || storeId.isBlank() || userId.isBlank() || sojuCount + beerCount <= 0) {
            return null
        }

        return RawStoreDailyRecord(
            dateKey = dateKey,
            storeId = storeId,
            storeName = stringValue("storeName").orEmpty(),
            userId = userId,
            userName = stringValue("userName").orEmpty(),
            imageUrl = stringValue("imageUrl").orEmpty(),
            sojuDelta = sojuCount,
            beerDelta = beerCount
        )
    }

    private data class RawDailyRecord(
        val dateKey: String,
        val userId: String,
        val userName: String,
        val imageUrl: String,
        val sojuDelta: Int,
        val beerDelta: Int
    )

    private data class RawStoreDailyRecord(
        val dateKey: String,
        val storeId: String,
        val storeName: String,
        val userId: String,
        val userName: String,
        val imageUrl: String,
        val sojuDelta: Int,
        val beerDelta: Int
    )

    private data class CountAccumulator(
        val label: String = "",
        val imageUrl: String = "",
        var soju: Int = 0,
        var beer: Int = 0
    ) {
        fun add(sojuDelta: Int, beerDelta: Int) {
            soju += sojuDelta
            beer += beerDelta
        }
    }

    private data class UserProfileInfo(
        val name: String,
        val imageUrl: String
    )

    private fun List<RankingStoreOption>.findBestMatch(place: KakaoPlace): RankingStoreOption? {
        val placeId = place.id.trim()
        val placePhone = place.phone.normalizePhone()
        val placeName = place.name.normalizeStoreText()
        val placeAddresses = place.addressCandidates()

        return firstOrNull { store ->
            placeId.isNotBlank() && store.externalId == placeId
        } ?: firstOrNull { store ->
            placePhone.isNotBlank() && store.phone.normalizePhone() == placePhone
        } ?: firstOrNull { store ->
            store.addressCandidates().any { storeAddress ->
                placeAddresses.any { placeAddress ->
                    storeAddress.matchesAddress(placeAddress)
                }
            }
        } ?: firstOrNull { store ->
            store.name.normalizeStoreText() == placeName &&
                store.addressCandidates().any { storeAddress ->
                    placeAddresses.any { placeAddress ->
                        storeAddress.hasSameAddressPrefix(placeAddress)
                    }
                }
        } ?: firstOrNull { store ->
            store.name.normalizeStoreText() == placeName
        }
    }

    private fun RankingStoreOption.addressCandidates(): List<String> =
        listOf(address, roadAddress, jibunAddress)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun KakaoPlace.addressCandidates(): List<String> =
        listOf(address, roadAddress, jibunAddress)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun String.matchesAddress(other: String): Boolean {
        val first = normalizeAddress()
        val second = other.normalizeAddress()
        if (first.isBlank() || second.isBlank()) return false
        if (first == second) return true

        val minLength = minOf(first.length, second.length)
        return minLength >= MIN_ADDRESS_CONTAINS_LENGTH &&
            (first.contains(second) || second.contains(first))
    }

    private fun String.hasSameAddressPrefix(other: String): Boolean {
        val first = normalizeAddress()
        val second = other.normalizeAddress()
        if (first.isBlank() || second.isBlank()) return false

        val prefixLength = minOf(first.length, second.length, ADDRESS_PREFIX_LENGTH)
        return prefixLength >= MIN_ADDRESS_PREFIX_LENGTH &&
            first.take(prefixLength) == second.take(prefixLength)
    }

    private fun String.normalizeStoreText(): String =
        trim()
            .lowercase(Locale.KOREA)
            .replace(Regex("\\s+"), "")

    private fun String.normalizeAddress(): String =
        normalizeStoreText()
            .replace("서울특별시", "서울")
            .replace("부산광역시", "부산")
            .replace("대구광역시", "대구")
            .replace("인천광역시", "인천")
            .replace("광주광역시", "광주")
            .replace("대전광역시", "대전")
            .replace("울산광역시", "울산")
            .replace("세종특별자치시", "세종")
            .replace("제주특별자치도", "제주")
            .replace("강원특별자치도", "강원")

    private fun String.normalizePhone(): String =
        filter { it.isDigit() }

    private fun String.toSafeStoreKey(): String =
        trim()
            .replace(Regex("[^A-Za-z0-9가-힣_-]"), "_")
            .take(80)
            .ifBlank { "unknown_store" }

    private companion object {
        private const val MIN_ADDRESS_CONTAINS_LENGTH = 10
        private const val ADDRESS_PREFIX_LENGTH = 12
        private const val MIN_ADDRESS_PREFIX_LENGTH = 8
    }
}
