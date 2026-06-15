package com.gachon.janjan.domain.session.model

enum class RankingPeriod(val label: String) {
    DAILY("일간"),
    WEEKLY("주간"),
    MONTHLY("월간")
}

enum class RankingDrinkFilter(val label: String) {
    TOTAL("전체"),
    SOJU("소주"),
    BEER("맥주")
}

data class RankingUserStat(
    val userId: String,
    val userName: String,
    val imageUrl: String = "",
    val sojuCount: Int,
    val beerCount: Int,
    val rank: Int = 0,
    val isMe: Boolean = false,
    val friendshipStatus: RankingFriendshipStatus = RankingFriendshipStatus.CAN_REQUEST
) {
    val totalCount: Int get() = sojuCount + beerCount

    fun countFor(filter: RankingDrinkFilter): Int =
        when (filter) {
            RankingDrinkFilter.SOJU -> sojuCount
            RankingDrinkFilter.BEER -> beerCount
            RankingDrinkFilter.TOTAL -> totalCount
        }
}

data class RankingStoreStat(
    val storeId: String,
    val storeName: String,
    val sojuCount: Int,
    val beerCount: Int,
    val rank: Int = 0
) {
    val totalCount: Int get() = sojuCount + beerCount

    fun countFor(filter: RankingDrinkFilter): Int =
        when (filter) {
            RankingDrinkFilter.SOJU -> sojuCount
            RankingDrinkFilter.BEER -> beerCount
            RankingDrinkFilter.TOTAL -> totalCount
    }
}

data class RankingStoreOption(
    val id: String,
    val rankingStoreId: String = id,
    val externalId: String = "",
    val name: String,
    val address: String = "",
    val roadAddress: String = "",
    val jibunAddress: String = "",
    val category: String = "",
    val phone: String = "",
    val placeUrl: String = ""
)

data class RankingPeriodData(
    val users: List<RankingUserStat> = emptyList(),
    val stores: List<RankingStoreStat> = emptyList(),
    val storeUsersByStoreId: Map<String, List<RankingUserStat>> = emptyMap()
)

data class RankingUiState(
    val isLoading: Boolean = false,
    val selectedPeriod: RankingPeriod = RankingPeriod.WEEKLY,
    val selectedFilter: RankingDrinkFilter = RankingDrinkFilter.TOTAL,
    val selectedStoreId: String? = null,
    val users: List<RankingUserStat> = emptyList(),
    val stores: List<RankingStoreStat> = emptyList(),
    val storeOptions: List<RankingStoreOption> = emptyList(),
    val storeUsers: List<RankingUserStat> = emptyList(),
    val myRank: RankingUserStat? = null,
    val message: String? = null,
    val incomingFriendRequests: List<FriendRequest> = emptyList(),
    val outgoingFriendRequests: List<SentFriendRequest> = emptyList()
)
