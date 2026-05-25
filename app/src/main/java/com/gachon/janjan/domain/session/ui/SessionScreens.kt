package com.gachon.janjan.domain.session.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gachon.janjan.data.model.Session
import com.gachon.janjan.domain.session.model.DrinkHistoryItem
import com.gachon.janjan.domain.session.model.GlassUserMapping
import com.gachon.janjan.domain.session.model.HealthSummary
import com.gachon.janjan.domain.session.model.OrderSummaryItem
import com.gachon.janjan.domain.session.model.SessionParticipant
import com.gachon.janjan.domain.session.viewmodel.HistoryHealthViewModel
import com.gachon.janjan.domain.session.viewmodel.SessionViewModel
import kotlinx.coroutines.delay
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.util.Date
import java.util.Locale

private val Mint = Color(0xFF5AC4AF)
private val BgLight = Color(0xFFE8F6F3)
private val BgMuted = Color(0xFFCBECE5)
private val BorderMint = Color(0xFFA3DCD1)
private val TextMain = Color(0xFF1A1A1E)
private val TextSub = Color(0xFF6B7280)
private val TextDim = Color(0xFF9CA3AF)
private val ErrorRed = Color(0xFFEF4444)
private val Green = Color(0xFF22C55E)

private val currency = DecimalFormat("#,###")

@Composable
fun SessionHomeScreen(
    sessionViewModel: SessionViewModel,
    historyHealthViewModel: HistoryHealthViewModel,
    onQrScan: () -> Unit,
    onInviteCode: () -> Unit,
    onBusinessOwner: () -> Unit,
    onOrder: () -> Unit,
    onGlassColor: (String) -> Unit
) {
    val activeSession by sessionViewModel.activeSession.collectAsStateWithLifecycle()
    val mappings by sessionViewModel.glassMappings.collectAsStateWithLifecycle()
    val participants by sessionViewModel.participants.collectAsStateWithLifecycle()
    val orders by sessionViewModel.orderItems.collectAsStateWithLifecycle()
    val isLoading by sessionViewModel.isLoading.collectAsStateWithLifecycle()
    val histories by historyHealthViewModel.histories.collectAsStateWithLifecycle()
    val healthSummary by historyHealthViewModel.healthSummary.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf("home") }
    var profileTab by remember { mutableStateOf("history") }
    var selectedHistory by remember { mutableStateOf<DrinkHistoryItem?>(null) }
    var showSettlement by remember { mutableStateOf(false) }
    var settlementSession by remember { mutableStateOf<Session?>(null) }

    LaunchedEffect(Unit) {
        historyHealthViewModel.load()
    }

    JanjanSurface {
        val currentSettlement = settlementSession
        if (showSettlement && currentSettlement != null) {
            SettlementScreen(
                session = currentSettlement,
                myUserId = sessionViewModel.currentUserId,
                participants = participants,
                mappings = mappings,
                orders = orders,
                isLoading = isLoading,
                onBack = {
                    showSettlement = false
                    settlementSession = null
                },
                onStartSettlement = { sessionId, onComplete ->
                    sessionViewModel.startSettlement(sessionId, onComplete)
                },
                onCompleteSettlement = { sessionId, onComplete ->
                    sessionViewModel.completeSettlement(sessionId, onComplete)
                },
                onDone = {
                    showSettlement = false
                    settlementSession = null
                }
            )
        } else {
            Scaffold(
                containerColor = Color.White,
                bottomBar = {
                    JanjanBottomBar(tab = tab, onTabChange = { tab = it })
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (tab) {
                        "ranking" -> RankingPlaceholder()
                        "profile" -> ProfileScreen(
                            histories = histories,
                            healthSummary = healthSummary,
                            profileTab = profileTab,
                            onProfileTabChange = { profileTab = it },
                            onHistoryClick = { selectedHistory = it },
                            onDeleteHistory = { historyHealthViewModel.removeHistoryLocally(it.sessionId) }
                        )
                        else -> HomeContent(
                            activeSession = activeSession,
                            myUserId = sessionViewModel.currentUserId,
                            mappings = mappings,
                            participants = participants,
                            orders = orders,
                            histories = histories,
                            onQrScan = onQrScan,
                            onInviteCode = onInviteCode,
                            onBusinessOwner = onBusinessOwner,
                            onOrder = onOrder,
                            onGlassColor = onGlassColor,
                            onSettlement = {
                                activeSession?.let {
                                    settlementSession = it
                                    showSettlement = true
                                }
                            },
                            onShowHistory = {
                                tab = "profile"
                                profileTab = "history"
                            }
                        )
                    }
                }
            }
        }
    }

    selectedHistory?.let { history ->
        HistoryDetailDialog(
            history = history,
            onDismiss = { selectedHistory = null }
        )
    }
}

@Composable
fun QrScanScreen(
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit,
    onInvite: () -> Unit,
    onOrderReady: (String) -> Unit
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isJoining by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("카메라를 QR코드에 맞춰주세요") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        statusText = if (granted) {
            "카메라를 QR코드에 맞춰주세요"
        } else {
            "카메라 권한을 허용해야 QR을 스캔할 수 있어요"
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(cameraGranted, isJoining) {
        if (!cameraGranted || isJoining) return@LaunchedEffect
        while (true) {
            delay(80)
            scanProgress = if (scanProgress >= 100) 0 else scanProgress + 4
        }
    }

    JanjanSurface {
        HeaderScaffold(
            title = "QR 스캔",
            onBack = onBack,
            bottom = {
                OutlinedButton(
                    onClick = onInvite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    border = BorderStroke(1.dp, BorderMint),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = BgLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = TextSub)
                    Spacer(Modifier.width(8.dp))
                    Text("QR이 안 되나요? 초대코드 입력", color = TextSub)
                }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(256.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(2.dp, Mint.copy(alpha = 0.36f), RoundedCornerShape(22.dp))
                        .background(BgLight),
                    contentAlignment = Alignment.Center
                ) {
                    if (cameraGranted) {
                        QrCameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            onQrCode = { value ->
                                if (isJoining) return@QrCameraPreview
                                isJoining = true
                                statusText = "테이블 정보를 확인하는 중..."
                                sessionViewModel.joinByQrPayload(value) { success, sessionId ->
                                    if (success && sessionId != null) {
                                        onOrderReady(sessionId)
                                    } else {
                                        isJoining = false
                                        statusText = "인식된 QR을 JANJAN 테이블로 연결할 수 없어요"
                                    }
                                }
                            }
                        )
                    } else {
                        Icon(
                            Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = Mint.copy(alpha = 0.42f),
                            modifier = Modifier.size(58.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(1.dp, Mint.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                    )
                    ScannerCorner(Alignment.TopStart, top = true, start = true)
                    ScannerCorner(Alignment.TopEnd, top = true, end = true)
                    ScannerCorner(Alignment.BottomStart, bottom = true, start = true)
                    ScannerCorner(Alignment.BottomEnd, bottom = true, end = true)
                    if (!isJoining) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = ((scanProgress / 100f) * 238).dp)
                                .padding(horizontal = 8.dp)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Mint, RoundedCornerShape(999.dp))
                        )
                    }
                    if (isJoining) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.32f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("테이블 QR코드를 스캔해주세요", color = TextMain, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(statusText, color = TextDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                if (cameraGranted && !isJoining) {
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(
                        progress = { scanProgress / 100f },
                        modifier = Modifier
                            .width(192.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = Mint,
                        trackColor = BgMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("인식 중... $scanProgress%", color = TextDim, fontSize = 13.sp)
                }
                if (!cameraGranted) {
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("카메라 권한 허용", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun InviteCodeScreen(
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit,
    onOrderReady: (String) -> Unit
) {
    val isLoading by sessionViewModel.isLoading.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    fun setCharsFrom(index: Int, input: String) {
        val cleaned = input.uppercase(Locale.US).filter { it.isLetterOrDigit() }.take(6 - index)
        if (cleaned.isEmpty()) {
            val chars = code.padEnd(6).toCharArray()
            chars[index] = ' '
            code = chars.concatToString().replace(" ", "").take(6)
            return
        }
        val chars = code.padEnd(6).toCharArray()
        cleaned.forEachIndexed { offset, char -> chars[index + offset] = char }
        code = chars.concatToString().replace(" ", "").take(6)
        val nextIndex = (index + cleaned.length).coerceAtMost(5)
        focusRequesters[nextIndex].requestFocus()
    }

    LaunchedEffect(Unit) {
        focusRequesters.first().requestFocus()
    }

    JanjanSurface {
        HeaderScaffold(
            title = "초대코드 입력",
            onBack = onBack,
            bottom = {
                Button(
                    onClick = {
                        sessionViewModel.joinByInviteCode(code) { success, sessionId ->
                            if (success && sessionId != null) onOrderReady(sessionId)
                        }
                    },
                    enabled = code.length == 6 && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mint,
                        disabledContainerColor = BgMuted,
                        contentColor = Color.White,
                        disabledContentColor = TextDim
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("테이블 연결", fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(Mint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = Mint, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("초대코드를 입력해주세요", color = TextMain, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "QR 스캔이 어려울 때 사장님에게\n초대코드를 요청하세요",
                    color = TextDim,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(30.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { index ->
                        val cellValue = code.getOrNull(index)?.toString().orEmpty()
                        OutlinedTextField(
                            value = cellValue,
                            onValueChange = { setCharsFrom(index, it) },
                            singleLine = true,
                            modifier = Modifier
                                .width(44.dp)
                                .height(56.dp)
                                .focusRequester(focusRequesters[index])
                                .onKeyEvent { event ->
                                    if (
                                        event.type == KeyEventType.KeyUp &&
                                        event.key == Key.Backspace &&
                                        code.getOrNull(index) == null &&
                                        index > 0
                                    ) {
                                        focusRequesters[index - 1].requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            ),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BgLight,
                                unfocusedContainerColor = if (cellValue.isNotBlank()) Mint.copy(alpha = 0.10f) else BgLight,
                                focusedBorderColor = Mint,
                                unfocusedBorderColor = if (cellValue.isNotBlank()) Mint else BorderMint,
                                cursorColor = Mint
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassColorScreen(
    sessionViewModel: SessionViewModel,
    sessionId: String,
    onBack: () -> Unit,
    onDone: (String) -> Unit
) {
    val isLoading by sessionViewModel.isLoading.collectAsStateWithLifecycle()
    val participants by sessionViewModel.participants.collectAsStateWithLifecycle()
    val usedColors = participants
        .filter { it.userId != sessionViewModel.currentUserId }
        .mapNotNull { it.glassColor?.uppercase(Locale.US) }
        .toSet()
    var assignedColor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(usedColors) {
        if (assignedColor == null || assignedColor?.uppercase(Locale.US) in usedColors) {
            assignedColor = pickAvailableGlassColor(usedColors)
        }
    }

    val resolvedColor = assignedColor ?: SessionViewModel.GLASS_COLORS.first()
    val backgroundColor = resolvedColor.toComposeColor()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor,
        contentColor = contentColorFor(backgroundColor)
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Color.White)
                    }
                    Text(
                        "내 술잔 색",
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(48.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .border(3.dp, Color.White.copy(alpha = 0.42f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalBar, contentDescription = null, tint = Color.White, modifier = Modifier.size(82.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("자동 배정되었습니다", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "앱이 자동으로 고유한 색깔을 배정했어요\n스마트폰 화면으로 술잔을 인식할게요!",
                    color = Color.White.copy(alpha = 0.84f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(18.dp))
                TextButton(
                    onClick = {
                        assignedColor = pickAvailableGlassColor(
                            usedColors = usedColors,
                            excluding = setOf(resolvedColor.uppercase(Locale.US))
                        )
                    }
                ) {
                    Text("다시 배정받기", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        sessionViewModel.assignGlassColor(sessionId, resolvedColor, "soju") {
                            onDone(sessionId)
                        }
                    },
                    enabled = sessionId.isNotBlank() && assignedColor != null && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TextMain),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextMain, strokeWidth = 2.dp)
                    } else {
                        Text("이 색으로 사용하기", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DoneScreen(
    sessionViewModel: SessionViewModel,
    sessionId: String,
    onHome: () -> Unit
) {
    val session by sessionViewModel.activeSession.collectAsStateWithLifecycle()
    val orders by sessionViewModel.orderItems.collectAsStateWithLifecycle()
    val participants by sessionViewModel.participants.collectAsStateWithLifecycle()
    val myColor = participants.firstOrNull { it.userId == sessionViewModel.currentUserId }?.glassColor
    val tableLabel = session?.tableNumber?.takeIf { it > 0 }?.toString()
        ?: session?.tableId?.ifBlank { null }
        ?: "-"

    LaunchedEffect(sessionId) {
        if (sessionId.isNotBlank()) {
            sessionViewModel.loadOrderSummaries(sessionId)
        }
    }

    JanjanSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .background(Mint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Mint, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("준비 완료!", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "${session?.storeName ?: "연결된 가게"} · 테이블 $tableLabel",
                color = TextSub,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(22.dp))
            OrderSummaryCard(orders)
            if (myColor != null) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("내 술잔 색:", color = TextSub, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(myColor.toComposeColor())
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mint),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("홈으로 돌아가기", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeContent(
    activeSession: Session?,
    myUserId: String,
    mappings: List<com.gachon.janjan.domain.session.model.GlassUserMapping>,
    participants: List<com.gachon.janjan.domain.session.model.SessionParticipant>,
    orders: List<OrderSummaryItem>,
    histories: List<DrinkHistoryItem>,
    onQrScan: () -> Unit,
    onInviteCode: () -> Unit,
    onBusinessOwner: () -> Unit,
    onOrder: () -> Unit,
    onGlassColor: (String) -> Unit,
    onSettlement: () -> Unit,
    onShowHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("안녕하세요", color = TextSub, fontSize = 14.sp)
            Text("사용자 님", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        item {
            HomeStartButton(
                title = "사업자 테이블 관리",
                subtitle = "테이블별 카메라 연결",
                primary = false,
                icon = { Icon(Icons.Default.Home, contentDescription = null, tint = Mint) },
                onClick = onBusinessOwner
            )
        }

        if (activeSession == null) {
            item {
                HomeStartButton(
                    title = "QR 스캔",
                    subtitle = "테이블 QR을 찍어 술자리 시작",
                    primary = true,
                    icon = { Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color.White) },
                    onClick = onQrScan
                )
            }
            item {
                HomeStartButton(
                    title = "초대코드 입력",
                    subtitle = "QR이 안 될 때 코드로 참여",
                    primary = false,
                    icon = { Icon(Icons.Default.Tag, contentDescription = null, tint = Mint) },
                    onClick = onInviteCode
                )
            }
        } else {
            item {
                ActiveSessionCard(
                    session = activeSession,
                    myUserId = myUserId,
                    mappings = mappings,
                    participants = participants,
                    orders = orders,
                    onSettlement = onSettlement
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(
                        title = "추가 주문하기",
                        subtitle = "메뉴 더 담기",
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Mint) },
                        onClick = onOrder
                    )
                    ActionTile(
                        title = "술잔 색상 매핑",
                        subtitle = "화면 색으로 인식",
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Default.LocalBar, contentDescription = null, tint = Mint) },
                        onClick = { onGlassColor(activeSession.sessionId) }
                    )
                }
            }
        }

        item {
            val activeFriends = participants
                .filter { it.userId != myUserId }
                .take(3)
            SectionCard {
                Text("술 먹고 있는 친구", color = TextSub, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                if (activeFriends.isEmpty()) {
                    Text("현재 술자리 중인 친구가 없어요", color = TextDim, fontSize = 14.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeFriends.forEach { friend ->
                            val drinkCount = mappings
                                .filter { it.userId == friend.userId }
                                .sumOf { it.drinkCount }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(BgMuted),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(friend.userName.take(1).ifBlank { "?" }, color = TextMain, fontSize = 14.sp)
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(Green)
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(friend.userName.ifBlank { "참여자" }, color = TextMain, fontSize = 14.sp)
                                    Text(
                                        "${activeSession?.storeName?.ifBlank { "술자리" } ?: "술자리"} · ${drinkCount}잔",
                                        color = TextDim,
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(Icons.Default.LocalBar, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                Text("최근 술자리", color = TextSub, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                val latest = histories.firstOrNull()
                if (latest == null) {
                    Text("최근 술자리 내역이 없습니다.", color = TextDim, fontSize = 14.sp)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onShowHistory),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocalBar, contentDescription = null, tint = Mint)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(latest.storeName, color = TextMain, fontWeight = FontWeight.Bold)
                            Text(
                                "${latest.startedAt.formatDate()} · ${latest.participantCount}명 · ${currency.format(latest.myAmount)}원",
                                color = TextDim,
                                fontSize = 13.sp
                            )
                        }
                        Text("전체보기", color = Mint, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(
    session: Session,
    myUserId: String,
    mappings: List<com.gachon.janjan.domain.session.model.GlassUserMapping>,
    participants: List<com.gachon.janjan.domain.session.model.SessionParticipant>,
    orders: List<OrderSummaryItem>,
    onSettlement: () -> Unit
) {
    val participant = participants.firstOrNull { it.userId == myUserId }
    val cardColor = participant?.glassColor?.toComposeColor() ?: Mint
    val mySoju = mappings.filter { it.userId == myUserId && it.drinkType == "soju" }.sumOf { it.drinkCount }
    val myBeer = mappings.filter { it.userId == myUserId && it.drinkType == "beer" }.sumOf { it.drinkCount }
    val headCount = participants.size.coerceAtLeast(1)
    val expectedAmount = orders.sumOf { it.amount } / headCount
    var showColorFullscreen by remember { mutableStateOf(false) }

    Column {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(cardColor, cardColor.copy(alpha = 0.78f))
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                        .clickable { showColorFullscreen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalBar, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(12.dp))
                val tableLabel = session.tableNumber.takeIf { it > 0 }?.toString()
                    ?: session.tableId.ifBlank { "-" }
                Text(
                    "${session.storeName.ifBlank { "가게" }} · 테이블 $tableLabel",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DrinkCount("소주잔", mySoju)
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                    DrinkCount("맥주잔", myBeer)
                }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.height(16.dp))
                Text("내 정산 예정 금액", color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
                Text("${currency.format(expectedAmount)}원", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSettlement,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mint),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("정산하기", fontWeight = FontWeight.Bold)
        }
    }

    if (showColorFullscreen) {
        Dialog(
            onDismissRequest = { showColorFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cardColor)
                    .clickable { showColorFullscreen = false },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocalBar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(132.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("내 술잔", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "화면을 가득 채운 색으로\n내 잔을 쉽게 찾으세요",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(48.dp))
                    Text("탭하여 닫기", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
                }
            }
        }
    }
}

private data class SettlementParticipantUi(
    val userId: String,
    val name: String,
    val drinkCount: Int,
    val amount: Int,
    val isMe: Boolean
)

@Composable
private fun SettlementScreen(
    session: Session,
    myUserId: String,
    participants: List<SessionParticipant>,
    mappings: List<GlassUserMapping>,
    orders: List<OrderSummaryItem>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onStartSettlement: (String, (Boolean) -> Unit) -> Unit,
    onCompleteSettlement: (String, (Boolean) -> Unit) -> Unit,
    onDone: () -> Unit
) {
    var settlementStarted by remember(session.sessionId) { mutableStateOf(session.status == "settling") }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentMethod by remember { mutableStateOf<String?>(null) }
    var paymentCompleted by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val totalAmount = orders.sumOf { it.amount }
    val rows = remember(participants, mappings, orders, myUserId) {
        buildSettlementRows(
            participants = participants,
            mappings = mappings,
            totalAmount = totalAmount,
            myUserId = myUserId
        )
    }
    val myRow = rows.firstOrNull { it.isMe } ?: rows.firstOrNull()
    val tableLabel = session.tableNumber.takeIf { it > 0 }?.toString()
        ?: session.tableId.ifBlank { "-" }

    fun openPaymentDialog() {
        if (session.sessionId.isBlank()) {
            errorText = "세션 정보를 찾을 수 없어요."
            return
        }
        errorText = null
        if (settlementStarted) {
            showPaymentDialog = true
            return
        }
        onStartSettlement(session.sessionId) { success ->
            if (success) {
                settlementStarted = true
                showPaymentDialog = true
            } else {
                errorText = "정산 상태를 저장하지 못했어요."
            }
        }
    }

    fun completePayment() {
        errorText = null
        onCompleteSettlement(session.sessionId) { success ->
            if (success) {
                showPaymentDialog = false
                paymentMethod = null
                paymentCompleted = true
            } else {
                errorText = "결제 완료 상태를 저장하지 못했어요."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = TextMain)
            }
            Text(
                "정산하기",
                modifier = Modifier.weight(1f),
                color = TextMain,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(48.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "${session.storeName.ifBlank { "가게" }} · 테이블 $tableLabel",
                    color = TextSub,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgLight),
                    border = BorderStroke(1.dp, BorderMint),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("오늘 술자리 총 금액", color = TextSub, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("${currency.format(totalAmount)}원", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${session.startedAt.formatTimeLabel()} 시작 · ${session.startedAt.durationLabel(session.endedAt)} · ${rows.size.coerceAtLeast(1)}명",
                            color = TextDim,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            myRow?.let { row ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgLight),
                        border = BorderStroke(1.dp, BorderMint),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileInitial(name = row.name, size = 42)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${row.name}  나", color = TextMain, fontWeight = FontWeight.Bold)
                                Text("${row.drinkCount}잔 마심", color = TextDim, fontSize = 13.sp)
                            }
                            Text("${currency.format(row.amount)}원", color = Mint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Column {
                    Text("참가자 내역", color = TextSub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            val displayRows = rows.filterNot { it.isMe }.ifEmpty { rows }
                            displayRows.forEachIndexed { index, row ->
                                SettlementParticipantRow(
                                    row = row,
                                    status = if (row.isMe && paymentCompleted) "완료" else "미완료",
                                    showDivider = index < displayRows.lastIndex
                                )
                            }
                        }
                    }
                }
            }
            if (orders.isNotEmpty()) {
                item { OrderSummaryCard(orders) }
            }
            errorText?.let { message ->
                item {
                    Text(message, color = ErrorRed, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Button(
                    onClick = { openPaymentDialog() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, disabledContainerColor = BgMuted),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            if (myRow == null) "정산하기" else "${currency.format(myRow.amount)}원 결제하기",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    if (showPaymentDialog && myRow != null) {
        PaymentMethodDialog(
            amount = myRow.amount,
            paymentMethod = paymentMethod,
            isLoading = isLoading,
            onPaymentMethodChange = { paymentMethod = it },
            onDismiss = {
                showPaymentDialog = false
                paymentMethod = null
            },
            onCompletePayment = { completePayment() }
        )
    }

    if (paymentCompleted && myRow != null) {
        PaymentCompletedDialog(
            amount = myRow.amount,
            storeLabel = "${session.storeName.ifBlank { "가게" }} · 테이블 $tableLabel",
            onConfirm = {
                paymentCompleted = false
                onDone()
            }
        )
    }
}

@Composable
private fun PaymentMethodDialog(
    amount: Int,
    paymentMethod: String?,
    isLoading: Boolean,
    onPaymentMethodChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    onCompletePayment: () -> Unit
) {
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvc by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.widthIn(max = 440.dp)
            ) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("결제 방법", color = TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text("닫기", color = TextSub)
                        }
                    }
                    Text("결제 금액 ${currency.format(amount)}원", color = TextSub, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))

                    when (paymentMethod) {
                        null -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PaymentMethodTile("간편결제", "페이 선택", Modifier.weight(1f)) { onPaymentMethodChange("simple") }
                            PaymentMethodTile("신용/체크", "카드 입력", Modifier.weight(1f)) { onPaymentMethodChange("card") }
                            PaymentMethodTile("직접 결제", "매장 승인", Modifier.weight(1f)) { onPaymentMethodChange("direct") }
                        }
                        "simple" -> {
                            Text("간편결제 선택", color = TextSub, fontSize = 14.sp)
                            Spacer(Modifier.height(10.dp))
                            PaymentOptionButton("토스페이", onCompletePayment, isLoading)
                            PaymentOptionButton("카카오페이", onCompletePayment, isLoading)
                            PaymentOptionButton("네이버페이", onCompletePayment, isLoading)
                            TextButton(onClick = { onPaymentMethodChange(null) }, modifier = Modifier.fillMaxWidth()) {
                                Text("뒤로", color = TextSub)
                            }
                        }
                        "direct" -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BgLight),
                                border = BorderStroke(1.dp, Mint),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = Mint, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(14.dp))
                                    Text("대기 중", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("사장님의 승인을 기다리고 있습니다.", color = TextSub, fontSize = 13.sp, textAlign = TextAlign.Center)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onCompletePayment,
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Mint),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("승인 완료로 처리", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { onPaymentMethodChange(null) }, modifier = Modifier.fillMaxWidth()) {
                                Text("다른 결제 방법 선택", color = TextSub)
                            }
                        }
                        "card" -> {
                            Text("신용/체크 카드 정보 입력", color = TextSub, fontSize = 14.sp)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = cardNumber,
                                onValueChange = { cardNumber = it },
                                label = { Text("카드 번호") },
                                placeholder = { Text("0000-0000-0000-0000") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = cardExpiry,
                                    onValueChange = { cardExpiry = it },
                                    label = { Text("유효기간") },
                                    placeholder = { Text("MM/YY") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = cardCvc,
                                    onValueChange = { cardCvc = it.take(3) },
                                    label = { Text("CVC") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Button(
                                onClick = onCompletePayment,
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Mint),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("결제하기", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { onPaymentMethodChange(null) }, modifier = Modifier.fillMaxWidth()) {
                                Text("뒤로", color = TextSub)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(104.dp),
        colors = CardDefaults.cardColors(containerColor = BgLight),
        border = BorderStroke(1.dp, BorderMint),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TextDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PaymentOptionButton(title: String, onClick: () -> Unit, isLoading: Boolean) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BgLight, contentColor = TextMain),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderMint)
    ) {
        Text(title, color = TextMain, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

@Composable
private fun PaymentCompletedDialog(
    amount: Int,
    storeLabel: String,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onConfirm,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.widthIn(max = 360.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(Mint.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Mint, modifier = Modifier.size(42.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("결제 완료!", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${currency.format(amount)}원이 결제되었습니다", color = TextSub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(storeLabel, color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("확인", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlementParticipantRow(
    row: SettlementParticipantUi,
    status: String,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileInitial(name = row.name, size = 34)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name, color = TextMain)
                Text("${row.drinkCount}잔", color = TextDim, fontSize = 13.sp)
            }
            Text("${currency.format(row.amount)}원", color = TextMain, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(status, color = Mint, fontSize = 13.sp)
        }
        if (showDivider) {
            HorizontalDivider(color = BorderMint)
        }
    }
}

@Composable
private fun ProfileInitial(name: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(BgMuted),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1).ifBlank { "?" }, color = TextMain, fontSize = (size / 2.7f).sp)
    }
}

private fun buildSettlementRows(
    participants: List<SessionParticipant>,
    mappings: List<GlassUserMapping>,
    totalAmount: Int,
    myUserId: String
): List<SettlementParticipantUi> {
    val safeParticipants = participants.ifEmpty {
        listOf(SessionParticipant(userId = myUserId, userName = "사용자"))
    }
    val drinkCountByUser = mappings
        .groupBy { it.userId }
        .mapValues { (_, userMappings) -> userMappings.sumOf { it.drinkCount } }
    val totalDrinkCount = drinkCountByUser.values.sum()
    val fallbackShare = if (safeParticipants.isEmpty()) 0 else totalAmount / safeParticipants.size

    return safeParticipants.map { participant ->
        val drinks = drinkCountByUser[participant.userId] ?: 0
        val amount = when {
            totalAmount <= 0 -> 0
            totalDrinkCount > 0 -> totalAmount * drinks / totalDrinkCount
            else -> fallbackShare
        }
        SettlementParticipantUi(
            userId = participant.userId,
            name = participant.userName.ifBlank { "참여자" },
            drinkCount = drinks,
            amount = amount,
            isMe = participant.userId == myUserId
        )
    }
}

private fun pickAvailableGlassColor(
    usedColors: Set<String>,
    excluding: Set<String> = emptySet()
): String {
    val blocked = usedColors + excluding
    return SessionViewModel.GLASS_COLORS
        .filterNot { it.uppercase(Locale.US) in blocked }
        .ifEmpty { SessionViewModel.GLASS_COLORS }
        .random()
}

@Composable
private fun ProfileScreen(
    histories: List<DrinkHistoryItem>,
    healthSummary: HealthSummary?,
    profileTab: String,
    onProfileTabChange: (String) -> Unit,
    onHistoryClick: (DrinkHistoryItem) -> Unit,
    onDeleteHistory: (DrinkHistoryItem) -> Unit
) {
    var settingsScreen by remember { mutableStateOf<String?>(null) }

    settingsScreen?.let { screen ->
        SettingsDetailScreen(
            screen = screen,
            onBack = { settingsScreen = null }
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(BgMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Mint, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("사용자", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("잔잔과 함께한 술자리", color = TextDim, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("${histories.size}회", "총 술자리", Modifier.weight(1f))
                StatBox("${healthSummary?.totalDrinkCount ?: 0}잔", "총 마신 잔", Modifier.weight(1f))
                StatBox("${currency.format(healthSummary?.totalSpending ?: 0)}원", "총 지출", Modifier.weight(1f))
            }
        }
        item {
            val selectedIndex = when (profileTab) {
                "health" -> 1
                "settings" -> 2
                else -> 0
            }
            PrimaryTabRow(selectedTabIndex = selectedIndex) {
                Tab(selected = profileTab == "history", onClick = { onProfileTabChange("history") }, text = { Text("히스토리") })
                Tab(selected = profileTab == "health", onClick = { onProfileTabChange("health") }, text = { Text("내 상태") })
                Tab(selected = profileTab == "settings", onClick = { onProfileTabChange("settings") }, text = { Text("설정") })
            }
        }
        when (profileTab) {
            "history" -> {
                if (histories.isEmpty()) {
                    item { EmptyCard("히스토리가 없습니다.") }
                } else {
                    items(histories, key = { it.sessionId }) { history ->
                        HistoryRow(history, onClick = { onHistoryClick(history) }, onDelete = { onDeleteHistory(history) })
                    }
                }
            }
            "settings" -> item {
                SettingsContent(
                    onPersonalSettings = { settingsScreen = "personal" },
                    onAppSettings = { settingsScreen = "app" }
                )
            }
            else -> item { HealthContent(healthSummary) }
        }
    }
}

@Composable
private fun SettingsContent(
    onPersonalSettings: () -> Unit,
    onAppSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsEntry(
            title = "개인정보 설정",
            subtitle = "프로필, 닉네임 변경",
            onClick = onPersonalSettings
        )
        SettingsEntry(
            title = "앱 설정",
            subtitle = "알림, 테마 설정",
            onClick = onAppSettings
        )
    }
}

@Composable
private fun SettingsDetailScreen(screen: String, onBack: () -> Unit) {
    var nickname by remember { mutableStateOf("사용자") }
    var bio by remember { mutableStateOf("잔잔과 함께한 술자리") }
    var pushEnabled by remember { mutableStateOf(true) }
    var darkMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = TextMain)
            }
            Text(
                if (screen == "personal") "개인정보 설정" else "앱 설정",
                modifier = Modifier.weight(1f),
                color = TextMain,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(48.dp))
        }

        if (screen == "personal") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileInitial(name = nickname, size = 92)
                Text("프로필 사진 변경", color = TextSub, fontSize = 14.sp)
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("닉네임") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("자기소개") },
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsEntry(
                    title = "비밀번호 변경",
                    subtitle = "보안을 위해 주기적으로 변경하세요",
                    onClick = {}
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("저장하기", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsSwitchRow("알림", "주문과 정산 알림 받기", pushEnabled) { pushEnabled = it }
                SettingsSwitchRow("다크 모드", "어두운 테마 사용", darkMode) { darkMode = it }
                SettingsEntry(
                    title = "언어",
                    subtitle = "한국어",
                    onClick = {}
                )
                SettingsEntry(
                    title = "개인정보 보호",
                    subtitle = "계정과 활동 공개 범위",
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun SettingsEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = BgLight),
        border = BorderStroke(1.dp, BorderMint),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Mint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextMain, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextDim, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgLight),
        border = BorderStroke(1.dp, BorderMint),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = TextMain, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextDim, fontSize = 13.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun HealthContent(summary: HealthSummary?) {
    val weekly = summary?.weeklyDrinkCount ?: 0
    val (state, color, message) = when {
        weekly < 5 -> Triple("힘차요", Green, "주간 음주량이 낮은 편입니다.")
        weekly < 15 -> Triple("술을 원해요", Mint, "이번 주 음주량이 늘고 있습니다.")
        else -> Triple("아파요", ErrorRed, "충분한 휴식이 필요한 수준입니다.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
            border = BorderStroke(2.dp, color),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalBar, contentDescription = null, tint = color, modifier = Modifier.size(42.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("내 간: $state", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(message, color = TextSub, fontSize = 14.sp)
            }
        }

        SectionCard {
            Text("음주 요약", color = TextSub, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SummaryValue("총 음주 횟수", "${summary?.totalSessions ?: 0}회", Modifier.weight(1f))
                SummaryValue("주간 평균", "${weekly}잔", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SummaryValue("소주 누적 잔", "${summary?.totalSojuCount ?: 0}잔", Modifier.weight(1f))
                SummaryValue("맥주 누적 잔", "${summary?.totalBeerCount ?: 0}잔", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SummaryValue("소주 평균", "%.1f잔".format(summary?.avgSojuPerSession ?: 0f), Modifier.weight(1f))
                SummaryValue("맥주 평균", "%.1f잔".format(summary?.avgBeerPerSession ?: 0f), Modifier.weight(1f))
            }
        }

        DrinkCalendar(summary)
    }
}

@Composable
private fun DrinkCalendar(summary: HealthSummary?) {
    var offset by remember { mutableIntStateOf(0) }
    val month = YearMonth.now().plusMonths(offset.toLong())
    val dayCounts = summary?.calendarDays
        ?.filter { YearMonth.from(it.date) == month }
        ?.associate { it.date.dayOfMonth to it.totalCount }
        .orEmpty()
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cells = List(leading) { 0 } + (1..month.lengthOfMonth()).toList()
    val totalDays = dayCounts.count { it.value > 0 }
    val totalGlasses = dayCounts.values.sum()

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { offset-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", tint = TextSub)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${month.year}년 ${month.monthValue}월 음주 캘린더", color = TextMain, fontWeight = FontWeight.Bold)
                Text("${totalDays}일 · ${totalGlasses}잔", color = TextSub, fontSize = 12.sp)
            }
            IconButton(onClick = { if (offset < 0) offset++ }, enabled = offset < 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", tint = if (offset < 0) TextSub else TextDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { index, day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f),
                    color = if (index == 0) ErrorRed else TextDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                week.forEach { day ->
                    val count = dayCounts[day] ?: 0
                    val bg = when {
                        count >= 8 -> ErrorRed
                        count >= 1 -> Mint
                        else -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$day",
                                    color = if (count > 0) Color.White else TextSub,
                                    fontSize = 12.sp
                                )
                                if (count > 0) {
                                    Text("${count}잔", color = Color.White, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
            Spacer(Modifier.height(5.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendBox(Mint, "1-7잔")
            LegendBox(ErrorRed, "8잔+")
        }
    }
}

@Composable
private fun HistoryDetailDialog(history: DrinkHistoryItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인", color = Mint)
            }
        },
        title = { Text(history.storeName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${history.startedAt.formatDate()} · ${history.participantCount}명 · ${currency.format(history.myAmount)}원")
                HorizontalDivider()
                history.participants.forEach { participant ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(participant.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${participant.sojuCount}소주 · ${participant.beerCount}맥주", color = TextSub)
                    }
                }
            }
        }
    )
}

@Composable
private fun HeaderScaffold(
    title: String,
    onBack: () -> Unit,
    bottom: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(48.dp))
        }
        Box(modifier = Modifier.weight(1f)) { content() }
        Box(modifier = Modifier.padding(bottom = 24.dp)) { bottom() }
    }
}

@Composable
private fun JanjanBottomBar(tab: String, onTabChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(color = BorderMint)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .height(76.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                selected = tab == "ranking",
                label = "랭킹",
                icon = { Icon(Icons.Default.EmojiEvents, contentDescription = "랭킹") },
                onClick = { onTabChange("ranking") }
            )
            BottomNavItem(
                selected = tab == "home",
                label = "홈",
                isHome = true,
                icon = { Icon(Icons.Default.Home, contentDescription = "홈", tint = TextMain) },
                onClick = { onTabChange("home") }
            )
            BottomNavItem(
                selected = tab == "profile",
                label = "프로필",
                icon = { Icon(Icons.Default.Person, contentDescription = "프로필") },
                onClick = { onTabChange("profile") }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    isHome: Boolean = false
) {
    val tint = if (selected) Mint else TextDim
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isHome) {
            Box(
                modifier = Modifier
                    .offset(y = (-16).dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) Mint else BgMuted),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Text(label, color = tint, fontSize = 12.sp, modifier = Modifier.offset(y = (-12).dp))
        } else {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides tint
                ) {
                    icon()
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(label, color = tint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BoxScope.ScannerCorner(
    alignment: Alignment,
    top: Boolean = false,
    bottom: Boolean = false,
    start: Boolean = false,
    end: Boolean = false
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .size(32.dp)
    ) {
        if (top) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Mint, RoundedCornerShape(999.dp))
            )
        }
        if (bottom) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Mint, RoundedCornerShape(999.dp))
            )
        }
        if (start) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(32.dp)
                    .background(Mint, RoundedCornerShape(999.dp))
            )
        }
        if (end) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .height(32.dp)
                    .background(Mint, RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
private fun JanjanSurface(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
            content = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { content() } }
        )
    }
}

@Composable
private fun HomeStartButton(
    title: String,
    subtitle: String,
    primary: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val background = if (primary) Mint else BgLight
    val titleColor = if (primary) Color.White else TextMain
    val subtitleColor = if (primary) Color.White.copy(alpha = 0.82f) else TextDim
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        border = if (primary) null else BorderStroke(1.dp, BorderMint)
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (primary) Color.White.copy(alpha = 0.22f) else Mint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = subtitleColor, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgLight),
        border = BorderStroke(1.dp, BorderMint)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Mint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.height(10.dp))
            Text(title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgLight),
        border = BorderStroke(1.dp, BorderMint),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun OrderSummaryCard(items: List<OrderSummaryItem>) {
    SectionCard {
        Text("주문 내역", color = TextSub, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty()) {
            Text("아직 표시할 주문 내역이 없습니다.", color = TextDim, fontSize = 14.sp)
        } else {
            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${item.name} x${item.quantity}", color = TextMain)
                    Text("${currency.format(item.amount)}원", color = TextSub)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = BorderMint)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("합계", color = TextMain, fontWeight = FontWeight.Bold)
                Text("${currency.format(items.sumOf { it.amount })}원", color = Mint, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DrinkCount(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
        Text("${count}잔", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text(value, color = TextMain, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(label, color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = TextDim, fontSize = 13.sp)
        Text(value, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryRow(history: DrinkHistoryItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = BgLight),
        border = BorderStroke(1.dp, BorderMint),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalBar, contentDescription = null, tint = Mint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(history.storeName, color = TextMain, fontWeight = FontWeight.Bold)
                Text(
                    "${history.startedAt.formatDate()} · ${history.participantCount}명 · ${currency.format(history.myAmount)}원",
                    color = TextDim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "삭제", tint = TextDim)
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    SectionCard {
        Text(text, color = TextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Composable
private fun RankingPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Mint, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(14.dp))
        Text("랭킹", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("담당자 A/E 영역과 병합될 화면입니다.", color = TextDim, fontSize = 14.sp)
    }
}

@Composable
private fun LegendBox(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = TextSub, fontSize = 12.sp)
    }
}

private fun String.toComposeColor(): Color =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(Mint)

private fun com.google.firebase.Timestamp.formatDate(): String =
    SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(toDate().time))

private fun Long.formatTimeLabel(): String =
    takeIf { it > 0L }?.let { SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(it)) } ?: "--:--"

private fun Long.durationLabel(endedAt: Long): String {
    val startMillis = takeIf { it > 0L } ?: return "진행 중"
    val endMillis = endedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
    val minutes = ((endMillis - startMillis).coerceAtLeast(0L) / 60000L).toInt()
    val hours = minutes / 60
    val remains = minutes % 60
    return when {
        hours > 0 && remains > 0 -> "${hours}시간 ${remains}분"
        hours > 0 -> "${hours}시간"
        remains > 0 -> "${remains}분"
        else -> "방금 시작"
    }
}
