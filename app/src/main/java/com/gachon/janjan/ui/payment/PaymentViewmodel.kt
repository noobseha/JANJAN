package com.gachon.janjan.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gachon.janjan.data.repository.PaymentRepository
import com.gachon.janjan.domain.session.repository.RankingAggregationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class PaymentViewmodel(
    private val repository: PaymentRepository = PaymentRepository(),
    private val rankingAggregationRepository: RankingAggregationRepository = RankingAggregationRepository()
) : ViewModel() {
    fun completeSettlement(
        sessionId: String,
        paymentMethod: String = "앱 결제",
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                if (userId.isBlank()) error("로그인 정보를 확인하지 못했습니다.")
                val result = repository.completeSettlement(sessionId, userId, paymentMethod)
                if (result.sessionClosed) {
                    rankingAggregationRepository.aggregateClosedSession(sessionId)
                }
            }
                .onSuccess { onComplete(true) }
                .onFailure { onComplete(false) }
        }
    }
}
