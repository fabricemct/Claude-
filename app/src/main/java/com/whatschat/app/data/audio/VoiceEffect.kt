package com.whatschat.app.data.audio

/**
 * "Funny voice" presets applied with zero DSP: the WAV header simply claims
 * a different sample rate than the audio was actually recorded at, so
 * playback speeds up or slows down (and the pitch shifts along with it).
 */
enum class VoiceEffect(val label: String, val emoji: String, val rateMultiplier: Double) {
    NORMAL("Normal", "🎤", 1.0),
    CHIPMUNK("Chipmunk", "🐿️", 1.6),
    DEEP("Deep voice", "🎙️", 0.7)
}
