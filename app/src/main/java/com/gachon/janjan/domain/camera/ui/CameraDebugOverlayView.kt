package com.gachon.janjan.domain.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.gachon.janjan.domain.camera.model.CvDetection
import java.util.Locale
import kotlin.math.max

class CameraDebugOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var detections: List<CvDetection> = emptyList()
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        style = Paint.Style.FILL
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }

    fun setDetections(
        frameWidth: Int,
        frameHeight: Int,
        detections: List<CvDetection>
    ) {
        this.frameWidth = frameWidth
        this.frameHeight = frameHeight
        this.detections = detections
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (frameWidth <= 0 || frameHeight <= 0 || detections.isEmpty()) return

        val scale = max(width / frameWidth.toFloat(), height / frameHeight.toFloat())
        val renderedWidth = frameWidth * scale
        val renderedHeight = frameHeight * scale
        val offsetX = (width - renderedWidth) / 2f
        val offsetY = (height - renderedHeight) / 2f

        detections.forEach { detection ->
            boxPaint.color = detection.colorForObjectType()
            val rect = detection.toViewRect(scale, offsetX, offsetY)
            if (rect != null) {
                canvas.drawRect(rect, boxPaint)
                drawLabel(canvas, detection, rect.left, rect.top)
            } else {
                val cx = offsetX + detection.centerX * frameWidth * scale
                val cy = offsetY + detection.centerY * frameHeight * scale
                canvas.drawCircle(cx, cy, 9f, centerPaint)
                drawLabel(canvas, detection, cx + 12f, cy - 12f)
            }
        }
    }

    private fun CvDetection.toViewRect(
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): RectF? {
        val left = x ?: return null
        val top = y ?: return null
        val boxWidth = width ?: return null
        val boxHeight = height ?: return null
        return RectF(
            offsetX + left * frameWidth * scale,
            offsetY + top * frameHeight * scale,
            offsetX + (left + boxWidth) * frameWidth * scale,
            offsetY + (top + boxHeight) * frameHeight * scale
        )
    }

    private fun drawLabel(
        canvas: Canvas,
        detection: CvDetection,
        rawX: Float,
        rawY: Float
    ) {
        val label = "%s %.0f%%".format(
            Locale.US,
            detection.objectType,
            detection.confidence * 100f
        )
        val textWidth = textPaint.measureText(label)
        val labelX = rawX.coerceIn(4f, (width - textWidth - 12f).coerceAtLeast(4f))
        val labelY = rawY.coerceIn(36f, height - 8f)
        val bg = RectF(labelX - 6f, labelY - 32f, labelX + textWidth + 6f, labelY + 8f)
        canvas.drawRoundRect(bg, 8f, 8f, labelBgPaint)
        canvas.drawText(label, labelX, labelY, textPaint)
    }

    private fun CvDetection.colorForObjectType(): Int =
        when (objectType) {
            "soju_bottle" -> Color.rgb(34, 197, 94)
            "beer_bottle" -> Color.rgb(245, 158, 11)
            "soju_glass" -> Color.rgb(20, 184, 166)
            "beer_glass" -> Color.rgb(59, 130, 246)
            "phone_screen" -> Color.rgb(236, 72, 153)
            else -> Color.WHITE
        }
}
