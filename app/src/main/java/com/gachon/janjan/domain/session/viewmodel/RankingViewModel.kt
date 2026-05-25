package com.gachon.janjan.domain.session.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.RankingDrinkFilter
import com.gachon.janjan.domain.session.model.RankingPeriod
import com.gachon.janjan.domain.session.model.RankingPeriodData
import com.gachon.janjan.domain.session.model.RankingStoreStat
import com.gachon.janjan.domain.session.model.RankingUiState
import com.gachon.janjan.domain.session.model.RankingUserStat
import com.gachon.janjan.domain.session.repository.RankingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RankingViewModel(
    private val repository: RankingRepository = RankingRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    private var cachedRankings: Map<RankingPeriod, RankingPeriodData> = emptyMap()

    init {
        loadRankings()
    }

    fun loadRankings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            runCatching {
                ensureSignedIn()
                repository.loadRankings(currentUserId)
            }.onSuccess { rankings ->
                cachedRankings = rankings
                publishState()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "랭킹을 불러오지 못했어요: ${error.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    fun selectPeriod(period: RankingPeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
        publishState()
    }

    fun selectFilter(filter: RankingDrinkFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        publishState()
    }

    private fun publishState() {
        val current = _uiState.value
        val periodData = cachedRankings[current.selectedPeriod] ?: RankingPeriodData()
        val users = rankUsers(periodData.users, current.selectedFilter)
        val stores = rankStores(periodData.stores, current.selectedFilter)
        _uiState.value = current.copy(
            isLoading = false,
            users = users,
            stores = stores,
            myRank = users.firstOrNull { it.isMe },
            message = null
        )
    }

    private fun rankUsers(
        users: List<RankingUserStat>,
        filter: RankingDrinkFilter
    ): List<RankingUserStat> =
        users.filter { it.countFor(filter) > 0 }
            .sortedWith(compareByDescending<RankingUserStat> { it.countFor(filter) }.thenBy { it.userName })
            .mapIndexed { index, stat -> stat.copy(rank = index + 1) }

    private fun rankStores(
        stores: List<RankingStoreStat>,
        filter: RankingDrinkFilter
    ): List<RankingStoreStat> =
        stores.filter { it.countFor(filter) > 0 }
            .sortedWith(compareByDescending<RankingStoreStat> { it.countFor(filter) }.thenBy { it.storeName })
            .mapIndexed { index, stat -> stat.copy(rank = index + 1) }

    private val currentUserId: String
        get() = FirebaseConfig.auth.currentUser?.uid ?: PENDING_USER_ID

    private suspend fun ensureSignedIn() {
        if (FirebaseConfig.auth.currentUser == null) {
            FirebaseConfig.auth.signInAnonymously().await()
        }
    }

    companion object {
        private const val PENDING_USER_ID = "anonymous_pending_user"
    }
}
