package com.gachon.janjan.domain.session.model

enum class RankingFriendshipStatus {
    CAN_REQUEST,
    REQUESTED,
    INCOMING,
    FRIEND
}

data class FriendRequest(
    val id: String,
    val requesterId: String,
    val requesterName: String,
    val imageUrl: String = ""
)

data class SentFriendRequest(
    val id: String,
    val receiverId: String,
    val receiverName: String,
    val imageUrl: String = ""
)

data class FriendContext(
    val acceptedFriendIds: Set<String> = emptySet(),
    val statuses: Map<String, RankingFriendshipStatus> = emptyMap()
)

sealed class FriendActionResult {
    data class Success(val message: String) : FriendActionResult()
    data class Failure(val message: String) : FriendActionResult()
}
