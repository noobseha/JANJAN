package com.gachon.janjan.domain.camera.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.gachon.janjan.domain.camera.model.CvDetection
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class YoloOnnxDetector(
    private val context: Context,
    private val cameraId: String,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
) : Closeable {
    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var createdSession: OrtSession? = null
    private val session: OrtSession by lazy { createSession().also { createdSession = it } }
    private val labels: List<String> by lazy { loadLabels() }
    private val tracks = mutableListOf<TrackedObject>()
    private var frameIndex: Long = 0L
    private var nextTrackId: Int = 1

    fun detect(bitmap: Bitmap): List<CvDetection> {
        frameIndex += 1
        val input = preprocess(bitmap)
        val tensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input.values),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        )

        tensor.use {
            session.run(mapOf(session.inputNames.first() to tensor)).use { results ->
                val candidates = extractPredictions(results[0].value).mapNotNull { prediction ->
                    prediction.toDetectionCandidate(
                        originalWidth = bitmap.width,
                        originalHeight = bitmap.height,
                        scale = input.scale,
                        padX = input.padX,
                        padY = input.padY
                    )
                }
                return assignTrackIds(nonMaxSuppression(candidates))
            }
        }
    }

    private fun createSession(): OrtSession {
        val modelBytes = context.assets.open(MODEL_ASSET_NAME).use { it.readBytes() }
        return environment.createSession(modelBytes, OrtSession.SessionOptions())
    }

    private fun loadLabels(): List<String> =
        context.assets.open(LABELS_ASSET_NAME).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotBlank() }.toList()
        }

    private fun preprocess(bitmap: Bitmap): LetterboxInput {
        val scale = min(
            INPUT_SIZE / bitmap.width.toFloat(),
            INPUT_SIZE / bitmap.height.toFloat()
        )
        val resizedWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val resizedHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val padX = ((INPUT_SIZE - resizedWidth) / 2f)
        val padY = ((INPUT_SIZE - resizedHeight) / 2f)
        val scaled = Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, true)
        val pixels = IntArray(resizedWidth * resizedHeight)
        scaled.getPixels(pixels, 0, resizedWidth, 0, 0, resizedWidth, resizedHeight)
        if (scaled !== bitmap) {
            scaled.recycle()
        }

        val planeSize = INPUT_SIZE * INPUT_SIZE
        val input = FloatArray(planeSize * 3) { LETTERBOX_FILL }
        for (y in 0 until resizedHeight) {
            for (x in 0 until resizedWidth) {
                val color = pixels[y * resizedWidth + x]
                val targetX = x + padX.roundToInt()
                val targetY = y + padY.roundToInt()
                val target = targetY * INPUT_SIZE + targetX
                input[target] = Color.red(color) / 255f
                input[planeSize + target] = Color.green(color) / 255f
                input[planeSize * 2 + target] = Color.blue(color) / 255f
            }
        }

        return LetterboxInput(
            values = input,
            scale = scale,
            padX = padX,
            padY = padY
        )
    }

    private fun RawPrediction.toDetectionCandidate(
        originalWidth: Int,
        originalHeight: Int,
        scale: Float,
        padX: Float,
        padY: Float
    ): DetectionCandidate? {
        if (confidence < confidenceThreshold) return null

        val modelLabel = labels.getOrNull(classId) ?: return null
        val mapped = modelLabel.toMappedLabel() ?: return null

        val maxCoord = maxOf(first, second, third, fourth)
        val coordScale = if (maxCoord <= 1.5f) INPUT_SIZE.toFloat() else 1f
        val rawBox = if (boxFormat == BoxFormat.XYWH) {
            val centerX = first * coordScale
            val centerY = second * coordScale
            val boxWidth = third * coordScale
            val boxHeight = fourth * coordScale
            RawBox(
                x1 = centerX - boxWidth / 2f,
                y1 = centerY - boxHeight / 2f,
                x2 = centerX + boxWidth / 2f,
                y2 = centerY + boxHeight / 2f
            )
        } else {
            RawBox(
                x1 = first * coordScale,
                y1 = second * coordScale,
                x2 = third * coordScale,
                y2 = fourth * coordScale
            )
        }
        val x1 = ((rawBox.x1 - padX) / scale).coerceIn(0f, originalWidth.toFloat())
        val y1 = ((rawBox.y1 - padY) / scale).coerceIn(0f, originalHeight.toFloat())
        val x2 = ((rawBox.x2 - padX) / scale).coerceIn(0f, originalWidth.toFloat())
        val y2 = ((rawBox.y2 - padY) / scale).coerceIn(0f, originalHeight.toFloat())

        if (x2 - x1 < MIN_BOX_SIZE_PX || y2 - y1 < MIN_BOX_SIZE_PX) return null

        return DetectionCandidate(
            objectType = mapped.objectType,
            drinkType = mapped.drinkType,
            centerX = ((x1 + x2) / 2f / originalWidth).coerceIn(0f, 1f),
            centerY = ((y1 + y2) / 2f / originalHeight).coerceIn(0f, 1f),
            x = (x1 / originalWidth).coerceIn(0f, 1f),
            y = (y1 / originalHeight).coerceIn(0f, 1f),
            width = ((x2 - x1) / originalWidth).coerceIn(0f, 1f),
            height = ((y2 - y1) / originalHeight).coerceIn(0f, 1f),
            confidence = confidence.coerceIn(0f, 1f)
        )
    }

    private fun extractPredictions(output: Any): List<RawPrediction> =
        extractMatrices(output).flatMap { matrix ->
            val normalizedRows = matrix.toPredictionRows()
            normalizedRows.mapNotNull { it.toRawPrediction() }
        }

    private fun extractMatrices(output: Any): List<Array<FloatArray>> {
        if (output !is Array<*>) return emptyList()
        val first = output.firstOrNull()
        return when (first) {
            is Array<*> -> listOf(first.filterIsInstance<FloatArray>().toTypedArray())
            is FloatArray -> listOf(output.filterIsInstance<FloatArray>().toTypedArray())
            else -> emptyList()
        }
    }

    private fun Array<FloatArray>.toPredictionRows(): List<FloatArray> {
        if (isEmpty()) return emptyList()
        val rowSize = first().size
        val attributeCount = size
        val looksTransposed = attributeCount in MIN_ATTRIBUTE_COUNT..MAX_ATTRIBUTE_COUNT &&
            rowSize > attributeCount &&
            rowSize > MIN_TRANSPOSED_PREDICTION_COUNT
        if (!looksTransposed) return toList()

        return List(rowSize) { predictionIndex ->
            FloatArray(attributeCount) { attributeIndex ->
                this[attributeIndex][predictionIndex]
            }
        }
    }

    private fun FloatArray.toRawPrediction(): RawPrediction? {
        val postProcessedClassId = getOrNull(5)?.roundToInt()
        if (size == POST_PROCESSED_ATTRIBUTE_COUNT &&
            postProcessedClassId != null &&
            postProcessedClassId in labels.indices
        ) {
            return RawPrediction(
                first = this[0],
                second = this[1],
                third = this[2],
                fourth = this[3],
                confidence = this[4].coerceIn(0f, 1f),
                classId = postProcessedClassId,
                boxFormat = BoxFormat.XYXY
            )
        }

        val classStartIndex = when {
            size == BOX_ATTRIBUTE_COUNT + labels.size -> BOX_ATTRIBUTE_COUNT
            size >= BOX_ATTRIBUTE_COUNT + OBJECTNESS_ATTRIBUTE_COUNT + labels.size ->
                BOX_ATTRIBUTE_COUNT + OBJECTNESS_ATTRIBUTE_COUNT
            else -> return null
        }
        val objectness = if (classStartIndex == BOX_ATTRIBUTE_COUNT) {
            1f
        } else {
            this[BOX_ATTRIBUTE_COUNT].coerceIn(0f, 1f)
        }
        val classScores = labels.indices.map { classId ->
            classId to this[classStartIndex + classId]
        }
        val (classId, classScore) = classScores.maxByOrNull { it.second } ?: return null
        return RawPrediction(
            first = this[0],
            second = this[1],
            third = this[2],
            fourth = this[3],
            confidence = (objectness * classScore).coerceIn(0f, 1f),
            classId = classId,
            boxFormat = BoxFormat.XYWH
        )
    }

    private fun nonMaxSuppression(candidates: List<DetectionCandidate>): List<DetectionCandidate> {
        val selected = mutableListOf<DetectionCandidate>()
        candidates.sortedByDescending { it.confidence }.forEach { candidate ->
            val overlapsSameObject = selected.any { existing ->
                existing.objectType == candidate.objectType &&
                    existing.iou(candidate) >= NMS_IOU_THRESHOLD
            }
            if (!overlapsSameObject) {
                selected += candidate
            }
            if (selected.size >= MAX_DETECTIONS_PER_FRAME) return@forEach
        }
        return selected
    }

    private fun DetectionCandidate.iou(other: DetectionCandidate): Float {
        val left = maxOf(x, other.x)
        val top = maxOf(y, other.y)
        val right = minOf(x + width, other.x + other.width)
        val bottom = minOf(y + height, other.y + other.height)
        val intersection = ((right - left).coerceAtLeast(0f)) * ((bottom - top).coerceAtLeast(0f))
        val union = width * height + other.width * other.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private fun assignTrackIds(candidates: List<DetectionCandidate>): List<CvDetection> {
        val activeTracks = tracks.filter { frameIndex - it.lastSeenFrame <= TRACK_TTL_FRAMES }
            .toMutableList()
        tracks.clear()
        tracks.addAll(activeTracks)

        val usedTrackIds = mutableSetOf<String>()
        return candidates.sortedByDescending { it.confidence }.map { candidate ->
            val track = findBestTrack(candidate, usedTrackIds) ?: createTrack(candidate)
            usedTrackIds += track.id
            track.centerX = candidate.centerX
            track.centerY = candidate.centerY
            track.confidence = candidate.confidence
            track.lastSeenFrame = frameIndex

            CvDetection(
                trackId = track.id,
                objectType = candidate.objectType,
                centerX = candidate.centerX,
                centerY = candidate.centerY,
                x = candidate.x,
                y = candidate.y,
                width = candidate.width,
                height = candidate.height,
                confidence = candidate.confidence,
                physicalGlassId = candidate.drinkType?.let { "${cameraId}_${track.id}" },
                drinkType = candidate.drinkType
            )
        }
    }

    private fun findBestTrack(
        candidate: DetectionCandidate,
        usedTrackIds: Set<String>
    ): TrackedObject? {
        return tracks
            .filter { it.objectType == candidate.objectType && it.id !in usedTrackIds }
            .map { track ->
                track to hypot(
                    (track.centerX - candidate.centerX).toDouble(),
                    (track.centerY - candidate.centerY).toDouble()
                )
            }
            .filter { (_, distance) -> distance <= TRACK_MATCH_DISTANCE }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    private fun createTrack(candidate: DetectionCandidate): TrackedObject {
        val id = "${candidate.objectType}_${nextTrackId++}"
        return TrackedObject(
            id = id,
            objectType = candidate.objectType,
            centerX = candidate.centerX,
            centerY = candidate.centerY,
            confidence = candidate.confidence,
            lastSeenFrame = frameIndex
        ).also { tracks += it }
    }

    private fun String.toMappedLabel(): MappedLabel? {
        val compact = lowercase().replace("_", "").replace("-", "").replace(" ", "")
        return when (compact) {
            "beer" -> MappedLabel("beer_bottle", null)
            "beerglass", "beercup" -> MappedLabel("beer_glass", "beer")
            "soju" -> MappedLabel("soju_bottle", null)
            "sojuglass", "shotglass", "soglass" -> MappedLabel("soju_glass", "soju")
            else -> null
        }
    }

    override fun close() {
        createdSession?.close()
        createdSession = null
    }

    private data class LetterboxInput(
        val values: FloatArray,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    private data class MappedLabel(
        val objectType: String,
        val drinkType: String?
    )

    private data class RawPrediction(
        val first: Float,
        val second: Float,
        val third: Float,
        val fourth: Float,
        val confidence: Float,
        val classId: Int,
        val boxFormat: BoxFormat
    )

    private data class RawBox(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    )

    private enum class BoxFormat {
        XYXY,
        XYWH
    }

    private data class DetectionCandidate(
        val objectType: String,
        val drinkType: String?,
        val centerX: Float,
        val centerY: Float,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val confidence: Float
    )

    private data class TrackedObject(
        val id: String,
        val objectType: String,
        var centerX: Float,
        var centerY: Float,
        var confidence: Float,
        var lastSeenFrame: Long
    )

    companion object {
        private const val MODEL_ASSET_NAME = "best_baby.onnx"
        private const val LABELS_ASSET_NAME = "best_baby_labels.txt"
        private const val INPUT_SIZE = 640
        private const val LETTERBOX_FILL = 114f / 255f
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.35f
        private const val MIN_BOX_SIZE_PX = 8f
        private const val BOX_ATTRIBUTE_COUNT = 4
        private const val OBJECTNESS_ATTRIBUTE_COUNT = 1
        private const val POST_PROCESSED_ATTRIBUTE_COUNT = 6
        private const val MIN_ATTRIBUTE_COUNT = 6
        private const val MAX_ATTRIBUTE_COUNT = 128
        private const val MIN_TRANSPOSED_PREDICTION_COUNT = 64
        private const val NMS_IOU_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS_PER_FRAME = 24
        private const val TRACK_MATCH_DISTANCE = 0.14
        private const val TRACK_TTL_FRAMES = 8L
    }
}
