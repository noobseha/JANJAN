package com.gachon.janjan.ui.status

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.gachon.janjan.data.model.ActiveFriend
import com.gachon.janjan.data.model.RecentSession
import com.gachon.janjan.data.model.SessionState
import com.gachon.janjan.data.model.Settlement
import com.gachon.janjan.data.model.SettlementParticipant
import com.gachon.janjan.data.repository.StatusRepository
import com.gachon.janjan.data.repository.SettlementRepository

class StatusViewModel : ViewModel() {

    // 레포지토리 장착! (Firebase에서 데이터 가져오기 위함)
    private val repository = StatusRepository()
    private val settlementRepository = SettlementRepository()

    // 최근 로드된 세션 데이터를 저장하기 위한 임시 변수
    private var lastSessionState: SessionState? = null

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

        // 0. [세션 참가] 사용자가 세션에 속해있지 않으면 자동 참가 등록
        repository.checkAndJoinSession(sessionId, userId) {
            // 1. [상단 박스] 레포지토리를 통해 Firebase에서 진짜 데이터 가져오기
            repository.getSessionData(sessionId) { sessionData ->
                if (sessionData != null) {
                    lastSessionState = sessionData
                    calculateAndApply(sessionData, userId)
                }
            }
        }

        // 2. [친구 목록] 더미 데이터 세팅 (나중에 이것도 레포지토리로 뺄 예정)
        val friendList = listOf(
            ActiveFriend("user_111", "맥주마스터", "강남 이자카야", 5, true),
            ActiveFriend("user_333", "소주요정", "건대 포차", 3, true),
            ActiveFriend("user_222", "안주킬러", "홍대 맛집", 1, false),
            ActiveFriend("user_444", "잠만보", "집", 0, true)
        )
        _activeFriends.value = friendList

        // 3. [최근 술자리] 더미 데이터 세팅 (나중에 레포지토리로 교체)
        val pastSession = RecentSession(
            sessionId = "session_000",
            storeName = "홍대 포차",
            date = "2026.01.28", // 어제 날짜로!
            headCount = 4,
            totalPrice = 34000
        )
        _recentSession.value = pastSession
    }

    // 🧮 정산 계산 및 색상 적용 로직
    private fun calculateAndApply(data: SessionState, myUserId: String) {
        _storeInfo.value = "${data.storeName} · ${data.tableId}번 테이블"

        val sortedParticipants = data.participants.sortedBy { it.joinedAt }
        val myIndex = sortedParticipants.indexOfFirst { it.userId == myUserId }
        val me = sortedParticipants.getOrNull(myIndex) ?: return

        _userName.value = me.userName ?: ""
        _mySojuCount.value = me.sojuCupCount
        _myBeerCount.value = me.beerCupCount

        // 색상 배정 (순서대로 빨주노초파보)
        val colorHex = when(myIndex) {
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

        val totalSojuCups = sortedParticipants.sumOf { it.sojuCupCount }
        val totalBeerCups = sortedParticipants.sumOf { it.beerCupCount }

        if (headCount > 0) {
            myPrice += data.totalFoodPrice / headCount
        }
        if (totalSojuCups > 0) {
            myPrice += (data.totalSojuPrice * me.sojuCupCount) / totalSojuCups
        }
        if (totalBeerCups > 0) {
            myPrice += (data.totalBeerPrice * me.beerCupCount) / totalBeerCups
        }

        _myExpectedPrice.value = myPrice
    }

    // 🔥 현재 활성화된 세션 데이터를 기반으로 정산 문서(Settlement) 생성 및 Firebase 업로드
    fun createSettlementFromCurrentSession(onComplete: (String?) -> Unit) {
        val session = lastSessionState ?: run {
            onComplete(null)
            return
        }

        val sortedParticipants = session.participants.sortedBy { it.joinedAt }
        val headCount = sortedParticipants.size

        val totalSojuCups = sortedParticipants.sumOf { it.sojuCupCount }
        val totalBeerCups = sortedParticipants.sumOf { it.beerCupCount }

        val settlementParticipants = sortedParticipants.map { participant ->
            var myPrice = 0
            if (headCount > 0) {
                myPrice += session.totalFoodPrice / headCount
            }
            if (totalSojuCups > 0) {
                myPrice += (session.totalSojuPrice * participant.sojuCupCount) / totalSojuCups
            }
            if (totalBeerCups > 0) {
                myPrice += (session.totalBeerPrice * participant.beerCupCount) / totalBeerCups
            }

            SettlementParticipant(
                userId = participant.userId,
                userName = participant.userName,
                mytotal = myPrice,
                beerCupCount = participant.beerCupCount,
                sojuCupCount = participant.sojuCupCount,
                paidStatus = false
            )
        }

        val totalPrice = session.totalFoodPrice + session.totalSojuPrice + session.totalBeerPrice

        // 시간 정보 계산 (시작 시간, 정산 시점까지의 경과 시간, 참가자 수)
        val firstJoinedAt = sortedParticipants.firstOrNull()?.joinedAt
        val formattedTimeInfo = if (firstJoinedAt != null) {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
            val startTimeStr = sdf.format(firstJoinedAt.toDate())

            val startMillis = firstJoinedAt.toDate().time
            val diffMillis = System.currentTimeMillis() - startMillis
            val hours = diffMillis / (1000 * 60 * 60)
            val minutes = (diffMillis % (1000 * 60 * 60)) / (1000 * 60)

            val durationStr = if (hours > 0) {
                "${hours}시간 ${minutes}분"
            } else {
                "${minutes}분"
            }

            "${startTimeStr} 시작 · ${durationStr} · ${headCount}명"
        } else {
            "시작 시간 정보 없음 · ${headCount}명"
        }

        val newSettlement = Settlement(
            sessionId = session.sessionId,
            storeName = session.storeName,
            tableId = session.tableId,
            totalPrice = totalPrice,
            timeInfo = formattedTimeInfo,
            participants = settlementParticipants
        )

        settlementRepository.createSettlement(newSettlement, onComplete)
    }
}