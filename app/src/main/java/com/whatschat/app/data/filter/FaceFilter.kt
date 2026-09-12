package com.whatschat.app.data.filter

/**
 * Selfie filter presets. Most are drawn as emoji positioned on the face's
 * actual detected landmarks (see [FaceFilterCompositor]) rather than a fixed
 * on-screen spot — no custom illustrated artwork was available to build this
 * with, so an emoji glyph stands in for it. [anchor] decides which
 * landmark(s) an [FilterKind.EMOJI_OVERLAY] filter is centered on.
 *
 * [FilterKind.WARP] filters instead reshape the photo's own pixels around a
 * landmark (see [FaceWarpCompositor]) — [warpTarget] picks the landmark(s)
 * and [warpStrength] how much: positive bulges that area bigger, negative
 * pinches it smaller. Their [emoji] is only used as the picker icon, never
 * drawn onto the photo.
 */
enum class FaceFilter(
    val label: String,
    val emoji: String,
    val anchor: FilterAnchor,
    val kind: FilterKind = FilterKind.EMOJI_OVERLAY,
    val warpTarget: WarpTarget? = null,
    val warpStrength: Float = 0f
) {
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
    SANTA("Santa", "🎅", FilterAnchor.TOP),
    BIG_NOSE("Big Nose", "👃", FilterAnchor.NOSE, FilterKind.WARP, WarpTarget.NOSE, 0.9f),
    SMALL_NOSE("Small Nose", "👃", FilterAnchor.NOSE, FilterKind.WARP, WarpTarget.NOSE, -0.6f),
    BIG_EYES("Big Eyes", "👀", FilterAnchor.EYES, FilterKind.WARP, WarpTarget.EYES, 0.7f),
    SMALL_EYES("Small Eyes", "👀", FilterAnchor.EYES, FilterKind.WARP, WarpTarget.EYES, -0.5f)
}

/** Which detected face feature a [FaceFilter] is centered/scaled on. */
enum class FilterAnchor { FACE, EYES, NOSE, TOP }

/** Whether a [FaceFilter] overlays an emoji glyph, or warps the photo's pixels around a landmark. */
enum class FilterKind { EMOJI_OVERLAY, WARP }

/** Which landmark(s) a [FilterKind.WARP] filter distorts. */
enum class WarpTarget { NOSE, EYES }
