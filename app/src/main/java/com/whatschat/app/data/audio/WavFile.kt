package com.whatschat.app.data.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

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

    /** Scans a WAV file's chunks to compute its playback duration, in milliseconds. */
    fun readDurationMs(file: File): Long = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            raf.skipBytes(12) // "RIFF" + size + "WAVE"

            var sampleRate = 0
            var channels = 1
            var bitsPerSample = 16
            var dataSize = 0L

            while (raf.filePointer <= raf.length() - 8) {
                val chunkId = ByteArray(4).also { raf.readFully(it) }
                val sizeBytes = ByteArray(4).also { raf.readFully(it) }
                val chunkSize = (sizeBytes[0].toInt() and 0xff) or
                    ((sizeBytes[1].toInt() and 0xff) shl 8) or
                    ((sizeBytes[2].toInt() and 0xff) shl 16) or
                    ((sizeBytes[3].toInt() and 0xff) shl 24)

                when (String(chunkId, Charsets.US_ASCII)) {
                    "fmt " -> {
                        val fmt = ByteArray(chunkSize).also { raf.readFully(it) }
                        channels = (fmt[2].toInt() and 0xff) or ((fmt[3].toInt() and 0xff) shl 8)
                        sampleRate = (fmt[4].toInt() and 0xff) or
                            ((fmt[5].toInt() and 0xff) shl 8) or
                            ((fmt[6].toInt() and 0xff) shl 16) or
                            ((fmt[7].toInt() and 0xff) shl 24)
                        bitsPerSample = (fmt[14].toInt() and 0xff) or ((fmt[15].toInt() and 0xff) shl 8)
                    }
                    "data" -> {
                        dataSize = chunkSize.toLong()
                    }
                    else -> raf.skipBytes(chunkSize)
                }
                if (chunkSize % 2 == 1) raf.skipBytes(1) // chunks are word-aligned
                if (dataSize > 0 && sampleRate > 0) break
            }

            val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)
            if (bytesPerSecond <= 0) 0L else (dataSize * 1000L) / bytesPerSecond
        }
    }.getOrDefault(0L)
}
