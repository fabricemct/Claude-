package com.whatschat.app.data.translate

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/** Target languages offered for "translate & speak". */
enum class AppLanguage(val label: String, val flag: String, val mlKitCode: String, val ttsLocale: Locale) {
    ENGLISH("English", "🇬🇧", TranslateLanguage.ENGLISH, Locale.ENGLISH),
    FRENCH("French", "🇫🇷", TranslateLanguage.FRENCH, Locale.FRENCH),
    PORTUGUESE("Portuguese", "🇵🇹", TranslateLanguage.PORTUGUESE, Locale("pt")),
    GERMAN("German", "🇩🇪", TranslateLanguage.GERMAN, Locale.GERMAN),
    SPANISH("Spanish", "🇪🇸", TranslateLanguage.SPANISH, Locale("es")),
    ITALIAN("Italian", "🇮🇹", TranslateLanguage.ITALIAN, Locale.ITALIAN)
}
