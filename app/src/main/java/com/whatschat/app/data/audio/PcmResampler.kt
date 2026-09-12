package com.whatschat.app.data.audio

/** Applies a [VoiceEffect]'s playback-rate curve to raw 16-bit mono PCM. */
object PcmResampler {

    /**
     * Walks through [pcm] at a variable rate driven by [effect], linearly
     * interpolating between samples. A constant rate changes pitch/speed
     * uniformly; a rate that oscillates over time (e.g. [VoiceEffect.LAUGHING])
     * produces a wobble. The result is real resampled audio — not just a
     * relabeled header — so it plays correctly at [sampleRate] afterwards.
     */
    fun resample(pcm: ByteArray, sampleRate: Int, effect: VoiceEffect): ByteArray {
        val sampleCount = pcm.size / 2
        if (sampleCount < 2) return pcm

        val samples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            samples[i] = ((hi shl 8) or lo).toShort()
        }

        val output = ArrayList<Short>(sampleCount)
        var srcPos = 0.0
        while (srcPos < sampleCount - 1) {
            val i = srcPos.toInt()
            val frac = srcPos - i
            val a = samples[i].toDouble()
            val b = samples[i + 1].toDouble()
            output.add((a + (b - a) * frac).toInt().toShort())

            val elapsedSeconds = srcPos / sampleRate
            val rate = effect.rateAt(elapsedSeconds).coerceIn(0.3, 3.0)
            srcPos += rate
        }

        val outBytes = ByteArray(output.size * 2)
        for (i in output.indices) {
            val s = output[i].toInt()
            outBytes[i * 2] = (s and 0xFF).toByte()
            outBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return outBytes
    }
}
