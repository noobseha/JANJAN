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
    val sojuCount: Int,
    val beerCount: Int,
    val rank: Int = 0,
    val isMe: Boolean = false
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

data class RankingPeriodData(
    val users: List<RankingUserStat> = emptyList(),
    val stores: List<RankingStoreStat> = emptyList()
)

data class RankingUiState(
    val isLoading: Boolean = false,
    val selectedPeriod: RankingPeriod = RankingPeriod.WEEKLY,
    val selectedFilter: RankingDrinkFilter = RankingDrinkFilter.TOTAL,
    val users: List<RankingUserStat> = emptyList(),
    val stores: List<RankingStoreStat> = emptyList(),
    val myRank: RankingUserStat? = null,
    val message: String? = null
)
