package com.whatschat.app.data.translate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Transcribes spoken audio to text using Android's built-in speech
 * recognizer (the same engine behind the keyboard's "voice typing" button —
 * on most devices this is Google's, on-device for common languages).
 * [locale] hints which language is being spoken for accuracy; the resulting
 * text still goes through ML Kit's own language detection when it's
 * translated afterwards, so an imprecise hint here isn't fatal. Must be
 * called from the main thread (it's driven by Compose's UI dispatcher here).
 */
class SpeechToText(private val context: Context) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun listen(locale: Locale = Locale.getDefault()): String =
        suspendCancellableCoroutine { cont ->
            if (!isAvailable()) {
                cont.resumeWithException(IllegalStateException("Speech recognition isn't available on this device"))
                return@suspendCancellableCoroutine
            }

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    recognizer.destroy()
                    when {
                        !cont.isActive -> Unit
                        text.isBlank() -> cont.resumeWithException(IllegalStateException("Didn't catch that — try again"))
                        else -> cont.resume(text)
                    }
                }

                override fun onError(error: Int) {
                    recognizer.destroy()
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(errorMessage(error)))
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            recognizer.startListening(intent)
            cont.invokeOnCancellation { recognizer.destroy() }
        }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — try again"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed"
        else -> "Speech recognition failed — try again"
    }
}
