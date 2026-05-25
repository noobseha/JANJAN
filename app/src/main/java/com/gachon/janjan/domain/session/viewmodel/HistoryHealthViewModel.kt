package com.gachon.janjan.domain.session.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.DrinkHistoryItem
import com.gachon.janjan.domain.session.model.HealthSummary
import com.gachon.janjan.domain.session.repository.HistoryHealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HistoryHealthViewModel(
    private val repository: HistoryHealthRepository = HistoryHealthRepository()
) : ViewModel() {
    private val _histories = MutableStateFlow<List<DrinkHistoryItem>>(emptyList())
    val histories: StateFlow<List<DrinkHistoryItem>> = _histories.asStateFlow()

    private val _healthSummary = MutableStateFlow<HealthSummary?>(null)
    val healthSummary: StateFlow<HealthSummary?> = _healthSummary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val currentUserId: String
        get() = FirebaseConfig.auth.currentUser?.uid ?: PENDING_USER_ID

    fun load(userId: String = currentUserId) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ensureSignedIn()
                val resolvedUserId = FirebaseConfig.auth.currentUser?.uid ?: userId
                repository.getMyHistory(resolvedUserId) to repository.getHealthSummary(resolvedUserId)
            }.onSuccess { (history, health) ->
                _histories.value = history
                _healthSummary.value = health
            }
            _isLoading.value = false
        }
    }

    fun removeHistoryLocally(sessionId: String) {
        _histories.value = _histories.value.filterNot { it.sessionId == sessionId }
    }

    private suspend fun ensureSignedIn() {
        if (FirebaseConfig.auth.currentUser == null) {
            FirebaseConfig.auth.signInAnonymously().await()
        }
    }

    companion object {
        private const val PENDING_USER_ID = "anonymous_pending_user"
    }
}
