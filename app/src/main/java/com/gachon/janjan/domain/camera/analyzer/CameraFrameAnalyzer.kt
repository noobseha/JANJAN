package com.gachon.janjan.domain.camera.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.gachon.janjan.domain.camera.model.CvDetection
import java.io.Closeable
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.min

class CameraFrameAnalyzer(
    context: Context,
    private val cameraId: String,
    confidenceThreshold: Float = 0.35f,
    private val onFrameAnalyzed: (CameraFrameAnalysisResult) -> Unit
) : ImageAnalysis.Analyzer, Closeable {
    private val yoloDetector = YoloOnnxDetector(
        context.applicationContext,
        cameraId,
        confidenceThreshold
    )
    private var lastAnalyzedAt: Long = 0L
    private var yoloUnavailable: Boolean = false

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastAnalyzedAt < ANALYSIS_INTERVAL_MS) return
            lastAnalyzedAt = now

            val bitmap = image.toBitmapOrNull()
                ?.rotate(image.imageInfo.rotationDegrees)
                ?: return
            val detection = detectColorScreen(bitmap.width, bitmap.height) { x, y ->
                bitmap.getPixel(x, y)
            }
            val yoloDetections = detectYolo(bitmap)
            onFrameAnalyzed(
                CameraFrameAnalysisResult(
                    frameWidth = bitmap.width,
                    frameHeight = bitmap.height,
                    detections = yoloDetections + listOfNotNull(detection)
                )
            )
            bitmap.recycle()
        } finally {
            image.close()
        }
    }

    private fun detectYolo(bitmap: Bitmap): List<CvDetection> {
        if (yoloUnavailable) return emptyList()
        return runCatching {
            yoloDetector.detect(bitmap)
        }.onFailure {
            yoloUnavailable = true
        }.getOrDefault(emptyList())
    }

    private fun detectColorScreen(
        width: Int,
        height: Int,
        pixelAt: (Int, Int) -> Int
    ): CvDetection? {
        val step = (width.coerceAtMost(height) / 32).coerceAtLeast(6)
        val hueBuckets = mutableMapOf<String, Bucket>()

        var y = step
        while (y < height - step) {
            var x = step
            while (x < width - step) {
                val color = pixelAt(x, y)
                val bucket = colorBucket(color)
                if (bucket != null) {
                    hueBuckets.getOrPut(bucket) { Bucket(bucket) }.add(x, y)
                }
                x += step
            }
            y += step
        }

        val best = hueBuckets.values.maxByOrNull { it.count } ?: return null
        if (best.count < MIN_COLOR_POINTS) return null

        val boxWidth = (best.maxX - best.minX + step).coerceAtLeast(step)
        val boxHeight = (best.maxY - best.minY + step).coerceAtLeast(step)
        val boxWidthRatio = boxWidth.toFloat() / width
        val boxHeightRatio = boxHeight.toFloat() / height
        val sampleColumns = (boxWidth / step).coerceAtLeast(1)
        val sampleRows = (boxHeight / step).coerceAtLeast(1)
        val density = best.count.toFloat() / (sampleColumns * sampleRows).coerceAtLeast(1)
        val aspectRatio = boxWidthRatio / boxHeightRatio.coerceAtLeast(0.01f)

        if (boxWidthRatio < MIN_SCREEN_WIDTH_RATIO || boxHeightRatio < MIN_SCREEN_HEIGHT_RATIO) {
            return null
        }
        if (density < MIN_SCREEN_COLOR_DENSITY) return null
        if (aspectRatio !in MIN_SCREEN_ASPECT_RATIO..MAX_SCREEN_ASPECT_RATIO) return null

        val centerX = best.sumX.toFloat() / best.count / width
        val centerY = best.sumY.toFloat() / best.count / height
        return CvDetection(
            trackId = "screen_${cameraId}_${best.hex.removePrefix("#")}",
            objectType = "phone_screen",
            centerX = centerX,
            centerY = centerY,
            x = (best.minX.toFloat() / width).coerceIn(0f, 1f),
            y = (best.minY.toFloat() / height).coerceIn(0f, 1f),
            width = boxWidthRatio.coerceIn(0f, 1f),
            height = boxHeightRatio.coerceIn(0f, 1f),
            confidence = (0.35f + density * 0.45f + best.count / 260f).coerceIn(0.35f, 1f),
            screenColorHex = best.hex
        )
    }

    private fun colorBucket(color: Int): String? {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]
        if (saturation < 0.45f || value < 0.35f) return null
        return GLASS_COLOR_BUCKETS.minByOrNull { bucket ->
            hsvDistance(
                hue = hue,
                saturation = saturation,
                value = value,
                targetHue = bucket.hue,
                targetSaturation = bucket.saturation,
                targetValue = bucket.value
            )
        }?.hex
    }

    private fun hsvDistance(
        hue: Float,
        saturation: Float,
        value: Float,
        targetHue: Float,
        targetSaturation: Float,
        targetValue: Float
    ): Float {
        val hueDistance = min(abs(hue - targetHue), 360f - abs(hue - targetHue)) / 180f
        val saturationDistance = abs(saturation - targetSaturation)
        val valueDistance = abs(value - targetValue)
        return hueDistance * 2.4f + saturationDistance * 0.7f + valueDistance * 0.5f
    }

    private class Bucket(
        val hex: String
    ) {
        var count: Int = 0
        var sumX: Long = 0
        var sumY: Long = 0
        var minX: Int = Int.MAX_VALUE
        var minY: Int = Int.MAX_VALUE
        var maxX: Int = Int.MIN_VALUE
        var maxY: Int = Int.MIN_VALUE

        fun add(x: Int, y: Int) {
            count += 1
            sumX += x.toLong()
            sumY += y.toLong()
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
        }
    }

    companion object {
        private const val ANALYSIS_INTERVAL_MS = 900L
        private const val MIN_COLOR_POINTS = 28
        private const val MIN_SCREEN_WIDTH_RATIO = 0.10f
        private const val MIN_SCREEN_HEIGHT_RATIO = 0.10f
        private const val MIN_SCREEN_COLOR_DENSITY = 0.35f
        private const val MIN_SCREEN_ASPECT_RATIO = 0.35f
        private const val MAX_SCREEN_ASPECT_RATIO = 3.2f
        private val GLASS_COLOR_BUCKETS = listOf(
            "#ef4444",
            "#3b82f6",
            "#22c55e",
            "#eab308",
            "#8b5cf6",
            "#ec4899",
            "#06b6d4"
        ).map { hex ->
            val color = android.graphics.Color.parseColor(hex)
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color),
                hsv
            )
            ColorBucket(hex = hex, hue = hsv[0], saturation = hsv[1], value = hsv[2])
        }
    }

    private data class ColorBucket(
        val hex: String,
        val hue: Float,
        val saturation: Float,
        val value: Float
    )

    override fun close() {
        yoloDetector.close()
    }
}

private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return this
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    recycle()
    return rotated
}

private fun ImageProxy.toBitmapOrNull(): android.graphics.Bitmap? {
    if (format != ImageFormat.YUV_420_888) return null
    val nv21 = yuv420888ToNv21()
    val image = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    image.compressToJpeg(Rect(0, 0, width, height), 70, out)
    return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}

private fun ImageProxy.yuv420888ToNv21(): ByteArray {
    val imageWidth = width
    val imageHeight = height
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]
    val nv21 = ByteArray(imageWidth * imageHeight * 3 / 2)

    var outputIndex = 0
    for (row in 0 until imageHeight) {
        val rowStart = row * yPlane.rowStride
        for (col in 0 until imageWidth) {
            nv21[outputIndex++] = yPlane.buffer.get(rowStart + col * yPlane.pixelStride)
        }
    }

    val chromaHeight = imageHeight / 2
    val chromaWidth = imageWidth / 2
    for (row in 0 until chromaHeight) {
        val uRowStart = row * uPlane.rowStride
        val vRowStart = row * vPlane.rowStride
        for (col in 0 until chromaWidth) {
            nv21[outputIndex++] = vPlane.buffer.get(vRowStart + col * vPlane.pixelStride)
            nv21[outputIndex++] = uPlane.buffer.get(uRowStart + col * uPlane.pixelStride)
        }
    }
    return nv21
}
