package com.gachon.janjan.domain.camera.model

data class CvDetection(
    val trackId: String = "",
    val objectType: String = "",
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val x: Float? = null,
    val y: Float? = null,
    val width: Float? = null,
    val height: Float? = null,
    val confidence: Float = 0f,
    val screenColorHex: String? = null,
    val physicalGlassId: String? = null,
    val drinkType: String? = null
)
