package com.whatschat.app.data.audio

import kotlin.math.sin

/**
 * Generic "funny voice" presets — no real person is modeled or imitated,
 * these are just playback-rate curves applied to the recorded audio.
 * [rateAt] returns the local playback-rate multiplier at [elapsedSeconds]
 * into the clip: 1.0 is unchanged, >1 speeds up/raises pitch, <1 slows
 * down/lowers pitch. A curve that varies over time (see [LAUGHING])
 * creates a wobble instead of a flat pitch shift.
 *
 * [PcmResampler] shifts pitch by resampling, which inherently ties pitch to
 * speed (higher pitch = faster playback) — there's no independent time-stretch
 * here. Rates are kept close to 1.0 so effects stay intelligible instead of
 * turning into a blur of fast-forwarded speech.
 */
enum class VoiceEffect(val label: String, val emoji: String) {
    NORMAL("Normal", "🎤") {
        override fun rateAt(elapsedSeconds: Double) = 1.0
    },
    WOMAN("Woman", "👩") {
        override fun rateAt(elapsedSeconds: Double) = 1.12
    },
    MAN("Man", "🧔") {
        override fun rateAt(elapsedSeconds: Double) = 0.9
    },
    BABY("Baby", "👶") {
        override fun rateAt(elapsedSeconds: Double) = 1.3
    },
    GIANT("Giant", "🧌") {
        override fun rateAt(elapsedSeconds: Double) = 0.75
    },
    ROBOT("Robot", "🤖") {
        override fun rateAt(elapsedSeconds: Double) = 0.85
    },
    ALIEN("Alien", "👽") {
        override fun rateAt(elapsedSeconds: Double) = 1.4
    },
    TIRED("Tired", "😴") {
        override fun rateAt(elapsedSeconds: Double) = 0.88
    },
    LAUGHING("Laughing", "😂") {
        override fun rateAt(elapsedSeconds: Double) = 1.0 + 0.2 * sin(2 * Math.PI * elapsedSeconds * 5.0)
    };

    abstract fun rateAt(elapsedSeconds: Double): Double
}
