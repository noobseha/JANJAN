package com.gachon.janjan.ui.status

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.gachon.janjan.data.model.ActiveFriend
import com.gachon.janjan.data.model.RecentSession
import com.gachon.janjan.data.model.SessionState
import com.gachon.janjan.data.repository.StatusRepository

class StatusViewModel : ViewModel() {

    // 레포지토리 장착! (Firebase에서 데이터 가져오기 위함)
    private val repository = StatusRepository()

    // --- 상단 노란 박스용 LiveData ---
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


    // 🔄 새로고침 (데이터 다시 불러오기)
    fun refreshData(sessionId: String, userId: String) {

        // 1. [상단 박스] 레포지토리를 통해 Firebase에서 진짜 데이터 가져오기
        repository.getSessionData(sessionId) { sessionData ->
            if (sessionData != null) {
                calculateAndApply(sessionData, userId)
                _activeFriends.value = sessionData.participants
                    .filter { it.userId != userId }
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
            } else {
                _activeFriends.value = emptyList()
            }
        }

        // 2. [최근 술자리] 현재 세션을 제외한 내 참여 기록에서 가장 최근 세션 조회
        repository.getRecentSession(userId, sessionId) { recentSession ->
            _recentSession.value = recentSession
        }
    }

    fun startSettlement(sessionId: String, onComplete: (Boolean) -> Unit) {
        repository.startSettlement(sessionId, onComplete)
    }

    // 🧮 정산 계산 및 색상 적용 로직
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

        // 색상 배정 (순서대로 빨주노초파보)
        val colorHex = me.glassColor ?: when(myIndex) {
            0 -> "#FF5252"
            1 -> "#FF9800"
            2 -> "#FBC02D"
            3 -> "#4CAF50"
            4 -> "#2196F3"
            5 -> "#9C27B0"
            else -> "#CCCCCC"
        }
        _myCardColor.value = colorHex

        // 금액 계산 로직
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
}
