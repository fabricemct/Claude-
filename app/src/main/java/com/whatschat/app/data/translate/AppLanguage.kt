package com.whatschat.app.data.translate

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/** Target languages offered for translation (typed/spoken text, and per-message translate). */
enum class AppLanguage(val label: String, val flag: String, val mlKitCode: String, val ttsLocale: Locale) {
    ENGLISH("English", "🇬🇧", TranslateLanguage.ENGLISH, Locale.ENGLISH),
    FRENCH("French", "🇫🇷", TranslateLanguage.FRENCH, Locale.FRENCH),
    PORTUGUESE("Portuguese", "🇵🇹", TranslateLanguage.PORTUGUESE, Locale("pt")),
    GERMAN("German", "🇩🇪", TranslateLanguage.GERMAN, Locale.GERMAN),
    SPANISH("Spanish", "🇪🇸", TranslateLanguage.SPANISH, Locale("es")),
    ITALIAN("Italian", "🇮🇹", TranslateLanguage.ITALIAN, Locale.ITALIAN),
    ARABIC("Arabic", "🇸🇦", TranslateLanguage.ARABIC, Locale("ar")),
    CHINESE("Chinese", "🇨🇳", TranslateLanguage.CHINESE, Locale.SIMPLIFIED_CHINESE),
    JAPANESE("Japanese", "🇯🇵", TranslateLanguage.JAPANESE, Locale.JAPANESE),
    KOREAN("Korean", "🇰🇷", TranslateLanguage.KOREAN, Locale.KOREAN),
    RUSSIAN("Russian", "🇷🇺", TranslateLanguage.RUSSIAN, Locale("ru")),
    DUTCH("Dutch", "🇳🇱", TranslateLanguage.DUTCH, Locale("nl")),
    TURKISH("Turkish", "🇹🇷", TranslateLanguage.TURKISH, Locale("tr")),
    POLISH("Polish", "🇵🇱", TranslateLanguage.POLISH, Locale("pl")),
    HINDI("Hindi", "🇮🇳", TranslateLanguage.HINDI, Locale("hi")),
    VIETNAMESE("Vietnamese", "🇻🇳", TranslateLanguage.VIETNAMESE, Locale("vi")),
    THAI("Thai", "🇹🇭", TranslateLanguage.THAI, Locale("th")),
    SWEDISH("Swedish", "🇸🇪", TranslateLanguage.SWEDISH, Locale("sv")),
    GREEK("Greek", "🇬🇷", TranslateLanguage.GREEK, Locale("el")),
    UKRAINIAN("Ukrainian", "🇺🇦", TranslateLanguage.UKRAINIAN, Locale("uk")),
    HEBREW("Hebrew", "🇮🇱", TranslateLanguage.HEBREW, Locale("he")),
    INDONESIAN("Indonesian", "🇮🇩", TranslateLanguage.INDONESIAN, Locale("id")),
    ROMANIAN("Romanian", "🇷🇴", TranslateLanguage.ROMANIAN, Locale("ro")),
    CZECH("Czech", "🇨🇿", TranslateLanguage.CZECH, Locale("cs")),
    DANISH("Danish", "🇩🇰", TranslateLanguage.DANISH, Locale("da")),
    NORWEGIAN("Norwegian", "🇳🇴", TranslateLanguage.NORWEGIAN, Locale("no")),
    HUNGARIAN("Hungarian", "🇭🇺", TranslateLanguage.HUNGARIAN, Locale("hu")),
    FINNISH("Finnish", "🇫🇮", TranslateLanguage.FINNISH, Locale("fi")),
    PERSIAN("Persian", "🇮🇷", TranslateLanguage.PERSIAN, Locale("fa")),
    URDU("Urdu", "🇵🇰", TranslateLanguage.URDU, Locale("ur")),
    TAGALOG("Filipino", "🇵🇭", TranslateLanguage.TAGALOG, Locale("tl"))
}
