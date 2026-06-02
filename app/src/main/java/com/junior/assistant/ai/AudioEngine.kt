package com.junior.assistant.ai

import android.media.*
import android.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

class AudioEngine {
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    private val isRecording = AtomicBoolean(false)
    private var isMuted = false
    private var isSpeaking = false

    // FIX: Added onAudioChunk callback — mic data must be sent to GeminiLiveClient
    var onAudioChunk: ((String) -> Unit)? = null   // Base64-encoded PCM for WebSocket
    var onAmplitudeChanged: ((Float) -> Unit)? = null
    var onSpeakingStarted: (() -> Unit)? = null
    var onSpeakingStopped: (() -> Unit)? = null

    fun startRecording() {
        val minBuf = AudioRecord.getMinBufferSize(
            16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, 2048)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16000, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufSize
        )
        audioRecord?.startRecording()
        isRecording.set(true)

        thread(name = "JuniorMic") {
            val buffer = ByteArray(1024)
            while (isRecording.get()) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    val rms = calculateRMS(buffer, read)
                    onAmplitudeChanged?.invoke(rms)
                    if (!isMuted && !isSpeaking) {
                        // FIX: send PCM data as Base64 to GeminiLiveClient via callback
                        val encoded = Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP)
                        onAudioChunk?.invoke(encoded)
                    }
                }
            }
        }
    }

    fun startPlayback() {
        val minBuf = AudioTrack.getMinBufferSize(
            24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(24000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setBufferSizeInBytes(maxOf(minBuf, 4096))
            .build()
        audioTrack?.play()

        thread(name = "JuniorSpeaker") {
            while (true) {
                // Poll with timeout so we can detect when the queue drains
                val chunk = try {
                    playbackQueue.poll(200, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    break
                }

                if (chunk != null && chunk.isNotEmpty()) {
                    if (!isSpeaking) {
                        isSpeaking = true
                        onSpeakingStarted?.invoke()
                    }
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    // FIX: queue drained naturally → reset isSpeaking so mic reopens
                    if (isSpeaking && playbackQueue.isEmpty()) {
                        isSpeaking = false
                        onSpeakingStopped?.invoke()
                    }
                }
            }
        }
    }

    private fun calculateRMS(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var i = 0
        while (i < length - 1) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += sample.toDouble() * sample.toDouble()
            i += 2
        }
        val samples = length / 2
        return if (samples > 0) (sqrt(sum / samples) / 32768.0).toFloat().coerceIn(0f, 1f) else 0f
    }

    fun queueAudio(pcmData: ByteArray) {
        playbackQueue.offer(pcmData)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun interrupt() {
        playbackQueue.clear()
        if (isSpeaking) {
            isSpeaking = false
            onSpeakingStopped?.invoke()
        }
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
    }

    fun release() {
        isRecording.set(false)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
