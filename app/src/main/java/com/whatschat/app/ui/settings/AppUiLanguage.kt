package com.whatschat.app.ui.settings

import androidx.annotation.StringRes
import com.whatschat.app.R

/**
 * Languages the app's own interface (menus, buttons, screens) can display
 * in — distinct from [com.whatschat.app.data.translate.AppLanguage], which
 * picks the target language for translating a *message*. [languageTag] is
 * null for "follow the phone's language" (clears the per-app override).
 */
enum class AppUiLanguage(@StringRes val labelRes: Int, val flag: String, val languageTag: String?) {
    SYSTEM_DEFAULT(R.string.settings_language_system, "📱", null),
    ENGLISH(R.string.settings_language_english, "🇬🇧", "en"),
    FRENCH(R.string.settings_language_french, "🇫🇷", "fr")
}
