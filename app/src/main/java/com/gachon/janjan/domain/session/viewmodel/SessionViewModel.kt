package com.gachon.janjan.domain.session.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gachon.janjan.data.model.Session
import com.gachon.janjan.data.repository.PaymentRepository
import com.gachon.janjan.data.repository.StatusRepository
import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.model.GlassUserMapping
import com.gachon.janjan.domain.session.model.OrderSummaryItem
import com.gachon.janjan.domain.session.model.SessionParticipant
import com.gachon.janjan.domain.session.model.UserProfile
import com.gachon.janjan.domain.session.repository.DetectionEventRepository
import com.gachon.janjan.domain.session.repository.GlassMappingRepository
import com.gachon.janjan.domain.session.repository.ParticipantRepository
import com.gachon.janjan.domain.session.repository.SessionRepository
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class SessionViewModel(
    private val sessionRepo: SessionRepository = SessionRepository(),
    private val participantRepo: ParticipantRepository = ParticipantRepository(),
    private val mappingRepo: GlassMappingRepository = GlassMappingRepository(),
    private val detectionRepo: DetectionEventRepository = DetectionEventRepository(),
    private val statusRepo: StatusRepository = StatusRepository(),
    private val paymentRepo: PaymentRepository = PaymentRepository()
) : ViewModel() {
    private var sessionListener: ListenerRegistration? = null
    private var mappingListener: ListenerRegistration? = null
    private var participantListener: ListenerRegistration? = null

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
                val doc = FirebaseConfig.db.collection("users").document(uid).get().await()
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
                    address = doc.getString("address").orEmpty()
                )
            }.onSuccess { profile ->
                _userProfile.value = profile
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

    fun loadLatestActiveSession() {
        viewModelScope.launch {
            runCatching {
                ensureSignedIn()
                sessionRepo.findLatestActiveSessionForUser(currentUserId)
            }.onSuccess { session ->
                if (session != null) {
                    startListening(session.sessionId)
                    loadOrderSummaries(session.sessionId)
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
                    participantRepo.joinSession(session.sessionId, currentUserId, currentUserName)
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
                    participantRepo.joinSession(session.sessionId, currentUserId, currentUserName)
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
                participantRepo.joinSession(sessionId, currentUserId, currentUserName)
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

    fun loadOrderSummaries(sessionId: String) {
        viewModelScope.launch {
            runCatching { sessionRepo.loadOrderSummaries(sessionId) }
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

    fun completeSettlement(sessionId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ensureSignedIn()
                paymentRepo.completeSettlement(sessionId)
            }.onSuccess {
                _message.value = "정산이 완료되었습니다."
                clearActiveSession()
                onComplete(true)
            }.onFailure {
                _message.value = "정산 완료 실패: ${it.message ?: "알 수 없는 오류"}"
                onComplete(false)
            }
            _isLoading.value = false
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
        sessionListener = sessionRepo.listenToSession(sessionId) { session ->
            if (session == null || session.status == "closed") {
                clearActiveSession()
            } else {
                _activeSession.value = session
            }
        }
        mappingListener = mappingRepo.listenToMappings(sessionId) { _glassMappings.value = it }
        participantListener = participantRepo.listenParticipants(sessionId) { _participants.value = it }
    }

    private fun clearActiveSession() {
        sessionListener?.remove()
        mappingListener?.remove()
        participantListener?.remove()
        sessionListener = null
        mappingListener = null
        participantListener = null
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
            val sessionId = uri.getQueryParameter("sessionId")
                ?: uri.getQueryParameter("session_id")
            val inviteCode = uri.getQueryParameter("inviteCode")
                ?: uri.getQueryParameter("invite_code")
                ?: uri.getQueryParameter("code")
            if (!sessionId.isNullOrBlank() || !inviteCode.isNullOrBlank()) {
                return sessionId to inviteCode
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
