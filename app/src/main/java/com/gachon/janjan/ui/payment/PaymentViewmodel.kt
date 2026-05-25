package com.gachon.janjan.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gachon.janjan.data.repository.PaymentRepository
import kotlinx.coroutines.launch

class PaymentViewmodel(
    private val repository: PaymentRepository = PaymentRepository()
) : ViewModel() {
    fun completeSettlement(sessionId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.completeSettlement(sessionId) }
                .onSuccess { onComplete(true) }
                .onFailure { onComplete(false) }
        }
    }
}
