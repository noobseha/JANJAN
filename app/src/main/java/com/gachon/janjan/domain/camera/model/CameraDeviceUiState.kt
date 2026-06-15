package com.gachon.janjan.domain.camera.model

data class CameraDeviceUiState(
    val deviceId: String = "",
    val storeId: String = "",
    val tableId: String = "",
    val tableNumber: Int = 0,
    val nearDistanceThreshold: Float = 0.08f,
    val pourThresholdMs: Int = 1500,
    val colorMappingThresholdMs: Int = 1800,
    val activeSessionId: String = "",
    val cameraStatus: String = "setup",
    val isConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val lastDetectionSummary: String = "감지 대기 중",
    val lastUploadLabel: String = "-",
    val message: String? = null
) {
    val isRecognizing: Boolean
        get() = isConfigured && activeSessionId.isNotBlank()
}
