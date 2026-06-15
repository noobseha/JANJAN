package com.gachon.janjan.domain.session.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gachon.janjan.data.model.Session
import com.gachon.janjan.data.model.Settlement
import com.gachon.janjan.data.model.SettlementParticipant
import com.gachon.janjan.data.repository.PaymentRepository
import com.gachon.janjan.data.repository.SettlementRepository
import com.gachon.janjan.data.repository.StatusRepository
import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.ActivityVisibility
import com.gachon.janjan.domain.session.model.GlassUserMapping
import com.gachon.janjan.domain.session.model.OrderSummaryItem
import com.gachon.janjan.domain.session.model.SessionParticipant
import com.gachon.janjan.domain.session.model.UserProfile
import com.gachon.janjan.domain.session.repository.DetectionEventRepository
import com.gachon.janjan.domain.session.repository.GlassMappingRepository
import com.gachon.janjan.domain.session.repository.ParticipantRepository
import com.gachon.janjan.domain.session.repository.RankingAggregationRepository
import com.gachon.janjan.domain.session.repository.SessionRepository
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

class SessionViewModel(
    private val sessionRepo: SessionRepository = SessionRepository(),
    private val participantRepo: ParticipantRepository = ParticipantRepository(),
    private val mappingRepo: GlassMappingRepository = GlassMappingRepository(),
    private val detectionRepo: DetectionEventRepository = DetectionEventRepository(),
    private val statusRepo: StatusRepository = StatusRepository(),
    private val paymentRepo: PaymentRepository = PaymentRepository(),
    private val settlementRepo: SettlementRepository = SettlementRepository(),
    private val rankingAggregationRepo: RankingAggregationRepository = RankingAggregationRepository()
) : ViewModel() {
    private var sessionListener: ListenerRegistration? = null
    private var mappingListener: ListenerRegistration? = null
    private var participantListener: ListenerRegistration? = null
    private var orderListener: ListenerRegistration? = null

    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession: StateFlow<Session?> = _activeSession.asStateFlow()

    private val _activeSessionId = MutableStateFlow("")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _glassMappings = MutableStateFlow<List<GlassUserMapping>>(emptyList())
    val glassMappings: StateFlow<List<GlassUserMapping>> = _glassMappings.asStateFlow()

    private val _participants = MutableStateFlow<List<SessionParticipant>>(emptyList())
    val participants: StateFlow<List<SessionParticipant>> = _participants.asStateFlow()

    private val _orderItems = MutableStateFlow<List<OrderSummaryItem>>(emptyList())
    val orderItems: StateFlow<List<OrderSummaryItem>> = _orderItems.asStateFlow()

    private val _lastOrderedItems = MutableStateFlow<List<OrderSummaryItem>>(emptyList())
    val lastOrderedItems: StateFlow<List<OrderSummaryItem>> = _lastOrderedItems.asStateFlow()

    fun setLastOrderedItems(items: List<OrderSummaryItem>) {
        _lastOrderedItems.value = items
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _externalPaymentCompleteEvent = MutableSharedFlow<String>()
    val externalPaymentCompleteEvent: SharedFlow<String> = _externalPaymentCompleteEvent.asSharedFlow()

    fun triggerExternalPaymentComplete(method: String) {
        viewModelScope.launch {
            _externalPaymentCompleteEvent.emit(method)
        }
    }

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    val currentUserId: String
        get() = FirebaseConfig.auth.currentUser?.uid ?: PENDING_USER_ID

    private val currentUserName: String
        get() = _userProfile.value.nickname.ifBlank {
            FirebaseConfig.auth.currentUser?.displayName
            ?: FirebaseConfig.auth.currentUser?.email?.substringBefore("@")
            ?: "사용자"
        }

    init {
        loadLatestActiveSession()
        loadUserProfile()
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            runCatching {
                ensureSignedIn()
                val uid = currentUserId
                val doc = FirebaseConfig.db.collection(FirestorePaths.USERS).document(uid).get().await()
                val settingsDoc = FirebaseConfig.db.collection(FirestorePaths.USER_APP_SETTINGS)
                    .document(uid)
                    .get()
                    .await()
                val fallbackName = FirebaseConfig.auth.currentUser?.displayName
                    ?: FirebaseConfig.auth.currentUser?.email?.substringBefore("@")
                    ?: "사용자"
                UserProfile(
                    userId = uid,
                    nickname = doc.getString("nickname")
                        ?: doc.getString("name")
                        ?: fallbackName,
                    bio = doc.getString("bio")
                        ?: doc.getString("description")
                        ?: "잔잔과 함께한 술자리",
                    phone = doc.getString("phone").orEmpty(),
                    address = doc.getString("address").orEmpty(),
                    imageUrl = doc.getString("imageUrl").orEmpty(),
                    activityVisibility = ActivityVisibility.fromStorage(
                        value = settingsDoc.getString("activity_visibility"),
                        legacyPrivate = settingsDoc.getBoolean("is_private_account")
                    )
                )
            }.onSuccess { profile ->
                _userProfile.value = profile
                activeSession.value?.sessionId?.let { sessionId ->
                    FirebaseConfig.db.collection(FirestorePaths.participants(sessionId))
                        .document(profile.userId)
                        .set(mapOf("imageUrl" to profile.imageUrl, "userName" to profile.nickname), SetOptions.merge())
                }
            }
        }
    }

    fun saveUserProfile(nickname: String, bio: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ensureSignedIn()
                val cleanedNickname = nickname.trim().ifBlank { "사용자" }
                val cleanedBio = bio.trim().ifBlank { "잔잔과 함께한 술자리" }
                FirebaseConfig.db.collection("users").document(currentUserId)
                    .set(
                        mapOf(
                            "nickname" to cleanedNickname,
                            "bio" to cleanedBio,
                            "updatedAt" to com.google.firebase.Timestamp.now()
                        ),
                        SetOptions.merge()
                    )
                    .await()
                _userProfile.value = _userProfile.value.copy(
                    userId = currentUserId,
                    nickname = cleanedNickname,
                    bio = cleanedBio
                )
            }.onSuccess {
                _message.value = "프로필이 저장되었습니다."
                onComplete(true)
            }.onFailure {
                _message.value = "프로필 저장 실패: ${it.message ?: "알 수 없는 오류"}"
                onComplete(false)
            }
            _isLoading.value = false
        }
    }

    fun uploadProfileImage(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ensureSignedIn()
                val file = com.gachon.janjan.utils.ImageUtils.getFileFromUri(context, uri)
                    ?: throw IllegalStateException("이미지를 불러올 수 없습니다")
                
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = okhttp3.MultipartBody.Part.createFormData("image", file.name, requestFile)
                val userIdBody = currentUserId.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.gachon.janjan.api.RetrofitClient.api.uploadImage(userIdBody, body).execute()
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val url = response.body()?.url ?: ""
                    FirebaseConfig.db.collection("users").document(currentUserId)
                        .set(mapOf("imageUrl" to url), SetOptions.merge())
                        .await()
                    _userProfile.value = _userProfile.value.copy(imageUrl = url)
                    
                    activeSession.value?.sessionId?.let { sessionId ->
                        FirebaseConfig.db.collection(FirestorePaths.participants(sessionId))
                            .document(currentUserId)
                            .set(mapOf("imageUrl" to url), SetOptions.merge())
                            .await()
                    }
                    
                    "프로필 사진이 변경되었습니다."
                } else {
                    throw IllegalStateException("업로드 실패: ${response.code()}")
                }
            }.onSuccess { msg ->
                _message.value = msg
            }.onFailure {
                _message.value = "프로필 사진 변경 실패: ${it.message ?: "알 수 없는 오류"}"
            }
            _isLoading.value = false
        }
    }

    fun saveActivityVisibility(
        visibility: ActivityVisibility,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ensureSignedIn()
                FirebaseConfig.db.collection(FirestorePaths.USER_APP_SETTINGS)
                    .document(currentUserId)
                    .set(
                        mapOf(
                            "activity_visibility" to visibility.storageValue,
                            "is_private_account" to (visibility == ActivityVisibility.PRIVATE),
                            "updated_at" to com.google.firebase.Timestamp.now()
                        ),
                        SetOptions.merge()
                    )
                    .await()
                _userProfile.value = _userProfile.value.copy(
                    userId = currentUserId,
                    activityVisibility = visibility
                )
            }.onSuccess {
                _message.value = "공개 범위가 저장되었습니다."
                onComplete(true)
            }.onFailure {
                _message.value = "공개 범위 저장 실패: ${it.message ?: "알 수 없는 오류"}"
                onComplete(false)
            }
            _isLoading.value = false
        }
    }

    fun loadLatestActiveSession() {
        viewModelScope.launch {
            runCatching {
                ensureSignedIn()
                sessionRepo.findLatestActiveSessionForUser(currentUserId)
            }.onSuccess { session ->
                if (session != null) {
                    startListening(session.sessionId)
                    loadOrderSummaries(session.sessionId)
                    FirebaseConfig.db.collection(FirestorePaths.participants(session.sessionId))
                        .document(currentUserId)
                        .set(
                            mapOf(
                                "imageUrl" to _userProfile.value.imageUrl,
                                "userName" to _userProfile.value.nickname.ifBlank { "사용자" }
                            ),
                            SetOptions.merge()
                        )
                }
            }
        }
    }

    fun joinByInviteCode(code: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ensureSignedIn()
                val session = sessionRepo.findByInviteCode(code)
                if (session != null) {
                    participantRepo.joinSession(session.sessionId, currentUserId, currentUserName, _userProfile.value.imageUrl)
                    sessionRepo.syncTableActiveSession(session)
                    startListening(session.sessionId)
                    loadOrderSummaries(session.sessionId)
                }
                session
            }.onSuccess { session ->
                if (session == null) {
                    _message.value = "활성 세션을 찾을 수 없습니다."
                    onResult(false, null)
                } else {
                    _message.value = "테이블에 연결되었습니다."
                    onResult(true, session.sessionId)
                }
            }.onFailure {
                _message.value = "초대코드 연결 실패: ${it.message ?: "알 수 없는 오류"}"
                onResult(false, null)
            }
            _isLoading.value = false
        }
    }

    fun joinByQrPayload(payload: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ensureSignedIn()
                val (sessionId, inviteCode) = parseQrPayload(payload)
                val session = when {
                    !sessionId.isNullOrBlank() -> sessionRepo.getSession(sessionId)
                    !inviteCode.isNullOrBlank() -> sessionRepo.findByInviteCode(inviteCode)
                    else -> null
                }
                if (session != null) {
                    participantRepo.joinSession(session.sessionId, currentUserId, currentUserName, _userProfile.value.imageUrl)
                    sessionRepo.syncTableActiveSession(session)
                    startListening(session.sessionId)
                    loadOrderSummaries(session.sessionId)
                }
                session
            }.onSuccess { session ->
                if (session == null) {
                    _message.value = "유효한 테이블 QR을 찾을 수 없습니다."
                    onResult(false, null)
                } else {
                    _message.value = "테이블에 연결되었습니다."
                    onResult(true, session.sessionId)
                }
            }.onFailure {
                _message.value = "QR 연결 실패: ${it.message ?: "알 수 없는 오류"}"
                onResult(false, null)
            }
            _isLoading.value = false
        }
    }

    fun assignGlassColor(
        sessionId: String,
        color: String,
        drinkType: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ensureSignedIn()
                val normalizedColor = color.normalizeHexColor()
                participantRepo.joinSession(sessionId, currentUserId, currentUserName, _userProfile.value.imageUrl)
                participantRepo.updateGlassColor(sessionId, currentUserId, normalizedColor)
                mappingRepo.createPendingColorMapping(sessionId, currentUserId, normalizedColor, drinkType)
                startListening(sessionId)
                loadOrderSummaries(sessionId)
            }.onSuccess {
                _message.value = "색상 화면이 배정되었습니다. 실제 술잔은 카메라가 5초 인식하면 연결됩니다."
                onComplete()
            }.onFailure {
                _message.value = "술잔 매핑 실패: ${it.message ?: "알 수 없는 오류"}"
            }
            _isLoading.value = false
        }
    }

    fun loadOrderSummaries(sessionId: String, storeId: String? = null) {
        viewModelScope.launch {
            var actualStoreId = storeId ?: _activeSession.value?.storeId
            if (actualStoreId == null) {
                val sessionDoc = FirebaseConfig.db.collection(FirestorePaths.SESSIONS).document(sessionId).get().await()
                actualStoreId = sessionDoc.getString("storeId")
            }
            runCatching { sessionRepo.loadOrderSummaries(sessionId, actualStoreId) }
                .onSuccess { _orderItems.value = it }
        }
    }

    fun startSettlement(sessionId: String, onComplete: (Boolean) -> Unit) {
        _isLoading.value = true
        statusRepo.startSettlement(sessionId) { success ->
            _isLoading.value = false
            _message.value = if (success) {
                "정산이 시작되었습니다."
            } else {
                "정산 시작 실패"
            }
            onComplete(success)
        }
    }

    fun completeSettlement(
        sessionId: String,
        paymentMethod: String = "앱 결제",
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ensureSignedIn()
                saveSettlementDocument(sessionId)
                    ?: error("정산 문서를 저장하지 못했습니다.")
                val result = paymentRepo.completeSettlement(
                    sessionId = sessionId,
                    userId = currentUserId,
                    paymentMethod = paymentMethod
                )
                if (result.sessionClosed) {
                    rankingAggregationRepo.aggregateClosedSession(sessionId)
                }
                result
            }.onSuccess { result ->
                _message.value = if (result.sessionClosed) {
                    clearActiveSession()
                    "모든 참가자의 정산이 완료되었습니다."
                } else if (paymentMethod == PaymentRepository.DIRECT_PAYMENT_METHOD) {
                    "직접 결제 승인 대기 상태로 저장되었습니다."
                } else {
                    "내 결제 완료 상태가 저장되었습니다."
                }
                onComplete(true)
            }.onFailure {
                _message.value = "정산 완료 실패: ${it.message ?: "알 수 없는 오류"}"
                onComplete(false)
            }
            _isLoading.value = false
        }
    }

    private suspend fun saveSettlementDocument(sessionId: String): String? {
        val session = _activeSession.value ?: return null
        val currentParticipants = _participants.value
        val currentOrders = runCatching {
            sessionRepo.loadOrderSummaries(sessionId, session.storeId)
        }.getOrElse {
            _orderItems.value
        }
        _orderItems.value = currentOrders
        if (currentParticipants.isEmpty()) return null

        val totalAmount = currentOrders.sumOf { it.amount }
        val sessionTotalSojuPrice = currentOrders.filter { it.category.contains("소주") || it.category.equals("soju", ignoreCase = true) || (it.category.isBlank() && it.name.contains("소주")) }.sumOf { it.amount }
        val sessionTotalBeerPrice = currentOrders.filter { it.category.contains("맥주") || it.category.equals("beer", ignoreCase = true) || (it.category.isBlank() && it.name.contains("맥주")) }.sumOf { it.amount }
        val sessionTotalFoodPrice = totalAmount - sessionTotalSojuPrice - sessionTotalBeerPrice
        val headCount = currentParticipants.size

        // Updated calculation logic: split by soju, beer, and food
        val totalSojuCount = currentParticipants.sumOf { it.sojuDrinkCount }
        val totalBeerCount = currentParticipants.sumOf { it.beerDrinkCount }

        val settlementParticipants = currentParticipants.map { participant ->
            var participantPrice = 0
            
            // 1. N-빵 (Food)
            var foodTotal = 0
            if (headCount > 0) {
                foodTotal = sessionTotalFoodPrice / headCount
                participantPrice += foodTotal
            }
            // 2. Soju share
            var sojuTotal = 0
            if (totalSojuCount > 0) {
                sojuTotal = (sessionTotalSojuPrice * participant.sojuDrinkCount) / totalSojuCount
                participantPrice += sojuTotal
            }
            // 3. Beer share
            var beerTotal = 0
            if (totalBeerCount > 0) {
                beerTotal = (sessionTotalBeerPrice * participant.beerDrinkCount) / totalBeerCount
                participantPrice += beerTotal
            }
            
            // Fallback for cases where session prices might be zero, use simple division if totalAmount is strictly > 0 but participantPrice is 0
            if (participantPrice == 0 && totalAmount > 0 && sessionTotalSojuPrice == 0 && sessionTotalBeerPrice == 0 && sessionTotalFoodPrice == 0) {
                val totalDrinkCount = totalSojuCount + totalBeerCount
                val drinkCount = participant.sojuDrinkCount + participant.beerDrinkCount
                if (totalDrinkCount > 0) {
                    participantPrice = (totalAmount.toLong() * drinkCount / totalDrinkCount).toInt()
                } else if (headCount > 0) {
                    participantPrice = totalAmount / headCount
                }
            }
            
            participantRepo.updateParticipantTotals(
                sessionId = sessionId,
                userId = participant.userId,
                foodTotal = foodTotal,
                sojuTotal = sojuTotal,
                beerTotal = beerTotal,
                totalPrice = participantPrice
            )

            SettlementParticipant(
                userId = participant.userId,
                userName = participant.userName,
                mytotal = participantPrice,
                beerCupCount = participant.beerDrinkCount,
                sojuCupCount = participant.sojuDrinkCount,
                paidStatus = false
            )
        }

        val firstJoinedAt = currentParticipants.mapNotNull { it.joinedAt?.seconds }.minOrNull()?.takeIf { it > 0L }
        val timeInfo = if (firstJoinedAt != null) {
            val startMillis = firstJoinedAt * 1000L
            val startTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date(startMillis))
            val diffMillis = System.currentTimeMillis() - startMillis
            val hours = diffMillis / (1000 * 60 * 60)
            val minutes = (diffMillis % (1000 * 60 * 60)) / (1000 * 60)
            val duration = if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
            "$startTime 시작 · $duration · ${headCount}명"
        } else {
            "시작 시간 정보 없음 · ${headCount}명"
        }

        val settlement = Settlement(
            sessionId = session.sessionId,
            storeName = session.storeName,
            tableId = session.tableNumber.takeIf { it > 0 } ?: session.tableId.toIntOrNull() ?: 0,
            totalPrice = totalAmount,
            timeInfo = timeInfo,
            participants = settlementParticipants
        )

        // Save to Firestore synchronously via suspendCancellableCoroutine
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            settlementRepo.createSettlement(settlement) { settlementId ->
                if (settlementId != null) {
                    cont.resume(settlementId) {}
                } else {
                    cont.resume(null) {}
                }
            }
        }
    }

    fun startDetection(sessionId: String, glassId: String, onReady: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { detectionRepo.insertDetection(sessionId, glassId) }
                .onSuccess(onReady)
                .onFailure { _message.value = "감지 이벤트 생성 실패: ${it.message}" }
        }
    }

    fun startColorGlassMappingDetection(
        sessionId: String,
        screenColorHex: String,
        physicalGlassId: String,
        onReady: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                detectionRepo.insertColorGlassProximity(sessionId, screenColorHex, physicalGlassId)
            }.onSuccess(onReady)
                .onFailure { _message.value = "색상-술잔 매핑 이벤트 생성 실패: ${it.message}" }
        }
    }

    fun releaseDetection(sessionId: String, eventId: String) {
        viewModelScope.launch {
            runCatching { detectionRepo.updateReleased(sessionId, eventId) }
                .onFailure { _message.value = "감지 이벤트 업데이트 실패: ${it.message}" }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun startListening(sessionId: String) {
        _activeSessionId.value = sessionId
        sessionListener?.remove()
        mappingListener?.remove()
        participantListener?.remove()
        orderListener?.remove()
        sessionListener = sessionRepo.listenToSession(sessionId) { session ->
            if (session == null || session.status == "closed") {
                clearActiveSession()
            } else {
                _activeSession.value = session
            }
        }
        mappingListener = mappingRepo.listenToMappings(sessionId) { _glassMappings.value = it }
        participantListener = participantRepo.listenParticipants(sessionId) { _participants.value = it }
        orderListener = sessionRepo.listenOrderSummaries(
            sessionId = sessionId,
            onUpdate = { _orderItems.value = it },
            onError = { _message.value = "주문 내역을 불러오지 못했습니다: ${it.message ?: "알 수 없는 오류"}" }
        )
    }

    private fun clearActiveSession() {
        sessionListener?.remove()
        mappingListener?.remove()
        participantListener?.remove()
        orderListener?.remove()
        sessionListener = null
        mappingListener = null
        participantListener = null
        orderListener = null
        _activeSession.value = null
        _activeSessionId.value = ""
        _glassMappings.value = emptyList()
        _participants.value = emptyList()
        _orderItems.value = emptyList()
    }

    private fun parseQrPayload(payload: String): Pair<String?, String?> {
        val trimmed = payload.trim()
        if (trimmed.isBlank()) return null to null

        runCatching { Uri.parse(trimmed) }.getOrNull()?.let { uri ->
            if (!uri.isOpaque) {
                val sessionId = uri.getQueryParameter("sessionId")
                    ?: uri.getQueryParameter("session_id")
                val inviteCode = uri.getQueryParameter("inviteCode")
                    ?: uri.getQueryParameter("invite_code")
                    ?: uri.getQueryParameter("code")
                if (!sessionId.isNullOrBlank() || !inviteCode.isNullOrBlank()) {
                    return sessionId to inviteCode
                }
            }
            uri.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }?.let { pathId ->
                if (pathId.length <= 8 && pathId.all { it.isLetterOrDigit() }) {
                    return null to pathId
                }
                return pathId to null
            }
        }

        val compact = trimmed.removePrefix("session:").removePrefix("code:")
        return if (compact.length in 4..8 && compact.all { it.isLetterOrDigit() }) {
            null to compact
        } else {
            compact to null
        }
    }

    private suspend fun ensureSignedIn() {
        if (FirebaseConfig.auth.currentUser == null) {
            FirebaseConfig.auth.signInAnonymously().await()
        }
    }

    override fun onCleared() {
        sessionListener?.remove()
        mappingListener?.remove()
        participantListener?.remove()
        super.onCleared()
    }

    private fun String.normalizeHexColor(): String {
        val compact = trim().removePrefix("#").lowercase(Locale.US)
        return "#$compact"
    }

    companion object {
        private const val PENDING_USER_ID = "anonymous_pending_user"

        val GLASS_COLORS = listOf(
            "#ef4444",
            "#3b82f6",
            "#22c55e",
            "#eab308",
            "#8b5cf6",
            "#ec4899",
            "#06b6d4",
            "#14b8a6"
        )
    }
}
