package com.gachon.janjan.domain.camera.model

data class CameraDeviceConfig(
    val deviceId: String = "",
    val storeId: String = "",
    val tableId: String = "",
    val tableNumber: Int = 0,
    val confidenceThreshold: Float = 0.35f,
    val nearDistanceThreshold: Float = 0.08f,
    val pourThresholdMs: Int = 1500,
    val colorMappingThresholdMs: Int = 1800
)
