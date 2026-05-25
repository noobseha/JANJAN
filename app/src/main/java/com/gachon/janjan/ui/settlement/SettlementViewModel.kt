package com.gachon.janjan.ui.settlement

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.gachon.janjan.data.model.Settlement
import com.gachon.janjan.data.repository.SettlementRepository
import com.google.firebase.firestore.ListenerRegistration

class SettlementViewModel : ViewModel() {
    private val repository = SettlementRepository()
    private var listenerRegistration: ListenerRegistration? = null

    private val _settlementData = MutableLiveData<Settlement?>()
    val settlementData: LiveData<Settlement?> = _settlementData

    // 실시간으로 특정 정산 데이터 구독 시작
    fun startObservingSettlement(settlementId: String) {
        // 기존 구독 해제
        listenerRegistration?.remove()

        listenerRegistration = repository.observeSettlement(settlementId) { settlement ->
            _settlementData.value = settlement
        }
    }

    // 특정 유저의 송금 여부 변경 (총무가 승인/취소할 때 사용)
    fun togglePaidStatus(settlementId: String, userId: String, currentStatus: Boolean) {
        repository.updatePaidStatus(settlementId, userId, !currentStatus) { success ->
            // 필요 시 UI에 성공 여부 표시 등 처리
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 뷰모델 파괴 시 실시간 구독 리스너 해제 (메모리 누수 방지)
        listenerRegistration?.remove()
    }
}
