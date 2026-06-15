package com.gachon.janjan.domain.camera.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gachon.janjan.domain.camera.analyzer.CameraFrameAnalysisResult
import com.gachon.janjan.domain.camera.model.CameraDeviceConfig
import com.gachon.janjan.domain.camera.model.CameraDeviceUiState
import com.gachon.janjan.domain.camera.model.CvFrame
import com.gachon.janjan.domain.camera.repository.CameraDeviceRepository
import com.gachon.janjan.domain.camera.repository.CvFrameRepository
import com.gachon.janjan.domain.camera.repository.DirectDrinkCountRepository
import com.gachon.janjan.domain.session.FirebaseConfig
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraDeviceViewModel(
    private val deviceRepository: CameraDeviceRepository = CameraDeviceRepository(),
    private val frameRepository: CvFrameRepository = CvFrameRepository(),
    private val directDrinkCountRepository: DirectDrinkCountRepository = DirectDrinkCountRepository()
) : ViewModel() {
    private var tableListener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(CameraDeviceUiState())
    val uiState: StateFlow<CameraDeviceUiState> = _uiState.asStateFlow()

    fun configure(config: CameraDeviceConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            runCatching {
                ensureSignedIn()
                deviceRepository.registerDevice(config)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    deviceId = config.deviceId,
                    storeId = config.storeId,
                    tableId = config.tableId,
                    tableNumber = config.tableNumber,
                    nearDistanceThreshold = config.nearDistanceThreshold,
                    pourThresholdMs = config.pourThresholdMs,
                    colorMappingThresholdMs = config.colorMappingThresholdMs,
                    cameraStatus = CameraDeviceRepository.STATUS_WAITING,
                    isConfigured = true,
                    isLoading = false,
                    message = "카메라 장치가 등록되었습니다."
                )
                listenActiveSession(config)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "카메라 등록 실패: ${error.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    fun submitAnalysis(result: CameraFrameAnalysisResult) {
        val state = _uiState.value
        val sessionId = state.activeSessionId
        if (!state.isConfigured || sessionId.isBlank()) {
            _uiState.value = state.copy(lastDetectionSummary = "활성 세션 대기 중")
            return
        }

        val summary = if (result.detections.isEmpty()) {
            "감지 없음"
        } else {
            result.detections.groupingBy { it.objectType }.eachCount()
                .entries.joinToString { "${it.key} ${it.value}개" }
        }
        _uiState.value = state.copy(lastDetectionSummary = summary)

        if (result.detections.isEmpty()) return

        viewModelScope.launch {
            var directSummary = ""
            runCatching {
                frameRepository.submitFrame(
                    sessionId = sessionId,
                    frame = CvFrame(
                        cameraId = state.deviceId,
                        frameWidth = result.frameWidth,
                        frameHeight = result.frameHeight,
                        detections = result.detections,
                        nearDistanceThreshold = state.nearDistanceThreshold,
                        pourThresholdMs = state.pourThresholdMs,
                        colorMappingThresholdMs = state.colorMappingThresholdMs
                    )
                )
                val directResult = directDrinkCountRepository.processFrame(
                    sessionId = sessionId,
                    cameraId = state.deviceId,
                    detections = result.detections,
                    nearDistanceThreshold = state.nearDistanceThreshold,
                    pourThresholdMs = state.pourThresholdMs,
                    colorMappingThresholdMs = state.colorMappingThresholdMs
                )
                directSummary = directResult.toSummaryLabel()
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    lastDetectionSummary = if (directSummary.isBlank()) {
                        summary
                    } else {
                        "$summary · $directSummary"
                    },
                    lastUploadLabel = timeFormat.format(Date()),
                    message = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    message = "프레임 처리 실패: ${error.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun listenActiveSession(config: CameraDeviceConfig) {
        tableListener?.remove()
        tableListener = deviceRepository.listenTableSession(config) { activeSessionId ->
            val status = if (activeSessionId.isBlank()) {
                CameraDeviceRepository.STATUS_WAITING
            } else {
                CameraDeviceRepository.STATUS_RECOGNIZING
            }
            _uiState.value = _uiState.value.copy(
                activeSessionId = activeSessionId,
                cameraStatus = status,
                lastDetectionSummary = if (activeSessionId.isBlank()) {
                    "활성 세션 대기 중"
                } else {
                    "인식 준비 완료"
                }
            )
            viewModelScope.launch {
                runCatching {
                    deviceRepository.updateDeviceStatus(
                        config = config,
                        status = status,
                        activeSessionId = activeSessionId
                    )
                }
            }
        }
    }

    private suspend fun ensureSignedIn() {
        if (FirebaseConfig.auth.currentUser == null) {
            FirebaseConfig.auth.signInAnonymously().await()
        }
    }

    override fun onCleared() {
        tableListener?.remove()
        super.onCleared()
    }

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
    }
}
