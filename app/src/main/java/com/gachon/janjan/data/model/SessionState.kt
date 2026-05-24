package com.gachon.janjan.data.model

import com.google.firebase.Timestamp
import kotlin.time.Instant

// 현재 테이블(세션)의 실시간 상태를 통째로 담는 박스
data class SessionState(
    val sessionId: String = "",
    val storeName: String = "",
    val tableId: Int = 0,
    val totalFoodPrice: Int = 0, // 전체 안주 금액
    val totalSojuPrice: Int = 0, // 전체 소주 금액
    val totalBeerPrice: Int = 0, // 전체 맥주 금액
    val totalSojuCount: Int = 0, // 이 테이블에서 마신 소주 총 잔 수
    val totalBeerCount: Int = 0, // 이 테이블에서 마신 맥주 총 잔 수
    val participants: List<Participant> = emptyList() // 참가자 목록 (배열)
)

// 참가자 1명의 정보
data class Participant(
    val userId: String = "",
    val userName: String = "",
    val sojuCupCount: Int = 0, // 내가 마신 소주 잔 수
    val beerCupCount: Int = 0,  // 내가 마신 맥주 잔 수
    val joinedAt: Timestamp = Timestamp.now()
)