package com.gachon.janjan.domain.session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gachon.janjan.domain.session.model.FriendRequest
import com.gachon.janjan.domain.session.model.RankingDrinkFilter
import com.gachon.janjan.domain.session.model.RankingFriendshipStatus
import com.gachon.janjan.domain.session.model.RankingPeriod
import com.gachon.janjan.domain.session.model.RankingUiState
import com.gachon.janjan.domain.session.model.RankingUserStat
import com.gachon.janjan.domain.session.model.SentFriendRequest
import com.gachon.janjan.domain.session.viewmodel.RankingViewModel
import com.gachon.janjan.MenuItem
import com.google.firebase.firestore.FirebaseFirestore

private val RankingMint = Color(0xFF4DB8A4)
private val RankingMintDark = Color(0xFF3A9A88)
private val RankingBgLight = Color(0xFFE8F5F2)
private val RankingFieldBg = Color(0xFFF5F5F5)
private val RankingTextMain = Color(0xFF333333)
private val RankingTextSub = Color(0xFF666666)
private val RankingTextDim = Color(0xFF9E9E9E)
private val RankingError = Color(0xFFEF4444)
private val RankGold = Color(0xFFFFC107)
private val RankGoldDark = Color(0xFFE6A800)
private val RankSilver = Color(0xFFB0BEC5)
private val RankSilverDark = Color(0xFF90A4AE)
private val RankBronze = Color(0xFFFF7043)
private val RankBronzeDark = Color(0xFFE64A19)
private const val RankingSampleMinCount = 15

private enum class RankingMainTab(val label: String) {
    DAILY("일간"),
    WEEKLY("주간"),
    MONTHLY("월간"),
    STORE("가게별"),
    FRIEND("친구")
}

private enum class MyRankPin {
    TOP,
    BOTTOM
}

private data class RankingStoreUi(
    val id: String,
    val rankingStoreId: String = id,
    val externalId: String = "",
    val name: String,
    val address: String,
    val category: String,
    val phone: String
)

private data class FriendRequestUi(
    val id: String,
    val nickname: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    rankingViewModel: RankingViewModel
) {
    val state by rankingViewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(RankingMainTab.WEEKLY) }
    var storeQuery by remember { mutableStateOf("") }
    var selectedStore by remember { mutableStateOf<RankingStoreUi?>(null) }
    var sheetStore by remember { mutableStateOf<RankingStoreUi?>(null) }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var friendInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        rankingViewModel.loadRankings()
    }

    sheetStore?.let { store ->
        ModalBottomSheet(
            onDismissRequest = { sheetStore = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White
        ) {
            StoreBottomSheetContent(store = store)
        }
    }

    if (showAddFriendDialog) {
        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            title = { Text("친구 추가", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = friendInput,
                    onValueChange = { friendInput = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    placeholder = { Text("상대방 아이디를 입력하세요") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rankingViewModel.sendFriendRequestByKeyword(friendInput)
                        friendInput = ""
                        showAddFriendDialog = false
                    }
                ) {
                    Text("요청 보내기", color = RankingMint)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriendDialog = false }) {
                    Text("취소", color = RankingTextSub)
                }
            }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val maxContentWidth = when {
            maxWidth < 360.dp -> maxWidth
            maxWidth < 600.dp -> 480.dp
            maxWidth < 840.dp -> 560.dp
            else -> 640.dp
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = maxContentWidth)
                .background(Color.White)
        ) {
            RankingTitleBar(
                isLoading = state.isLoading,
                onRefresh = rankingViewModel::loadRankings
            )
            RankingMainTabs(
                selectedTab = selectedTab,
                onTabClick = { tab ->
                    selectedTab = tab
                    when (tab) {
                        RankingMainTab.DAILY -> rankingViewModel.selectPeriod(RankingPeriod.DAILY)
                        RankingMainTab.WEEKLY -> rankingViewModel.selectPeriod(RankingPeriod.WEEKLY)
                        RankingMainTab.MONTHLY -> rankingViewModel.selectPeriod(RankingPeriod.MONTHLY)
                        RankingMainTab.STORE,
                        RankingMainTab.FRIEND -> Unit
                    }
                }
            )

            when (selectedTab) {
                RankingMainTab.DAILY,
                RankingMainTab.WEEKLY,
                RankingMainTab.MONTHLY -> TimeRankingTab(
                    state = state,
                    onFilterChange = rankingViewModel::selectFilter,
                    onAddFriend = { user -> rankingViewModel.sendFriendRequest(user.userId) }
                )

                RankingMainTab.STORE -> StoreRankingTab(
                    state = state,
                    storeOptions = state.storeOptions.map {
                        RankingStoreUi(
                            id = it.id,
                            rankingStoreId = it.rankingStoreId,
                            externalId = it.externalId,
                            name = it.name,
                            address = it.address,
                            category = it.category,
                            phone = it.phone
                        )
                    },
                    storeQuery = storeQuery,
                    selectedStore = selectedStore,
                    onQueryChange = {
                        storeQuery = it
                        selectedStore = null
                        rankingViewModel.selectStore(null)
                        rankingViewModel.searchStores(it)
                    },
                    onStoreSearch = {
                        rankingViewModel.searchStores(storeQuery)
                    },
                    onStoreSelected = {
                        selectedStore = it
                        rankingViewModel.selectStore(it.rankingStoreId.takeIf { storeId -> storeId.isNotBlank() })
                    },
                    onSelectedStoreClick = { sheetStore = it },
                    onFilterChange = rankingViewModel::selectFilter,
                    onAddFriend = { user -> rankingViewModel.sendFriendRequest(user.userId) }
                )

                RankingMainTab.FRIEND -> FriendRankingTab(
                    state = state,
                    onFilterChange = rankingViewModel::selectFilter,
                    onSearchFriend = { showAddFriendDialog = true },
                    onAcceptRequest = { request -> rankingViewModel.acceptFriendRequest(request.id) },
                    onRejectRequest = { request -> rankingViewModel.rejectFriendRequest(request.id) },
                    onCancelRequest = { request -> rankingViewModel.cancelFriendRequest(request.id) },
                    onRemoveFriend = { user -> rankingViewModel.removeFriend(user.userId) }
                )
            }
        }
    }
}

@Composable
private fun RankingTitleBar(
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "랭킹",
            color = RankingTextMain,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        IconButton(
            onClick = onRefresh,
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = RankingMint,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "새로고침", tint = RankingTextDim)
            }
        }
    }
}

@Composable
private fun RankingMainTabs(
    selectedTab: RankingMainTab,
    onTabClick: (RankingMainTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankingMainTab.entries.forEach { tab ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clickable { onTabClick(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    tab.label,
                    color = if (selectedTab == tab) RankingMint else RankingTextDim,
                    fontSize = 14.sp,
                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .fillMaxWidth()
                        .background(if (selectedTab == tab) RankingMint else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun TimeRankingTab(
    state: RankingUiState,
    onFilterChange: (RankingDrinkFilter) -> Unit,
    onAddFriend: (RankingUserStat) -> Unit
) {
    val listState = rememberLazyListState()
    val users = displayUsers(
        users = state.users,
        filter = state.selectedFilter,
        samples = sampleTimeRanking(state.selectedFilter, state.selectedPeriod)
    )
    val myRank = users.firstOrNull { it.isMe } ?: state.myRank
    val myPin by remember {
        derivedStateOf {
            myRankPinFor(
                listState = listState,
                myRank = myRank,
                hasMessage = state.message != null
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (myPin == null) 12.dp else 80.dp)
        ) {
            item {
                RankingFilterRow(
                    selectedFilter = state.selectedFilter,
                    onFilterChange = onFilterChange
                )
            }

            state.message?.let { message ->
                item { RankingMessage(message) }
            }

            item {
                TopRankSection(
                    users = users.take(3),
                    filter = state.selectedFilter,
                    onAddFriend = onAddFriend
                )
            }

            if (users.isEmpty() && !state.isLoading) {
                item { RankingEmptyBlock("아직 기록된 잔 수가 없습니다.") }
            } else {
                items(users.drop(3), key = { it.userId }) { item ->
                    RankingUserListRow(
                        item = item,
                        filter = state.selectedFilter,
                        onAddFriend = { onAddFriend(item) }
                    )
                }
            }
        }

        myPin?.let { pin ->
            myRank?.let { item ->
                MyRankPinnedRow(
                    item = item,
                    filter = state.selectedFilter,
                    modifier = Modifier
                        .align(if (pin == MyRankPin.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun StoreRankingTab(
    state: RankingUiState,
    storeOptions: List<RankingStoreUi>,
    storeQuery: String,
    selectedStore: RankingStoreUi?,
    onQueryChange: (String) -> Unit,
    onStoreSearch: () -> Unit,
    onStoreSelected: (RankingStoreUi) -> Unit,
    onSelectedStoreClick: (RankingStoreUi) -> Unit,
    onFilterChange: (RankingDrinkFilter) -> Unit,
    onAddFriend: (RankingUserStat) -> Unit
) {
    val displayStoreOptions = remember(storeOptions) {
        storeOptions.withSampleStores()
    }
    val searchResults = remember(storeQuery, displayStoreOptions) {
        val query = storeQuery.trim()
        val digits = query.filter { it.isDigit() }
        if (query.length < 2) {
            displayStoreOptions
        } else {
            displayStoreOptions.filter { store ->
                store.name.contains(query, ignoreCase = true) ||
                    store.address.contains(query, ignoreCase = true) ||
                    store.category.contains(query, ignoreCase = true) ||
                    (digits.isNotBlank() && store.phone.filter { it.isDigit() }.contains(digits))
            }
        }
    }
    val rankingUsers = displayUsers(
        users = state.storeUsers,
        filter = state.selectedFilter,
        samples = sampleStoreRanking(state.selectedFilter, selectedStore?.rankingStoreId)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item {
            RankingFilterRow(
                selectedFilter = state.selectedFilter,
                onFilterChange = onFilterChange
            )
        }
        state.message?.let { message ->
            item { RankingMessage(message) }
        }
        item {
            StoreSearchRow(
                query = storeQuery,
                onQueryChange = onQueryChange,
                onSearch = onStoreSearch
            )
        }

        if (selectedStore == null) {
            if (searchResults.isNotEmpty()) {
                items(searchResults, key = { it.id }) { store ->
                    StoreSearchResultRow(
                        store = store,
                        onClick = { onStoreSelected(store) }
                    )
                }
            } else {
                item {
                    StoreEmptyState(
                        text = if (storeQuery.trim().isEmpty()) {
                            "가게 이름을 검색해주세요"
                        } else {
                            "검색 결과가 없습니다"
                        }
                    )
                }
            }
        } else {
            item {
                SelectedStoreCard(
                    store = selectedStore,
                    onClick = { onSelectedStoreClick(selectedStore) }
                )
            }
            item {
                TopRankSection(
                    users = rankingUsers.take(3),
                    filter = state.selectedFilter,
                    onAddFriend = onAddFriend
                )
            }
            if (rankingUsers.isEmpty() && !state.isLoading) {
                item { RankingEmptyBlock("이 가게의 기록이 아직 없습니다.") }
            } else {
                items(rankingUsers.drop(3), key = { "store-${it.userId}" }) { item ->
                    RankingUserListRow(
                        item = item,
                        filter = state.selectedFilter,
                        onAddFriend = { onAddFriend(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRankingTab(
    state: RankingUiState,
    onFilterChange: (RankingDrinkFilter) -> Unit,
    onSearchFriend: () -> Unit,
    onAcceptRequest: (FriendRequest) -> Unit,
    onRejectRequest: (FriendRequest) -> Unit,
    onCancelRequest: (SentFriendRequest) -> Unit,
    onRemoveFriend: (RankingUserStat) -> Unit
) {
    val listState = rememberLazyListState()
    val friendRequests = state.incomingFriendRequests
    val sentRequests = state.outgoingFriendRequests
    val friends = state.users.filter { it.friendshipStatus == RankingFriendshipStatus.FRIEND && !it.isMe }
    val myRank = state.myRank

    val rankingUsers = remember(friends, myRank, state.selectedFilter) {
        val list = friends + listOfNotNull(myRank)
        displayUsers(
            users = list,
            filter = state.selectedFilter,
            samples = sampleFriendRanking(state.selectedFilter)
        )
    }

    val myPin by remember(rankingUsers) {
        derivedStateOf {
            val messageCount = if (state.message != null) 1 else 0
            val incomingCount = if (friendRequests.isNotEmpty()) 1 + friendRequests.size else 0
            val outgoingCount = if (sentRequests.isNotEmpty()) 1 + sentRequests.size else 0
            val headerCount = 1 + messageCount + incomingCount + outgoingCount + 1 + 1 
            myRankPinForFriend(listState, rankingUsers.find { it.isMe }, headerCount, rankingUsers)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (myPin == null) 12.dp else 80.dp)
        ) {
            item {
                RankingFilterRow(
                    selectedFilter = state.selectedFilter,
                    onFilterChange = onFilterChange
                )
            }
            state.message?.let { message ->
                item { RankingMessage(message) }
            }

            if (friendRequests.isNotEmpty()) {
                item {
                    Text(
                        "받은 친구 요청 (${friendRequests.size})",
                        color = RankingTextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                items(friendRequests, key = { it.id }) { request ->
                    FriendRequestRow(
                        request = request,
                        onAccept = { onAcceptRequest(request) },
                        onReject = { onRejectRequest(request) }
                    )
                }
            }

            if (sentRequests.isNotEmpty()) {
                item {
                    Text(
                        "보낸 친구 요청 (${sentRequests.size})",
                        color = RankingTextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                items(sentRequests, key = { it.id }) { request ->
                    SentFriendRequestRow(
                        request = request,
                        onCancel = { onCancelRequest(request) }
                    )
                }
            }

            item {
                FriendHeader(
                    count = friends.size,
                    onAddFriend = onSearchFriend
                )
            }

            item {
                TopRankSection(
                    users = rankingUsers.take(3),
                    filter = state.selectedFilter,
                    onAddFriend = { }
                )
            }

            if (rankingUsers.isEmpty() && !state.isLoading) {
                item { RankingEmptyBlock("친구의 기록이 아직 없습니다.") }
            } else {
                items(rankingUsers.drop(3), key = { "friend-${it.userId}" }) { item ->
                    val isSample = item.isSampleRankingUser()
                    RankingUserListRow(
                        item = item,
                        filter = state.selectedFilter,
                        friendLabelOverride = when {
                            item.isMe -> null
                            isSample -> "친구"
                            else -> "삭제"
                        },
                        onAddFriend = if (item.isMe || isSample) null else { { onRemoveFriend(item) } }
                    )
                }
            }
        }

        myPin?.let { pin ->
            rankingUsers.find { it.isMe }?.let { item ->
                MyRankPinnedRow(
                    item = item,
                    filter = state.selectedFilter,
                    modifier = Modifier.align(if (pin == MyRankPin.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun RankingFilterRow(
    selectedFilter: RankingDrinkFilter,
    onFilterChange: (RankingDrinkFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .height(52.dp)
            .background(RankingBgLight)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankingDrinkFilter.entries.forEach { filter ->
            val selected = selectedFilter == filter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onFilterChange(filter) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    filter.rankingLabel(),
                    color = RankingTextMain,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RankingMessage(message: String) {
    Text(
        text = message,
        color = RankingError,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun TopRankSection(
    users: List<RankingUserStat>,
    filter: RankingDrinkFilter,
    onAddFriend: (RankingUserStat) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        repeat(3) { index ->
            val item = users.getOrNull(index)
            TopRankRow(
                rank = index + 1,
                title = item?.let { if (it.isMe) "${it.userName} (나)" else it.userName }.orEmpty(),
                count = item?.countFor(filter) ?: 0,
                initial = item?.userName?.firstOrNull()?.toString() ?: "?",
                colors = topRankColors(index + 1),
                friendLabel = item?.friendActionLabel(),
                imageUrl = item?.imageUrl,
                onAddFriend = item?.takeIf { it.canSendFriendRequest() }?.let {
                    { onAddFriend(it) }
                }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TopRankRow(
    rank: Int,
    title: String,
    count: Int,
    initial: String,
    colors: Pair<Color, Color>,
    friendLabel: String?,
    imageUrl: String? = null,
    onAddFriend: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(colors.first)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rank.toString(),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Spacer(Modifier.width(8.dp))
        RankingSquareInitial(
            initial = initial,
            background = colors.second,
            textColor = Color.White,
            imageUrl = imageUrl
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title.ifBlank { "기록 없음" },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("${count}잔", color = Color.White, fontSize = 13.sp)
        }
        if (friendLabel != null) {
            val actionModifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(0.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .padding(horizontal = 12.dp, vertical = 7.dp)
            Text(
                friendLabel,
                color = if (onAddFriend != null) Color.White else Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = if (onAddFriend != null) {
                    actionModifier.clickable { onAddFriend() }
                } else {
                    actionModifier
                }
            )
        }
    }
}

@Composable
private fun RankingUserListRow(
    item: RankingUserStat,
    filter: RankingDrinkFilter,
    friendLabelOverride: String? = null,
    onAddFriend: (() -> Unit)?
) {
    val friendLabel = friendLabelOverride ?: item.friendActionLabel()
    RankingListRow(
        rankText = item.rank.toString(),
        title = if (item.isMe) "${item.userName} (나)" else item.userName,
        count = item.countFor(filter),
        initial = item.userName.firstOrNull()?.toString() ?: "?",
        isMe = item.isMe,
        friendLabel = friendLabel,
        imageUrl = item.imageUrl,
        onAddFriend = if (friendLabelOverride != null || item.canSendFriendRequest()) onAddFriend else null
    )
}

@Composable
private fun RankingListRow(
    rankText: String,
    title: String,
    count: Int,
    initial: String,
    isMe: Boolean,
    friendLabel: String?,
    imageUrl: String? = null,
    onAddFriend: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp)
            .background(if (isMe) RankingMint.copy(alpha = 0.16f) else RankingBgLight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rankText,
            color = RankingTextMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box {
            RankingSquareInitial(
                initial = initial,
                background = RankingMint,
                textColor = Color.White,
                imageUrl = imageUrl
            )
            if (isMe) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title.ifBlank { "사용자" },
                color = RankingTextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("${count}잔", color = RankingTextSub, fontSize = 13.sp)
        }

        if (friendLabel != null) {
            val actionModifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(0.dp))
                .background(RankingBgLight)
                .padding(horizontal = 12.dp, vertical = 7.dp)
            Text(
                friendLabel,
                color = if (onAddFriend != null) RankingMint else RankingTextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = if (onAddFriend != null) {
                    actionModifier.clickable { onAddFriend() }
                } else {
                    actionModifier
                }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun MyRankPinnedRow(
    item: RankingUserStat,
    filter: RankingDrinkFilter,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(RankingMint)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#${item.rank}",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Spacer(Modifier.width(8.dp))
        RankingSquareInitial(
            initial = item.userName.firstOrNull()?.toString() ?: "?",
            background = RankingMintDark,
            textColor = Color.White,
            imageUrl = item.imageUrl
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${item.userName} (나)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("${item.countFor(filter)}잔", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RankingSquareInitial(
    initial: String,
    background: Color,
    textColor: Color,
    imageUrl: String? = null
) {
    if (!imageUrl.isNullOrEmpty()) {
        coil.compose.AsyncImage(
            model = imageUrl,
            contentDescription = "프로필",
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(background),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initial,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StoreSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            textStyle = TextStyle(
                color = RankingTextMain,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .background(RankingFieldBg)
                .padding(horizontal = 16.dp, vertical = 17.dp),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("가게 이름을 검색하세요", color = RankingTextDim, fontSize = 14.sp)
                    }
                    innerTextField()
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(RankingMint)
                .clickable { onSearch() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Search, contentDescription = "검색", tint = Color.White)
        }
    }
}

@Composable
private fun StoreSearchResultRow(
    store: RankingStoreUi,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    store.name,
                    color = RankingTextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    store.address,
                    color = RankingTextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SelectedStoreCard(
    store: RankingStoreUi,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = RankingBgLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                store.name,
                color = Color(0xFF1A1A1A),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                store.address,
                color = RankingTextSub,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun StoreEmptyState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(RankingBgLight),
            contentAlignment = Alignment.Center
        ) {
            Text("🔍", fontSize = 36.sp)
        }
        Text(
            text,
            color = RankingTextDim,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun StoreBottomSheetContent(store: RankingStoreUi) {
    val menuItems by produceState<List<MenuItem>?>(initialValue = null, store.id) {
        FirebaseFirestore.getInstance().collection("stores").document(store.id)
            .collection("menuItems")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { result ->
                value = result.documents.map { doc ->
                    MenuItem(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        price = (doc.getLong("price") ?: 0).toInt(),
                        category = doc.getString("category") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        isSoldOut = doc.getBoolean("isSoldOut") ?: false
                    )
                }.sortedBy { it.displayOrder }
            }
            .addOnFailureListener {
                value = emptyList()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .size(width = 40.dp, height = 4.dp)
                .background(Color(0xFFDDDDDD))
                .align(Alignment.CenterHorizontally)
        )
        Column(Modifier.padding(16.dp)) {
            Text(
                store.name,
                color = Color(0xFF1A1A1A),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                store.category,
                color = Color(0xFF888888),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            StoreInfoCard(label = "주소", value = store.address)
            StoreInfoCard(label = "전화번호", value = store.phone)
            Text(
                "메뉴판",
                color = Color(0xFF1A1A1A),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            
            if (menuItems == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .align(Alignment.CenterHorizontally),
                    color = RankingMint,
                    strokeWidth = 2.dp
                )
            } else if (menuItems!!.isEmpty()) {
                Text(
                    "등록된 메뉴가 없습니다",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                menuItems!!.forEach { menu ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            menu.name,
                            color = Color(0xFF1A1A1A),
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(menu.price)}원",
                            color = RankingTextMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreInfoCard(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = RankingBgLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = RankingMint, fontSize = 12.sp)
            Text(
                value,
                color = Color(0xFF1A1A1A),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun FriendRequestRow(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .background(RankingBgLight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankingSquareInitial(
            initial = request.requesterName.firstOrNull()?.toString() ?: "?",
            background = RankingMint,
            textColor = Color.White,
            imageUrl = request.imageUrl
        )
        Text(
            request.requesterName,
            color = RankingTextMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Button(
            onClick = onAccept,
            modifier = Modifier.height(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RankingMint, contentColor = Color.White),
            shape = RoundedCornerShape(0.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text("수락", fontSize = 13.sp)
        }
        Text(
            "거절",
            color = RankingTextDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .height(36.dp)
                .clickable { onReject() }
                .padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun SentFriendRequestRow(
    request: SentFriendRequest,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .background(RankingBgLight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankingSquareInitial(
            initial = request.receiverName.firstOrNull()?.toString() ?: "?",
            background = RankingMint,
            textColor = Color.White,
            imageUrl = request.imageUrl
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                request.receiverName,
                color = RankingTextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("요청 중", color = RankingTextDim, fontSize = 12.sp)
        }
        Text(
            "요청 취소",
            color = RankingError,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .height(36.dp)
                .clickable { onCancel() }
                .padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun FriendHeader(
    count: Int,
    onAddFriend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "내 친구 (${count}명)",
            color = RankingTextMain,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onAddFriend,
            modifier = Modifier.height(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RankingMint, contentColor = Color.White),
            shape = RoundedCornerShape(0.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text("+ 친구 추가", fontSize = 13.sp)
        }
    }
}

@Composable
private fun RankingEmptyBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(RankingBgLight)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(color = Color.Transparent)
        Text(text, color = RankingTextSub, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

private fun myRankPinFor(
    listState: LazyListState,
    myRank: RankingUserStat?,
    hasMessage: Boolean
): MyRankPin? {
    val rank = myRank?.rank ?: return null
    if (rank <= 3) return null

    val restStartIndex = if (hasMessage) 3 else 2
    val myItemIndex = restStartIndex + (rank - 4)
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (visibleItems.any { it.index == myItemIndex }) return null

    val firstVisible = visibleItems.firstOrNull()?.index ?: return MyRankPin.BOTTOM
    return if (myItemIndex < firstVisible) MyRankPin.TOP else MyRankPin.BOTTOM
}

private fun myRankPinForFriend(
    listState: LazyListState,
    myRank: RankingUserStat?,
    headerItemCount: Int,
    rankingUsers: List<RankingUserStat>
): MyRankPin? {
    val rank = myRank?.rank ?: return null
    if (rank <= 3) return null
    
    val myIndexInList = rankingUsers.indexOf(myRank)
    if (myIndexInList < 3) return null
    
    val myItemIndex = headerItemCount + (myIndexInList - 3)
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (visibleItems.any { it.index == myItemIndex }) return null

    val firstVisible = visibleItems.firstOrNull()?.index ?: return MyRankPin.BOTTOM
    return if (myItemIndex < firstVisible) MyRankPin.TOP else MyRankPin.BOTTOM
}

private fun displayUsers(
    users: List<RankingUserStat>,
    filter: RankingDrinkFilter,
    samples: List<RankingUserStat> = emptyList()
): List<RankingUserStat> {
    val rankedUsers = users.rankBy(filter)
    if (rankedUsers.size >= RankingSampleMinCount) return rankedUsers

    val existingIds = rankedUsers.map { it.userId }.toSet()
    val hasRealMe = rankedUsers.any { it.isMe }
    val additions = samples
        .filterNot { it.userId in existingIds || (hasRealMe && it.isMe) }
        .take(RankingSampleMinCount - rankedUsers.size)

    return (users + additions).rankBy(filter)
}

private fun List<RankingStoreUi>.withSampleStores(): List<RankingStoreUi> {
    if (size >= RankingSampleMinCount) return this
    val existingIds = map { it.id }.toSet()
    val existingRankingIds = map { it.rankingStoreId }.toSet()
    val additions = sampleStores()
        .filterNot { it.id in existingIds || it.rankingStoreId in existingRankingIds }
        .take(RankingSampleMinCount - size)
    return this + additions
}

private fun RankingUserStat.friendActionLabel(): String? {
    if (isMe) return null
    return when (friendshipStatus) {
        RankingFriendshipStatus.CAN_REQUEST -> "+ 친구"
        RankingFriendshipStatus.REQUESTED -> "요청 중"
        RankingFriendshipStatus.INCOMING -> "받은 요청"
        RankingFriendshipStatus.FRIEND -> "친구"
    }
}

private fun RankingUserStat.canSendFriendRequest(): Boolean =
    !isMe && friendshipStatus == RankingFriendshipStatus.CAN_REQUEST

private fun RankingUserStat.isSampleRankingUser(): Boolean =
    userId.startsWith("demo-") ||
        userId.startsWith("friend-demo-") ||
        userId.startsWith("store-demo-")

private fun sampleTimeRanking(
    filter: RankingDrinkFilter,
    period: RankingPeriod
): List<RankingUserStat> {
    val periodKey = period.name.lowercase()
    val boost = when (period) {
        RankingPeriod.DAILY -> 0
        RankingPeriod.WEEKLY -> 42
        RankingPeriod.MONTHLY -> 126
    }
    return listOf(
        sampleRankingUser("demo-$periodKey-01", "소주마스터", 198 + boost, 114 + boost / 2),
        sampleRankingUser("demo-$periodKey-02", "맥주요정", 74 + boost / 2, 201 + boost),
        sampleRankingUser("demo-$periodKey-03", "잔잔러", 132 + boost, 88 + boost / 2),
        sampleRankingUser("demo-$periodKey-me", "나", 81 + boost / 2, 63 + boost / 3, isMe = true),
        sampleRankingUser("demo-$periodKey-04", "포차러버", 60 + boost / 2, 52 + boost / 3),
        sampleRankingUser("demo-$periodKey-05", "안주킬러", 44 + boost / 3, 39 + boost / 2),
        sampleRankingUser("demo-$periodKey-06", "건배장인", 151 + boost / 2, 92 + boost / 3),
        sampleRankingUser("demo-$periodKey-07", "잔요정", 53 + boost / 3, 147 + boost / 2),
        sampleRankingUser("demo-$periodKey-08", "회식대장", 121 + boost / 2, 116 + boost / 2),
        sampleRankingUser("demo-$periodKey-09", "치맥러", 37 + boost / 3, 133 + boost / 2),
        sampleRankingUser("demo-$periodKey-10", "민트잔", 96 + boost / 2, 58 + boost / 3),
        sampleRankingUser("demo-$periodKey-11", "테이블킹", 89 + boost / 3, 75 + boost / 2),
        sampleRankingUser("demo-$periodKey-12", "막잔친구", 68 + boost / 3, 82 + boost / 3),
        sampleRankingUser("demo-$periodKey-13", "야식러", 41 + boost / 3, 104 + boost / 2),
        sampleRankingUser("demo-$periodKey-14", "잔잔크루", 77 + boost / 2, 66 + boost / 3)
    ).rankBy(filter)
}

private fun sampleFriendRanking(filter: RankingDrinkFilter): List<RankingUserStat> =
    listOf(
        sampleFriendUser("friend-demo-01", "츄츄이", 126, 72),
        sampleFriendUser("friend-demo-02", "하이볼러", 42, 139),
        sampleFriendUser("friend-demo-03", "민트잔", 92, 31),
        sampleMeRank(),
        sampleFriendUser("friend-demo-04", "건배친구", 86, 54),
        sampleFriendUser("friend-demo-05", "잔잔메이트", 67, 92),
        sampleFriendUser("friend-demo-06", "안주친구", 58, 47),
        sampleFriendUser("friend-demo-07", "포차동료", 101, 38),
        sampleFriendUser("friend-demo-08", "맥주러버", 34, 113),
        sampleFriendUser("friend-demo-09", "소맥장인", 77, 84),
        sampleFriendUser("friend-demo-10", "마지막잔", 49, 61),
        sampleFriendUser("friend-demo-11", "회식친구", 71, 73),
        sampleFriendUser("friend-demo-12", "분위기메이커", 56, 68),
        sampleFriendUser("friend-demo-13", "잔친구", 63, 42),
        sampleFriendUser("friend-demo-14", "테이블친구", 38, 57)
    ).rankBy(filter)

private fun sampleStoreRanking(
    filter: RankingDrinkFilter,
    storeId: String?
): List<RankingUserStat> {
    val key = storeId?.takeIf { it.isNotBlank() } ?: "selected"
    return listOf(
        sampleRankingUser("store-demo-$key-01", "가게단골", 156, 91),
        sampleRankingUser("store-demo-$key-02", "소주단골", 174, 52),
        sampleRankingUser("store-demo-$key-03", "맥주단골", 64, 168),
        sampleRankingUser("store-demo-$key-04", "잔잔손님", 115, 87),
        sampleRankingUser("store-demo-$key-05", "포차손님", 93, 73),
        sampleRankingUser("store-demo-$key-06", "치맥손님", 48, 121),
        sampleRankingUser("store-demo-$key-07", "막잔손님", 82, 66),
        sampleRankingUser("store-demo-$key-08", "테이블손님", 76, 79),
        sampleRankingUser("store-demo-$key-09", "안주손님", 58, 54),
        sampleRankingUser("store-demo-$key-10", "회식손님", 102, 49),
        sampleRankingUser("store-demo-$key-11", "건배손님", 67, 93),
        sampleRankingUser("store-demo-$key-12", "술자리친구", 54, 82),
        sampleRankingUser("store-demo-$key-13", "잔요정손님", 44, 69),
        sampleRankingUser("store-demo-$key-14", "분위기손님", 39, 64),
        sampleRankingUser("store-demo-$key-15", "단골크루", 34, 58)
    ).rankBy(filter)
}

private fun sampleMeRank(): RankingUserStat =
    RankingUserStat(
        userId = "friend-demo-me",
        userName = "소주마스터",
        sojuCount = 73,
        beerCount = 55,
        rank = 4,
        isMe = true,
        friendshipStatus = RankingFriendshipStatus.FRIEND
    )

private fun sampleRankingUser(
    userId: String,
    userName: String,
    sojuCount: Int,
    beerCount: Int,
    isMe: Boolean = false
): RankingUserStat =
    RankingUserStat(
        userId = userId,
        userName = userName,
        sojuCount = sojuCount,
        beerCount = beerCount,
        isMe = isMe,
        friendshipStatus = if (isMe) {
            RankingFriendshipStatus.FRIEND
        } else {
            RankingFriendshipStatus.REQUESTED
        }
    )

private fun sampleFriendUser(
    userId: String,
    userName: String,
    sojuCount: Int,
    beerCount: Int
): RankingUserStat =
    RankingUserStat(
        userId = userId,
        userName = userName,
        sojuCount = sojuCount,
        beerCount = beerCount,
        friendshipStatus = RankingFriendshipStatus.FRIEND
    )

private fun List<RankingUserStat>.rankBy(filter: RankingDrinkFilter): List<RankingUserStat> =
    sortedWith(compareByDescending<RankingUserStat> { it.countFor(filter) }.thenBy { it.userName })
        .mapIndexed { index, item -> item.copy(rank = index + 1) }

private fun sampleStores(): List<RankingStoreUi> =
    listOf(
        RankingStoreUi(
            id = "store-hongdae",
            name = "홍대포차",
            address = "서울 마포구 홍익로 12",
            category = "음식점 > 술집 > 포장마차",
            phone = "02-1234-5678"
        ),
        RankingStoreUi(
            id = "store-test",
            name = "더치페이 테스트포차",
            address = "경기 성남시 수정구 테스트로 1",
            category = "음식점 > 술집",
            phone = "정보 없음"
        ),
        RankingStoreUi(
            id = "store-janjan",
            name = "잔잔포차",
            address = "서울 강남구 테헤란로 27",
            category = "음식점 > 요리주점",
            phone = "02-555-0012"
        ),
        RankingStoreUi(
            id = "store-gangnam",
            name = "강남잔집",
            address = "서울 강남구 강남대로 94",
            category = "음식점 > 술집",
            phone = "02-444-0912"
        ),
        RankingStoreUi(
            id = "store-sinchon",
            name = "신촌술방",
            address = "서울 서대문구 연세로 18",
            category = "음식점 > 요리주점",
            phone = "02-332-7001"
        ),
        RankingStoreUi(
            id = "store-konkuk",
            name = "건대맥주창고",
            address = "서울 광진구 아차산로 32",
            category = "음식점 > 맥주",
            phone = "02-498-1200"
        ),
        RankingStoreUi(
            id = "store-jamsil",
            name = "잠실소주집",
            address = "서울 송파구 올림픽로 240",
            category = "음식점 > 술집",
            phone = "02-420-1313"
        ),
        RankingStoreUi(
            id = "store-bundang",
            name = "분당포차거리",
            address = "경기 성남시 분당구 황새울로 210",
            category = "음식점 > 포장마차",
            phone = "031-701-2525"
        ),
        RankingStoreUi(
            id = "store-suwon",
            name = "수원잔잔",
            address = "경기 수원시 팔달구 향교로 9",
            category = "음식점 > 술집",
            phone = "031-252-8008"
        ),
        RankingStoreUi(
            id = "store-incheon",
            name = "인천밤잔",
            address = "인천 남동구 예술로 198",
            category = "음식점 > 요리주점",
            phone = "032-421-9090"
        ),
        RankingStoreUi(
            id = "store-gachon",
            name = "가천대포차",
            address = "경기 성남시 수정구 성남대로 1342",
            category = "음식점 > 술집",
            phone = "031-750-1234"
        ),
        RankingStoreUi(
            id = "store-yatap",
            name = "야탑잔집",
            address = "경기 성남시 분당구 야탑로 81",
            category = "음식점 > 맥주",
            phone = "031-707-3030"
        ),
        RankingStoreUi(
            id = "store-seohyeon",
            name = "서현소맥",
            address = "경기 성남시 분당구 서현로 192",
            category = "음식점 > 술집",
            phone = "031-781-4141"
        ),
        RankingStoreUi(
            id = "store-itaewon",
            name = "이태원밤잔",
            address = "서울 용산구 이태원로 177",
            category = "음식점 > 펍",
            phone = "02-797-2200"
        ),
        RankingStoreUi(
            id = "store-euljiro",
            name = "을지로잔",
            address = "서울 중구 을지로 157",
            category = "음식점 > 노포",
            phone = "02-2267-3300"
        )
    )

private fun sampleFriendRequests(): List<FriendRequestUi> =
    listOf(
        FriendRequestUi("request-01", "츄츄이"),
        FriendRequestUi("request-02", "포차친구")
    )

private fun RankingDrinkFilter.rankingLabel(): String =
    when (this) {
        RankingDrinkFilter.TOTAL -> "전체"
        RankingDrinkFilter.SOJU -> "🍶 소주"
        RankingDrinkFilter.BEER -> "🍺 맥주"
    }

private fun topRankColors(rank: Int): Pair<Color, Color> =
    when (rank) {
        1 -> RankGold to RankGoldDark
        2 -> RankSilver to RankSilverDark
        else -> RankBronze to RankBronzeDark
    }
