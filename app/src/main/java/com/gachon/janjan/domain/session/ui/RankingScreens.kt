package com.gachon.janjan.domain.session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gachon.janjan.domain.session.model.RankingDrinkFilter
import com.gachon.janjan.domain.session.model.RankingPeriod
import com.gachon.janjan.domain.session.model.RankingUiState
import com.gachon.janjan.domain.session.model.RankingUserStat
import com.gachon.janjan.domain.session.viewmodel.RankingViewModel

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
                    placeholder = { Text("상대방 아이디를 입력하세요") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
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
                onAddFriend = { showAddFriendDialog = true }
            )

            RankingMainTab.STORE -> StoreRankingTab(
                state = state,
                storeQuery = storeQuery,
                selectedStore = selectedStore,
                onQueryChange = {
                    storeQuery = it
                    selectedStore = null
                },
                onStoreSelected = { selectedStore = it },
                onSelectedStoreClick = { sheetStore = it },
                onFilterChange = rankingViewModel::selectFilter,
                onAddFriend = { showAddFriendDialog = true }
            )

            RankingMainTab.FRIEND -> FriendRankingTab(
                state = state,
                onFilterChange = rankingViewModel::selectFilter,
                onAddFriend = { showAddFriendDialog = true }
            )
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
    onAddFriend: () -> Unit
) {
    val listState = rememberLazyListState()
    val users = displayUsers(state.users, state.selectedFilter)
    val myRank = state.myRank ?: users.firstOrNull { it.isMe }
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
                    filter = state.selectedFilter
                )
            }

            if (users.isEmpty() && !state.isLoading) {
                item { RankingEmptyBlock("아직 기록된 잔 수가 없습니다.") }
            } else {
                items(users.drop(3), key = { it.userId }) { item ->
                    RankingUserListRow(
                        item = item,
                        filter = state.selectedFilter,
                        onAddFriend = onAddFriend
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
    storeQuery: String,
    selectedStore: RankingStoreUi?,
    onQueryChange: (String) -> Unit,
    onStoreSelected: (RankingStoreUi) -> Unit,
    onSelectedStoreClick: (RankingStoreUi) -> Unit,
    onFilterChange: (RankingDrinkFilter) -> Unit,
    onAddFriend: () -> Unit
) {
    val searchResults = remember(storeQuery) {
        sampleStores().filter {
            val query = storeQuery.trim()
            query.length >= 2 && (it.name.contains(query, ignoreCase = true) ||
                    it.address.contains(query, ignoreCase = true))
        }
    }
    val rankingUsers = displayUsers(state.users, state.selectedFilter)

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
        item {
            StoreSearchRow(
                query = storeQuery,
                onQueryChange = onQueryChange
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
                    filter = state.selectedFilter
                )
            }
            items(rankingUsers.drop(3), key = { "store-${it.userId}" }) { item ->
                RankingUserListRow(
                    item = item,
                    filter = state.selectedFilter,
                    onAddFriend = onAddFriend
                )
            }
        }
    }
}

@Composable
private fun FriendRankingTab(
    state: RankingUiState,
    onFilterChange: (RankingDrinkFilter) -> Unit,
    onAddFriend: () -> Unit
) {
    var friendRequests by remember { mutableStateOf(sampleFriendRequests()) }
    val friends = sampleFriendRanking(state.selectedFilter)
    val myRank = friends.firstOrNull { it.isMe } ?: sampleMeRank()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                RankingFilterRow(
                    selectedFilter = state.selectedFilter,
                    onFilterChange = onFilterChange
                )
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
                        onAccept = {
                            friendRequests = friendRequests.filterNot { it.id == request.id }
                        },
                        onReject = {
                            friendRequests = friendRequests.filterNot { it.id == request.id }
                        }
                    )
                }
            }

            item {
                FriendHeader(
                    count = friends.count { !it.isMe },
                    onAddFriend = onAddFriend
                )
            }

            items(friends.filterNot { it.isMe }, key = { "friend-${it.userId}" }) { item ->
                RankingUserListRow(
                    item = item,
                    filter = state.selectedFilter,
                    onAddFriend = null
                )
            }
        }

        MyRankPinnedRow(
            item = myRank,
            filter = state.selectedFilter,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
    filter: RankingDrinkFilter
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
                colors = topRankColors(index + 1)
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
    colors: Pair<Color, Color>
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
            textColor = Color.White
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
    }
}

@Composable
private fun RankingUserListRow(
    item: RankingUserStat,
    filter: RankingDrinkFilter,
    onAddFriend: (() -> Unit)?
) {
    RankingListRow(
        rankText = item.rank.toString(),
        title = if (item.isMe) "${item.userName} (나)" else item.userName,
        count = item.countFor(filter),
        initial = item.userName.firstOrNull()?.toString() ?: "?",
        isMe = item.isMe,
        showAddFriend = !item.isMe && onAddFriend != null,
        onAddFriend = onAddFriend
    )
}

@Composable
private fun RankingListRow(
    rankText: String,
    title: String,
    count: Int,
    initial: String,
    isMe: Boolean,
    showAddFriend: Boolean,
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
                textColor = Color.White
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

        if (showAddFriend && onAddFriend != null) {
            Text(
                "+ 친구",
                color = RankingMint,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(0.dp))
                    .background(RankingBgLight)
                    .clickable { onAddFriend() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
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
            textColor = Color.White
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
    textColor: Color
) {
    Box(
        modifier = Modifier
            .size(44.dp)
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

@Composable
private fun StoreSearchRow(
    query: String,
    onQueryChange: (String) -> Unit
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
                .clickable { },
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
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(RankingBgLight)
            )
            Spacer(Modifier.width(12.dp))
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(top = 12.dp)
                .background(RankingBgLight),
            contentAlignment = Alignment.Center
        ) {
            Text("🏪", fontSize = 48.sp)
        }
        Column(Modifier.padding(16.dp)) {
            Text(
                store.name,
                color = Color(0xFF1A1A1A),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
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
            Text(
                "메뉴 정보가 없어요",
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
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
    request: FriendRequestUi,
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
            initial = request.nickname.firstOrNull()?.toString() ?: "?",
            background = RankingMint,
            textColor = Color.White
        )
        Text(
            request.nickname,
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

private fun displayUsers(
    users: List<RankingUserStat>,
    filter: RankingDrinkFilter
): List<RankingUserStat> =
    users.ifEmpty { sampleTimeRanking(filter) }

private fun sampleTimeRanking(filter: RankingDrinkFilter): List<RankingUserStat> =
    listOf(
        RankingUserStat("demo-01", "소주마스터", sojuCount = 198, beerCount = 114),
        RankingUserStat("demo-02", "맥주요정", sojuCount = 74, beerCount = 201),
        RankingUserStat("demo-03", "잔잔러", sojuCount = 132, beerCount = 88),
        RankingUserStat("demo-me", "나", sojuCount = 81, beerCount = 63, isMe = true),
        RankingUserStat("demo-04", "포차러버", sojuCount = 60, beerCount = 52),
        RankingUserStat("demo-05", "안주킬러", sojuCount = 44, beerCount = 39)
    ).rankBy(filter)

private fun sampleFriendRanking(filter: RankingDrinkFilter): List<RankingUserStat> =
    listOf(
        RankingUserStat("friend-01", "츄츄이", sojuCount = 126, beerCount = 72),
        RankingUserStat("friend-02", "하이볼러", sojuCount = 42, beerCount = 139),
        RankingUserStat("friend-03", "민트잔", sojuCount = 92, beerCount = 31),
        sampleMeRank()
    ).rankBy(filter)

private fun sampleMeRank(): RankingUserStat =
    RankingUserStat("friend-me", "소주마스터", sojuCount = 73, beerCount = 55, rank = 4, isMe = true)

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
