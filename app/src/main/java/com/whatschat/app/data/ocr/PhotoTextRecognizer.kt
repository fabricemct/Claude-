package com.whatschat.app.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extracts text from a photo (a menu, a sign, a document) entirely
 * on-device via ML Kit's Latin-script text recognizer — free, no network
 * call, works offline once the app is installed. Latin script covers most
 * European languages; scripts like Chinese/Japanese/Korean/Devanagari would
 * need ML Kit's separate script-specific recognizers, not included here.
 */
object PhotoTextRecognizer {
    suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }
}
