package com.whatschat.app.data.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Produces a "translated photo": each recognized text block's spot in the
 * original image is painted over, then the translated text is drawn back in
 * its place — the same idea as pointing a phone's camera translate mode at a
 * menu or sign, done fully on-device from ML Kit's bounding boxes.
 */
object TranslatedImageComposer {
    fun compose(source: Bitmap, blocks: List<Pair<Rect, String>>): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val coverPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        blocks.forEach { (box, translated) ->
            canvas.drawRect(box, coverPaint)
            drawFittedText(canvas, textPaint, translated, box)
        }
        return output
    }

    /** Shrinks the text size until the wrapped translation fits inside the box, down to a readable floor. */
    private fun drawFittedText(canvas: Canvas, paint: Paint, text: String, box: Rect) {
        val minTextSize = 10f
        var textSize = box.height().toFloat().coerceAtLeast(minTextSize)
        var lines = listOf(text)
        while (textSize > minTextSize) {
            paint.textSize = textSize
            lines = wrapText(text, paint, box.width().toFloat())
            if (lines.size * paint.fontSpacing <= box.height()) break
            textSize -= 2f
        }
        paint.textSize = textSize.coerceAtLeast(minTextSize)

        var y = box.top + paint.fontSpacing - paint.descent()
        for (line in lines) {
            if (y > box.bottom) break
            canvas.drawText(line, box.left.toFloat(), y, paint)
            y += paint.fontSpacing
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isEmpty() || paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
