package com.androidtown.janjansup.model

data class RankingModel(
    val userId: String = "",
    val userName: String = "",
    val profileImageUrl: String = "",
    val rank: Int = 0,
    val totalDrinks: Double = 0.0,
    val sojuCount: Double = 0.0,
    val beerCount: Double = 0.0,
    val isFriend: Boolean = false
)