package com.whatschat.app.data.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

/**
 * Draws a [FaceFilter] onto a copy of [source] at the position/scale implied
 * by [face]'s detected bounding box and landmarks. Falls back to bounding-box
 * proportions when a specific landmark wasn't found (can happen at odd
 * angles even with [com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL]).
 */
object FaceFilterCompositor {

    fun apply(source: Bitmap, face: Face, filter: FaceFilter): Bitmap {
        if (filter == FaceFilter.NONE) return source
        if (filter.kind == FilterKind.WARP) return FaceWarpCompositor.apply(source, face, filter)
        val (centerX, centerY, textSize) = placementFor(filter, face, face.boundingBox)
        return drawEmoji(source, filter.emoji, centerX, centerY, textSize)
    }

    /** Draws [filter] at an explicit position (e.g. one the user dragged into place) instead of an auto-detected one. */
    fun applyAt(source: Bitmap, filter: FaceFilter, centerX: Float, centerY: Float, textSize: Float): Bitmap {
        if (filter == FaceFilter.NONE) return source
        return drawEmoji(source, filter.emoji, centerX, centerY, textSize)
    }

    private fun drawEmoji(source: Bitmap, emoji: String, centerX: Float, centerY: Float, textSize: Float): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            this.textSize = textSize
        }
        canvas.drawText(emoji, centerX, centerY + textSize * 0.3f, paint)
        return result
    }

    private fun placementFor(filter: FaceFilter, face: Face, box: Rect): Triple<Float, Float, Float> =
        when (filter.anchor) {
            FilterAnchor.EYES -> {
                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                val center = midpointOrNull(leftEye, rightEye) ?: PointF(box.exactCenterX(), box.top + box.height() * 0.4f)
                Triple(center.x, center.y, box.width() * 0.9f)
            }
            FilterAnchor.NOSE -> {
                val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
                val x = nose?.x ?: box.exactCenterX()
                val y = nose?.y ?: (box.top + box.height() * 0.55f)
                Triple(x, y, box.width() * 1.0f)
            }
            FilterAnchor.TOP -> Triple(box.exactCenterX(), box.top.toFloat(), box.width() * 1.0f)
            FilterAnchor.FACE -> Triple(box.exactCenterX(), box.top + box.height() * 0.4f, box.width() * 1.3f)
        }

    private fun midpointOrNull(a: PointF?, b: PointF?): PointF? =
        if (a != null && b != null) PointF((a.x + b.x) / 2f, (a.y + b.y) / 2f) else null
}
