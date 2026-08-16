package com.whatschat.app.data.filter

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.sqrt

/**
 * Distorts a circular region of a photo around a detected landmark to make
 * it locally bigger (bulge) or smaller (pinch) — e.g. "big nose"/"small
 * nose". Unlike [FaceFilterCompositor]'s emoji overlays this actually
 * reshapes pixels, which is too slow to redo on every camera frame, so
 * [SelfieFilterScreen] only applies it once to the final captured photo
 * rather than live in the viewfinder.
 */
object FaceWarpCompositor {

    fun apply(source: Bitmap, face: Face, filter: FaceFilter): Bitmap {
        val target = filter.warpTarget ?: return source
        val box = face.boundingBox
        return when (target) {
            WarpTarget.NOSE -> {
                val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
                val cx = nose?.x ?: box.exactCenterX()
                val cy = nose?.y ?: (box.top + box.height() * 0.55f)
                warp(source, cx, cy, box.width() * 0.32f, filter.warpStrength)
            }
            WarpTarget.EYES -> {
                val radius = box.width() * 0.22f
                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                var result = source
                if (leftEye != null) result = warp(result, leftEye.x, leftEye.y, radius, filter.warpStrength)
                if (rightEye != null) result = warp(result, rightEye.x, rightEye.y, radius, filter.warpStrength)
                result
            }
        }
    }

    /**
     * Backward-mapped radial warp: for every output pixel within [radius] of
     * ([centerX], [centerY]), samples the source from a point pulled toward
     * ([strength] > 0, bulge/magnify) or pushed away from ([strength] < 0,
     * pinch/shrink) the center, blending smoothly back to unwarped at the
     * radius edge so there's no visible seam.
     */
    private fun warp(source: Bitmap, centerX: Float, centerY: Float, radius: Float, strength: Float): Bitmap {
        if (radius <= 0f) return source
        val minX = (centerX - radius).toInt().coerceIn(0, source.width - 1)
        val maxX = (centerX + radius).toInt().coerceIn(0, source.width - 1)
        val minY = (centerY - radius).toInt().coerceIn(0, source.height - 1)
        val maxY = (centerY + radius).toInt().coerceIn(0, source.height - 1)
        if (minX >= maxX || minY >= maxY) return source

        val regionWidth = maxX - minX + 1
        val regionHeight = maxY - minY + 1
        val srcPixels = IntArray(regionWidth * regionHeight)
        source.getPixels(srcPixels, 0, regionWidth, minX, minY, regionWidth, regionHeight)
        val outPixels = srcPixels.copyOf()

        for (y in 0 until regionHeight) {
            val py = minY + y
            val dy = py - centerY
            for (x in 0 until regionWidth) {
                val px = minX + x
                val dx = px - centerX
                val dist = sqrt(dx * dx + dy * dy)
                if (dist >= radius) continue
                val percent = 1f - dist / radius
                val factor = 1f - strength * percent * percent
                val sampleX = (centerX + dx * factor).toInt().coerceIn(minX, maxX) - minX
                val sampleY = (centerY + dy * factor).toInt().coerceIn(minY, maxY) - minY
                outPixels[y * regionWidth + x] = srcPixels[sampleY * regionWidth + sampleX]
            }
        }

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(outPixels, 0, regionWidth, minX, minY, regionWidth, regionHeight)
        return result
    }
}
