package com.whatschat.app.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

/** Records raw 16-bit mono PCM audio at [SAMPLE_RATE] Hz until [stop] is called. */
class VoiceRecorder {

    companion object {
        const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val buffer = ByteArrayOutputStream()

    @Volatile
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun start() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize * 2
        )
        audioRecord = record
        buffer.reset()
        isRecording = true
        record.startRecording()

        recordingThread = Thread {
            val chunk = ByteArray(minBufferSize)
            while (isRecording) {
                val read = record.read(chunk, 0, chunk.size)
                if (read > 0) buffer.write(chunk, 0, read)
            }
        }.also { it.start() }
    }

    /** Stops recording and returns the captured raw PCM bytes. */
    fun stop(): ByteArray {
        isRecording = false
        recordingThread?.join()
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        return buffer.toByteArray()
    }

    /** Stops and discards whatever was captured so far. */
    fun cancel() {
        isRecording = false
        recordingThread?.join()
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        buffer.reset()
    }
}
