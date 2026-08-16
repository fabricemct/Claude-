package com.whatschat.app.data.filter

/**
 * Selfie filter presets, drawn as emoji positioned on the face's actual
 * detected landmarks (see [FaceFilterCompositor]) rather than a fixed
 * on-screen spot — no custom illustrated artwork was available to build
 * this with, so an emoji glyph stands in for it.
 */
enum class FaceFilter(val label: String, val emoji: String) {
    NONE("None", "🚫"),
    DOG("Dog", "🐶"),
    GLASSES("Glasses", "🕶️"),
    DISGUISE("Disguise", "🥸"),
    CROWN("Crown", "👑")
}
