package com.whatschat.app.data.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        val box = face.boundingBox

        when (filter) {
            FaceFilter.DOG -> {
                paint.textSize = box.width() * 1.3f
                canvas.drawText(filter.emoji, box.exactCenterX(), box.top + box.height() * 0.4f, paint)
            }
            FaceFilter.GLASSES -> {
                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                val centerX = if (leftEye != null && rightEye != null) (leftEye.x + rightEye.x) / 2f else box.exactCenterX()
                val centerY = if (leftEye != null && rightEye != null) {
                    (leftEye.y + rightEye.y) / 2f
                } else {
                    box.top + box.height() * 0.4f
                }
                paint.textSize = box.width() * 0.9f
                canvas.drawText(filter.emoji, centerX, centerY + paint.textSize * 0.3f, paint)
            }
            FaceFilter.DISGUISE -> {
                val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
                val centerX = nose?.x ?: box.exactCenterX()
                val centerY = nose?.y ?: (box.top + box.height() * 0.55f)
                paint.textSize = box.width() * 1.0f
                canvas.drawText(filter.emoji, centerX, centerY + paint.textSize * 0.3f, paint)
            }
            FaceFilter.CROWN -> {
                paint.textSize = box.width() * 1.0f
                canvas.drawText(filter.emoji, box.exactCenterX(), box.top.toFloat(), paint)
            }
            FaceFilter.NONE -> Unit
        }
        return result
    }
}
