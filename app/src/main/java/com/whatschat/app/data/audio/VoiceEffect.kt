package com.whatschat.app.data.audio

import kotlin.math.sin

/**
 * Generic "funny voice" presets — no real person is modeled or imitated,
 * these are just playback-rate curves applied to the recorded audio.
 * [rateAt] returns the local playback-rate multiplier at [elapsedSeconds]
 * into the clip: 1.0 is unchanged, >1 speeds up/raises pitch, <1 slows
 * down/lowers pitch. A curve that varies over time (see [LAUGHING])
 * creates a wobble instead of a flat pitch shift.
 */
enum class VoiceEffect(val label: String, val emoji: String) {
    NORMAL("Normal", "🎤") {
        override fun rateAt(elapsedSeconds: Double) = 1.0
    },
    WOMAN("Woman", "👩") {
        override fun rateAt(elapsedSeconds: Double) = 1.25
    },
    MAN("Man", "🧔") {
        override fun rateAt(elapsedSeconds: Double) = 0.85
    },
    BABY("Baby", "👶") {
        override fun rateAt(elapsedSeconds: Double) = 1.6
    },
    GIANT("Giant", "🧌") {
        override fun rateAt(elapsedSeconds: Double) = 0.6
    },
    ROBOT("Robot", "🤖") {
        override fun rateAt(elapsedSeconds: Double) = 0.75
    },
    ALIEN("Alien", "👽") {
        override fun rateAt(elapsedSeconds: Double) = 1.8
    },
    TIRED("Tired", "😴") {
        override fun rateAt(elapsedSeconds: Double) = 0.8
    },
    LAUGHING("Laughing", "😂") {
        override fun rateAt(elapsedSeconds: Double) = 1.0 + 0.35 * sin(2 * Math.PI * elapsedSeconds * 6.0)
    };

    abstract fun rateAt(elapsedSeconds: Double): Double
}
