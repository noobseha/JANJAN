package com.gachon.janjan.ui.status

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.gachon.janjan.data.model.ActiveFriend
import com.gachon.janjan.data.model.RecentSession
import com.gachon.janjan.data.model.SessionState
import com.gachon.janjan.data.model.Settlement
import com.gachon.janjan.data.model.SettlementParticipant
import com.gachon.janjan.data.repository.SettlementRepository
import com.gachon.janjan.data.repository.StatusRepository
import com.gachon.janjan.domain.session.repository.FriendRepository
import com.gachon.janjan.domain.session.model.RankingFriendshipStatus
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusViewModel : ViewModel() {
    private val repository = StatusRepository()
    private val settlementRepository = SettlementRepository()
    private val friendRepository = FriendRepository()
    private var lastSessionState: SessionState? = null

    private val _storeInfo = MutableLiveData<String>()
    val storeInfo: LiveData<String> = _storeInfo

    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> = _userName

    private val _mySojuCount = MutableLiveData<Int>(0)
    val mySojuCount: LiveData<Int> = _mySojuCount

    private val _myBeerCount = MutableLiveData<Int>(0)
    val myBeerCount: LiveData<Int> = _myBeerCount

    private val _myExpectedPrice = MutableLiveData<Int>(0)
    val myExpectedPrice: LiveData<Int> = _myExpectedPrice

    private val _myCardColor = MutableLiveData<String>("#FBC02D")
    val myCardColor: LiveData<String> = _myCardColor

    private val _activeFriends = MutableLiveData<List<ActiveFriend>>()
    val activeFriends: LiveData<List<ActiveFriend>> = _activeFriends

    private val _recentSession = MutableLiveData<RecentSession?>()
    val recentSession: LiveData<RecentSession?> = _recentSession

    fun refreshData(sessionId: String, userId: String) {
        repository.checkAndJoinSession(sessionId, userId) {
            repository.getSessionData(sessionId) { sessionData ->
                if (sessionData != null) {
                    lastSessionState = sessionData
                    calculateAndApply(sessionData, userId)
                    viewModelScope.launch {
                        val allParticipantIds = sessionData.participants.map { it.userId }.toSet()
                        val friendContext = friendRepository.loadFriendContext(userId, allParticipantIds)
                        
                        _activeFriends.value = sessionData.participants
                            .filter { it.userId != userId && friendContext.statuses[it.userId] == RankingFriendshipStatus.FRIEND }
                            .sortedByDescending { it.sojuCount + it.beerCount }
                            .take(4)
                            .map {
                                ActiveFriend(
                                    userId = it.userId,
                                    name = it.userName.ifBlank { "참여자" },
                                    storeName = sessionData.storeName,
                                    drinkCount = it.sojuCount + it.beerCount,
                                    isOnline = true
                                )
                            }
                    }
                } else {
                    lastSessionState = null
                    _activeFriends.value = emptyList()
                }
            }
        }

        repository.getRecentSession(userId, sessionId) { recentSession ->
            _recentSession.value = recentSession
        }
    }

    fun startSettlement(sessionId: String, onComplete: (Boolean) -> Unit) {
        repository.startSettlement(sessionId, onComplete)
    }

    private fun calculateAndApply(data: SessionState, myUserId: String) {
        val tableNumber = data.tableNumber.takeIf { it > 0 } ?: data.tableId
        _storeInfo.value = "${data.storeName} · ${tableNumber}번 테이블"

        val sortedParticipants = data.participants.sortedBy { it.joinedAt }
        val myIndex = sortedParticipants.indexOfFirst { it.userId == myUserId }
        val me = sortedParticipants.getOrNull(myIndex)
            ?: sortedParticipants.firstOrNull()
            ?: return

        _userName.value = me.userName
        _mySojuCount.value = me.sojuCount
        _myBeerCount.value = me.beerCount

        val colorHex = me.glassColor ?: when (myIndex) {
            0 -> "#FF5252"
            1 -> "#FF9800"
            2 -> "#FBC02D"
            3 -> "#4CAF50"
            4 -> "#2196F3"
            5 -> "#9C27B0"
            else -> "#CCCCCC"
        }
        _myCardColor.value = colorHex

        val headCount = sortedParticipants.size
        var myPrice = 0

        if (headCount > 0) {
            myPrice += data.totalFoodPrice / headCount
        }
        if (data.totalSojuCount > 0) {
            myPrice += (data.totalSojuPrice * me.sojuCount) / data.totalSojuCount
        }
        if (data.totalBeerCount > 0) {
            myPrice += (data.totalBeerPrice * me.beerCount) / data.totalBeerCount
        }

        _myExpectedPrice.value = myPrice
    }

    fun createSettlementFromCurrentSession(onComplete: (String?) -> Unit) {
        val session = lastSessionState ?: run {
            onComplete(null)
            return
        }

        val sortedParticipants = session.participants.sortedBy { it.joinedAt }
        val headCount = sortedParticipants.size
        val totalPrice = session.totalFoodPrice + session.totalSojuPrice + session.totalBeerPrice

        val settlementParticipants = sortedParticipants.map { participant ->
            var participantPrice = 0
            if (headCount > 0) {
                participantPrice += session.totalFoodPrice / headCount
            }
            if (session.totalSojuCount > 0) {
                participantPrice += (session.totalSojuPrice * participant.sojuCount) / session.totalSojuCount
            }
            if (session.totalBeerCount > 0) {
                participantPrice += (session.totalBeerPrice * participant.beerCount) / session.totalBeerCount
            }

            SettlementParticipant(
                userId = participant.userId,
                userName = participant.userName,
                mytotal = participantPrice,
                beerCupCount = participant.beerCount,
                sojuCupCount = participant.sojuCount,
                paidStatus = false
            )
        }

        val firstJoinedAt = sortedParticipants.firstOrNull()?.joinedAt?.takeIf { it > 0L }
        val timeInfo = if (firstJoinedAt != null) {
            val startMillis = firstJoinedAt * 1000L
            val startTime = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(startMillis))
            val diffMillis = System.currentTimeMillis() - startMillis
            val hours = diffMillis / (1000 * 60 * 60)
            val minutes = (diffMillis % (1000 * 60 * 60)) / (1000 * 60)
            val duration = if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
            "$startTime 시작 · $duration · ${headCount}명"
        } else {
            "시작 시간 정보 없음 · ${headCount}명"
        }

        val settlement = Settlement(
            sessionId = session.sessionId,
            storeName = session.storeName,
            tableId = session.tableNumber.takeIf { it > 0 } ?: session.tableId,
            totalPrice = totalPrice,
            timeInfo = timeInfo,
            participants = settlementParticipants
        )

        settlementRepository.createSettlement(settlement, onComplete)
    }
}
