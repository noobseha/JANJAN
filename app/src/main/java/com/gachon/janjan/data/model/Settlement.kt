package com.gachon.janjan.data.model

data class Settlement(
    val settlementId: String = "",  // 정산 문서 ID
    val sessionId: String = "",     // 연결된 세션 ID
    val storeName: String = "",     // 가게 이름 (예: "홍대 포차")
    val tableId: Int = 0,           // 테이블 번호
    val totalPrice: Int = 0,        // 오늘 술자리 총 금액
    val timeInfo: String = "",      // 시간 정보 (예: "17:30 시작 · 3시간 30분 · 4명")
    val participants: List<SettlementParticipant> = emptyList() // 정산 대상자 목록
)

data class SettlementParticipant(
    val userId: String = "",
    val userName: String = "",
    val mytotal: Int = 0,            // 이 사람이 개별적으로 내야 할 금액 (계산된 결과값)
    val beerCupCount: Int = 0,       // 맥주잔 수
    val sojuCupCount: Int = 0,       // 소주잔 수
    val paidStatus: Boolean = false, // 송금 완료 여부 (기본값 false)
    val pendingApproval: Boolean = false,
    val paymentMethod: String = ""
)
