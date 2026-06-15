package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.FriendActionResult
import com.gachon.janjan.domain.session.model.FriendContext
import com.gachon.janjan.domain.session.model.FriendRequest
import com.gachon.janjan.domain.session.model.RankingFriendshipStatus
import com.gachon.janjan.domain.session.model.SentFriendRequest
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FriendRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    suspend fun loadFriendContext(
        currentUserId: String,
        targetUserIds: Set<String>
    ): FriendContext {
        if (currentUserId.isBlank()) return FriendContext()

        val docs = db.collection(FirestorePaths.FRIENDSHIPS)
            .whereArrayContains("memberIds", currentUserId)
            .get()
            .await()
            .documents

        val acceptedFriendIds = mutableSetOf<String>()
        val statuses = mutableMapOf<String, RankingFriendshipStatus>()

        docs.forEach { doc ->
            val otherUserId = doc.otherUserId(currentUserId) ?: return@forEach
            when (doc.getString("status")) {
                STATUS_ACCEPTED -> {
                    acceptedFriendIds += otherUserId
                    statuses[otherUserId] = RankingFriendshipStatus.FRIEND
                }
                STATUS_PENDING -> {
                    statuses[otherUserId] = if (doc.getString("requesterId") == currentUserId) {
                        RankingFriendshipStatus.REQUESTED
                    } else {
                        RankingFriendshipStatus.INCOMING
                    }
                }
            }
        }

        val filteredStatuses = targetUserIds.associateWith { userId ->
            statuses[userId] ?: RankingFriendshipStatus.CAN_REQUEST
        }

        return FriendContext(
            acceptedFriendIds = acceptedFriendIds,
            statuses = filteredStatuses
        )
    }

    suspend fun loadIncomingRequests(currentUserId: String): List<FriendRequest> {
        if (currentUserId.isBlank()) return emptyList()

        val docs = db.collection(FirestorePaths.FRIENDSHIPS)
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("status", STATUS_PENDING)
            .get()
            .await()
            .documents

        return docs.mapNotNull { doc ->
            val requesterId = doc.getString("requesterId").orEmpty()
            if (requesterId.isBlank()) return@mapNotNull null
            
            val userDoc = runCatching {
                db.collection(FirestorePaths.USERS).document(requesterId).get().await()
            }.getOrNull()
            
            val name = doc.getString("requesterName")
                ?: userDoc?.getString("nickname")
                ?: userDoc?.getString("name")
                ?: userDoc?.getString("loginId")?.substringBefore("@")
                ?: "사용자 ${requesterId.takeLast(4)}"
                
            val imageUrl = userDoc?.getString("imageUrl") ?: ""

            FriendRequest(
                id = doc.id,
                requesterId = requesterId,
                requesterName = name,
                imageUrl = imageUrl
            )
        }
    }

    suspend fun loadOutgoingRequests(currentUserId: String): List<SentFriendRequest> {
        if (currentUserId.isBlank()) return emptyList()

        val docs = db.collection(FirestorePaths.FRIENDSHIPS)
            .whereEqualTo("requesterId", currentUserId)
            .whereEqualTo("status", STATUS_PENDING)
            .get()
            .await()
            .documents

        return docs.mapNotNull { doc ->
            val receiverId = doc.getString("receiverId").orEmpty()
            if (receiverId.isBlank()) return@mapNotNull null
            
            val userDoc = runCatching {
                db.collection(FirestorePaths.USERS).document(receiverId).get().await()
            }.getOrNull()
            
            val name = doc.getString("receiverName")
                ?: userDoc?.getString("nickname")
                ?: userDoc?.getString("name")
                ?: userDoc?.getString("loginId")?.substringBefore("@")
                ?: "사용자 ${receiverId.takeLast(4)}"
                
            val imageUrl = userDoc?.getString("imageUrl") ?: ""

            SentFriendRequest(
                id = doc.id,
                receiverId = receiverId,
                receiverName = name,
                imageUrl = imageUrl
            )
        }
    }

    suspend fun sendFriendRequest(
        currentUserId: String,
        targetUserId: String
    ): FriendActionResult {
        val cleanedTargetId = targetUserId.trim()
        if (currentUserId.isBlank() || cleanedTargetId.isBlank()) {
            return FriendActionResult.Failure("친구 요청 대상을 찾을 수 없어요.")
        }
        if (currentUserId == cleanedTargetId) {
            return FriendActionResult.Failure("본인에게는 친구 요청을 보낼 수 없어요.")
        }

        val friendshipRef = db.collection(FirestorePaths.FRIENDSHIPS)
            .document(pairId(currentUserId, cleanedTargetId))
        val existing = friendshipRef.get().await()
        if (existing.exists()) {
            return when (existing.getString("status")) {
                STATUS_ACCEPTED -> FriendActionResult.Success("이미 친구인 사용자예요.")
                STATUS_PENDING -> FriendActionResult.Success("이미 친구 요청을 보낸 상태예요.")
                else -> FriendActionResult.Failure("친구 요청 상태를 확인하지 못했어요.")
            }
        }

        val now = Timestamp.now()
        val data = mapOf<String, Any?>(
            "memberIds" to listOf(currentUserId, cleanedTargetId).sorted(),
            "requesterId" to currentUserId,
            "receiverId" to cleanedTargetId,
            "requesterName" to (loadUserDisplayName(currentUserId) ?: "사용자"),
            "receiverName" to (loadUserDisplayName(cleanedTargetId) ?: "사용자"),
            "status" to STATUS_PENDING,
            "createdAt" to now,
            "updatedAt" to now,
            "acceptedAt" to null
        )
        friendshipRef.set(data, SetOptions.merge()).await()
        return FriendActionResult.Success("친구 요청을 보냈어요.")
    }

    suspend fun sendFriendRequestByKeyword(
        currentUserId: String,
        keyword: String
    ): FriendActionResult {
        val targetUserId = findUserIdByKeyword(keyword)
            ?: return FriendActionResult.Failure("해당 사용자를 찾을 수 없어요.")
        return sendFriendRequest(currentUserId, targetUserId)
    }

    suspend fun acceptRequest(requestId: String, currentUserId: String): FriendActionResult {
        val ref = db.collection(FirestorePaths.FRIENDSHIPS).document(requestId)
        val doc = ref.get().await()
        if (!doc.exists() || doc.getString("receiverId") != currentUserId) {
            return FriendActionResult.Failure("수락할 친구 요청을 찾을 수 없어요.")
        }

        val now = Timestamp.now()
        ref.update(
            mapOf(
                "status" to STATUS_ACCEPTED,
                "acceptedAt" to now,
                "updatedAt" to now
            )
        ).await()
        return FriendActionResult.Success("친구 요청을 수락했어요.")
    }

    suspend fun rejectRequest(requestId: String, currentUserId: String): FriendActionResult {
        val ref = db.collection(FirestorePaths.FRIENDSHIPS).document(requestId)
        val doc = ref.get().await()
        if (!doc.exists() || doc.getString("receiverId") != currentUserId) {
            return FriendActionResult.Failure("거절할 친구 요청을 찾을 수 없어요.")
        }

        ref.delete().await()
        return FriendActionResult.Success("친구 요청을 거절했어요.")
    }

    suspend fun cancelRequest(requestId: String, currentUserId: String): FriendActionResult {
        val ref = db.collection(FirestorePaths.FRIENDSHIPS).document(requestId)
        val doc = ref.get().await()
        if (!doc.exists() || doc.getString("requesterId") != currentUserId) {
            return FriendActionResult.Failure("취소할 친구 요청을 찾을 수 없어요.")
        }
        if (doc.getString("status") != STATUS_PENDING) {
            return FriendActionResult.Failure("이미 처리된 친구 요청이에요.")
        }

        ref.delete().await()
        return FriendActionResult.Success("친구 요청을 취소했어요.")
    }

    suspend fun removeFriend(currentUserId: String, targetUserId: String): FriendActionResult {
        val cleanedTargetId = targetUserId.trim()
        if (currentUserId.isBlank() || cleanedTargetId.isBlank()) {
            return FriendActionResult.Failure("친구 정보를 확인할 수 없어요.")
        }

        val ref = db.collection(FirestorePaths.FRIENDSHIPS)
            .document(pairId(currentUserId, cleanedTargetId))
        val doc = ref.get().await()
        if (!doc.exists()) {
            return FriendActionResult.Failure("이미 친구 관계가 없어요.")
        }
        if (doc.getString("status") != STATUS_ACCEPTED) {
            return FriendActionResult.Failure("수락된 친구만 해제할 수 있어요.")
        }
        val memberIds = doc.get("memberIds") as? List<*> ?: emptyList<Any>()
        if (currentUserId !in memberIds.filterIsInstance<String>()) {
            return FriendActionResult.Failure("친구 관계를 변경할 권한이 없어요.")
        }

        ref.delete().await()
        return FriendActionResult.Success("친구를 해제했어요.")
    }

    private suspend fun findUserIdByKeyword(keyword: String): String? {
        val cleanedKeyword = keyword.trim()
        if (cleanedKeyword.isBlank()) return null

        val directDoc = db.collection(FirestorePaths.USERS).document(cleanedKeyword).get().await()
        if (directDoc.exists()) return directDoc.id

        listOf("loginId", "email", "nickname", "name").forEach { field ->
            val match = db.collection(FirestorePaths.USERS)
                .whereEqualTo(field, cleanedKeyword)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()
            if (match != null) return match.id
        }
        return null
    }

    private suspend fun loadUserDisplayName(userId: String): String? {
        val doc = runCatching {
            db.collection(FirestorePaths.USERS).document(userId).get().await()
        }.getOrNull()
        return doc?.getString("nickname")
            ?: doc?.getString("name")
            ?: doc?.getString("loginId")?.substringBefore("@")
    }

    private fun DocumentSnapshot.otherUserId(currentUserId: String): String? {
        val memberIds = get("memberIds") as? List<*> ?: return null
        return memberIds.filterIsInstance<String>().firstOrNull { it != currentUserId }
    }

    private fun pairId(firstUserId: String, secondUserId: String): String {
        return listOf(firstUserId, secondUserId).sorted().joinToString("_")
    }

    companion object {
        private const val STATUS_PENDING = "pending"
        private const val STATUS_ACCEPTED = "accepted"
    }
}
