package com.whatschat.app.data.audio

import java.io.File
import java.io.FileOutputStream

/** Wraps raw 16-bit PCM bytes in a standard 44-byte WAV header. */
object WavFile {

    fun write(outputFile: File, pcmData: ByteArray, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val header = ByteArray(44)

        fun writeString(offset: Int, value: String) {
            value.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
        }
        fun writeIntLE(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
            header[offset + 2] = ((value shr 16) and 0xff).toByte()
            header[offset + 3] = ((value shr 24) and 0xff).toByte()
        }
        fun writeShortLE(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
        }

        writeString(0, "RIFF")
        writeIntLE(4, 36 + dataSize)
        writeString(8, "WAVE")
        writeString(12, "fmt ")
        writeIntLE(16, 16)
        writeShortLE(20, 1) // PCM
        writeShortLE(22, channels)
        writeIntLE(24, sampleRate)
        writeIntLE(28, byteRate)
        writeShortLE(32, blockAlign)
        writeShortLE(34, bitsPerSample)
        writeString(36, "data")
        writeIntLE(40, dataSize)

        FileOutputStream(outputFile).use { out ->
            out.write(header)
            out.write(pcmData)
        }
    }
}
