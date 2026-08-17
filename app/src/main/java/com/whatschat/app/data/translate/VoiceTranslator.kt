package com.whatschat.app.data.translate

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Translates a typed message into [AppLanguage] and speaks it to a WAV file,
 * entirely on-device: ML Kit for language detection/translation (downloads a
 * small model per language pair on first use, then works offline), Android's
 * built-in TextToSpeech engine for speech synthesis. No network calls beyond
 * ML Kit's one-time model download, no external API/key.
 */
class VoiceTranslator(private val context: Context) {

    companion object {
        private const val SPEECH_RATE = 0.8f
    }

    /** Detects the source language, then translates [text] into [target]. */
    suspend fun translate(text: String, target: AppLanguage): String {
        val sourceCode = identifyLanguage(text)
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceCode)
            .setTargetLanguage(target.mlKitCode)
            .build()
        val translator = Translation.getClient(options)
        return try {
            downloadModel(translator)
            translateText(translator, text)
        } finally {
            translator.close()
        }
    }

    /**
     * Downloads [language]'s ML Kit translation model ahead of time (paired
     * with a common pivot language) so translating to/from it later works
     * without a data connection — useful to run once before a trip while
     * still on wifi. `translate()` already downloads models on demand, so
     * this is purely an optional head start, not a requirement.
     */
    suspend fun downloadForOffline(language: AppLanguage) {
        val pivot = if (language == AppLanguage.ENGLISH) TranslateLanguage.FRENCH else TranslateLanguage.ENGLISH
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(pivot)
            .setTargetLanguage(language.mlKitCode)
            .build()
        val translator = Translation.getClient(options)
        try {
            downloadModel(translator)
        } finally {
            translator.close()
        }
    }

    /** Synthesizes [text] as speech in [locale] and writes it to [outputFile] (WAV). */
    suspend fun speakToFile(text: String, locale: Locale, outputFile: File): Unit =
        withReadyEngine(locale) { engine, utteranceId ->
            engine.synthesizeToFile(text, Bundle(), outputFile, utteranceId)
        }

    /**
     * Reads [text] aloud through the speaker immediately (no file involved) —
     * used for "listen to this message". If [locale] isn't given, the
     * message's own language is auto-detected and used, so it's read in
     * whatever language it was actually written in rather than [AppLanguage]'s
     * currently selected target.
     */
    suspend fun speakNow(text: String, locale: Locale? = null) {
        val actualLocale = locale ?: run {
            val code = identifyLanguage(text)
            AppLanguage.entries.firstOrNull { it.mlKitCode == code }?.ttsLocale ?: Locale.forLanguageTag(code)
        }
        withReadyEngine(actualLocale) { engine, utteranceId ->
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        }
    }

    /** Boots a [TextToSpeech] engine, sets [locale], then runs [synthesize] (either speakNow or synthesizeToFile), shutting the engine down once it's done. */
    private suspend fun withReadyEngine(locale: Locale, synthesize: (TextToSpeech, String) -> Int): Unit =
        suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    cont.resumeWithException(IllegalStateException("Text-to-speech engine unavailable"))
                    return@TextToSpeech
                }

                val languageResult = engine.setLanguage(locale)
                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.shutdown()
                    cont.resumeWithException(
                        IllegalStateException("This language's voice isn't installed on this device")
                    )
                    return@TextToSpeech
                }

                // A bit slower than the device default so a phrase is easier to
                // catch on first listen, especially in an unfamiliar language.
                engine.setSpeechRate(SPEECH_RATE)

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        engine.shutdown()
                        if (cont.isActive) cont.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java", ReplaceWith(""))
                    override fun onError(utteranceId: String?) {
                        engine.shutdown()
                        if (cont.isActive) {
                            cont.resumeWithException(IllegalStateException("Speech synthesis failed"))
                        }
                    }
                })

                val utteranceId = "whatschat_${System.currentTimeMillis()}"
                if (synthesize(engine, utteranceId) != TextToSpeech.SUCCESS) {
                    engine.shutdown()
                    cont.resumeWithException(IllegalStateException("Could not start speech synthesis"))
                }
            }

            cont.invokeOnCancellation { tts?.shutdown() }
        }

    private suspend fun identifyLanguage(text: String): String {
        val identifier = LanguageIdentification.getClient()
        val code = suspendCancellableCoroutine<String> { cont ->
            identifier.identifyLanguage(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume("und") }
        }
        return if (code == "und") Locale.getDefault().language else code
    }

    private suspend fun downloadModel(translator: com.google.mlkit.nl.translate.Translator) =
        suspendCancellableCoroutine<Unit> { cont ->
            translator.downloadModelIfNeeded()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private suspend fun translateText(translator: com.google.mlkit.nl.translate.Translator, text: String) =
        suspendCancellableCoroutine<String> { cont ->
            translator.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
