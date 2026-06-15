package com.gachon.janjan.domain.camera.model

import com.google.firebase.firestore.FieldValue

data class CvFrame(
    val cameraId: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val detections: List<CvDetection>,
    val nearDistanceThreshold: Float = 0.08f,
    val pourThresholdMs: Int = 1500,
    val colorMappingThresholdMs: Int = 1800,
    val capturedAt: Any = FieldValue.serverTimestamp(),
    val createdAt: Any = FieldValue.serverTimestamp()
)
