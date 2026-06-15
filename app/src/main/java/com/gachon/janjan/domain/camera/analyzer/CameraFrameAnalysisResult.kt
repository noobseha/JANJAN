package com.gachon.janjan.domain.camera.analyzer

import com.gachon.janjan.domain.camera.model.CvDetection

data class CameraFrameAnalysisResult(
    val frameWidth: Int,
    val frameHeight: Int,
    val detections: List<CvDetection>
)
