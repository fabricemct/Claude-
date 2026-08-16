package com.whatschat.app.data.filter

/**
 * Selfie filter presets, drawn as emoji positioned on the face's actual
 * detected landmarks (see [FaceFilterCompositor]) rather than a fixed
 * on-screen spot — no custom illustrated artwork was available to build
 * this with, so an emoji glyph stands in for it. [anchor] decides which
 * landmark(s) the filter is centered on.
 */
enum class FaceFilter(val label: String, val emoji: String, val anchor: FilterAnchor) {
    NONE("None", "🚫", FilterAnchor.FACE),
    DOG("Dog", "🐶", FilterAnchor.FACE),
    CAT("Cat", "🐱", FilterAnchor.FACE),
    CLOWN("Clown", "🤡", FilterAnchor.FACE),
    ALIEN("Alien", "👽", FilterAnchor.FACE),
    PARTY("Party", "🥳", FilterAnchor.FACE),
    GLASSES("Glasses", "🕶️", FilterAnchor.EYES),
    HEART_EYES("Heart Eyes", "😍", FilterAnchor.EYES),
    DISGUISE("Disguise", "🥸", FilterAnchor.NOSE),
    CROWN("Crown", "👑", FilterAnchor.TOP),
    SANTA("Santa", "🎅", FilterAnchor.TOP)
}

/** Which detected face feature a [FaceFilter] is centered/scaled on. */
enum class FilterAnchor { FACE, EYES, NOSE, TOP }
