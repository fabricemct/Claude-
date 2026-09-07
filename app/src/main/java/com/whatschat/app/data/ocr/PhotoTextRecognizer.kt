package com.whatschat.app.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One line of recognized text and where it sits in the source photo. */
data class RecognizedTextBlock(val text: String, val boundingBox: Rect)

/**
 * Extracts text from a photo (a menu, a sign, a document) entirely
 * on-device via ML Kit's Latin-script text recognizer — free, no network
 * call, works offline once the app is installed. Latin script covers most
 * European languages; scripts like Chinese/Japanese/Korean/Devanagari would
 * need ML Kit's separate script-specific recognizers, not included here.
 */
object PhotoTextRecognizer {
    suspend fun recognize(bitmap: Bitmap): String =
        recognizeBlocks(bitmap).joinToString("\n") { it.text }

    /**
     * Same recognition, but keeping each *line's* position so a translation can be drawn
     * back over it. Grouped by line rather than by whole paragraph block: a menu mixes a
     * dish name, its description and its price within one block, and translating/redrawing
     * that as a single blob mangles the layout — one line at a time keeps each row intact.
     */
    suspend fun recognizeBlocks(bitmap: Bitmap): List<RecognizedTextBlock> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    val lines = result.textBlocks
                        .flatMap { it.lines }
                        .mapNotNull { line ->
                            val box = line.boundingBox
                            if (box != null && line.text.isNotBlank()) RecognizedTextBlock(line.text, box) else null
                        }
                    cont.resume(lines)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }
}
