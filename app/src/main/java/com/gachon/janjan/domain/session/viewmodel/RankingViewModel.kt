package com.gachon.janjan.domain.session.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.FriendActionResult
import com.gachon.janjan.domain.session.model.FriendRequest
import com.gachon.janjan.domain.session.model.RankingDrinkFilter
import com.gachon.janjan.domain.session.model.RankingPeriod
import com.gachon.janjan.domain.session.model.RankingPeriodData
import com.gachon.janjan.domain.session.model.RankingStoreOption
import com.gachon.janjan.domain.session.model.RankingStoreStat
import com.gachon.janjan.domain.session.model.RankingUiState
import com.gachon.janjan.domain.session.model.RankingUserStat
import com.gachon.janjan.domain.session.model.SentFriendRequest
import com.gachon.janjan.domain.session.repository.FriendRepository
import com.gachon.janjan.domain.session.repository.RankingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RankingViewModel(
    private val repository: RankingRepository = RankingRepository(),
    private val friendRepository: FriendRepository = FriendRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    private var cachedRankings: Map<RankingPeriod, RankingPeriodData> = emptyMap()
    private var storeSearchJob: Job? = null

    init {
        loadRankings()
    }

    fun loadRankings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            runCatching {
                ensureSignedIn()
                loadRankingBundle()
            }.onSuccess { result ->
                cachedRankings = result.rankings
                _uiState.value = _uiState.value.copy(
                    incomingFriendRequests = result.incomingRequests,
                    outgoingFriendRequests = result.outgoingRequests,
                    storeOptions = result.storeOptions
                )
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

    fun selectStore(storeId: String?) {
        _uiState.value = _uiState.value.copy(selectedStoreId = storeId)
        publishState()
    }

    fun searchStores(query: String) {
        storeSearchJob?.cancel()
        val cleanedQuery = query.trim()
        if (cleanedQuery.length < 2) {
            _uiState.value = _uiState.value.copy(storeOptions = emptyList(), selectedStoreId = null)
            publishState()
            return
        }

        storeSearchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            delay(STORE_SEARCH_DEBOUNCE_MS)
            runCatching {
                ensureSignedIn()
                repository.searchStoreOptions(cleanedQuery)
            }.onSuccess { stores ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    storeOptions = stores
                )
                publishState()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    storeOptions = emptyList(),
                    message = "가게 검색 실패: ${error.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    fun sendFriendRequest(targetUserId: String) {
        runFriendAction {
            friendRepository.sendFriendRequest(currentUserId, targetUserId)
        }
    }

    fun sendFriendRequestByKeyword(keyword: String) {
        runFriendAction {
            friendRepository.sendFriendRequestByKeyword(currentUserId, keyword)
        }
    }

    fun acceptFriendRequest(requestId: String) {
        runFriendAction {
            friendRepository.acceptRequest(requestId, currentUserId)
        }
    }

    fun rejectFriendRequest(requestId: String) {
        runFriendAction {
            friendRepository.rejectRequest(requestId, currentUserId)
        }
    }

    fun cancelFriendRequest(requestId: String) {
        runFriendAction {
            friendRepository.cancelRequest(requestId, currentUserId)
        }
    }

    fun removeFriend(targetUserId: String) {
        runFriendAction {
            friendRepository.removeFriend(currentUserId, targetUserId)
        }
    }

    private fun publishState(message: String? = _uiState.value.message) {
        val current = _uiState.value
        val periodData = cachedRankings[current.selectedPeriod] ?: RankingPeriodData()
        val users = rankUsers(periodData.users, current.selectedFilter)
        val stores = rankStores(periodData.stores, current.selectedFilter)
        val storeUsers = rankUsers(
            periodData.storeUsersByStoreId[current.selectedStoreId].orEmpty(),
            current.selectedFilter
        )
        _uiState.value = current.copy(
            isLoading = false,
            users = users,
            stores = stores,
            storeUsers = storeUsers,
            myRank = users.firstOrNull { it.isMe },
            message = message
        )
    }

    private suspend fun loadRankingBundle(): RankingLoadResult =
        RankingLoadResult(
            rankings = repository.loadRankings(currentUserId),
            incomingRequests = friendRepository.loadIncomingRequests(currentUserId),
            outgoingRequests = friendRepository.loadOutgoingRequests(currentUserId),
            storeOptions = repository.loadStoreOptions()
        )

    private fun runFriendAction(action: suspend () -> FriendActionResult) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            runCatching {
                ensureSignedIn()
                action()
            }.onSuccess { result ->
                val message = when (result) {
                    is FriendActionResult.Success -> result.message
                    is FriendActionResult.Failure -> result.message
                }
                runCatching {
                    loadRankingBundle()
                }.onSuccess { refreshed ->
                    cachedRankings = refreshed.rankings
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        incomingFriendRequests = refreshed.incomingRequests,
                        outgoingFriendRequests = refreshed.outgoingRequests,
                        storeOptions = refreshed.storeOptions
                    )
                    publishState(message)
                }.onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, message = message)
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "친구 요청 처리 실패: ${error.message ?: "알 수 없는 오류"}"
                )
            }
        }
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
        private const val STORE_SEARCH_DEBOUNCE_MS = 300L
    }

    private data class RankingLoadResult(
        val rankings: Map<RankingPeriod, RankingPeriodData>,
        val incomingRequests: List<FriendRequest>,
        val outgoingRequests: List<SentFriendRequest>,
        val storeOptions: List<RankingStoreOption>
    )
}
