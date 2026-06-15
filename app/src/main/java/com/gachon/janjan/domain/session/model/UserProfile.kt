package com.gachon.janjan.domain.session.model

data class UserProfile(
    val userId: String = "",
    val nickname: String = "사용자",
    val bio: String = "잔잔과 함께한 술자리",
    val phone: String = "",
    val address: String = "",
    val imageUrl: String = "",
    val activityVisibility: ActivityVisibility = ActivityVisibility.PUBLIC
)
