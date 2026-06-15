/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process

object AudioEngine {
    private var audioTrack: AudioTrack? = null
    private var nativeHandle: Long = 0
    var initialized = false

    var audio_fs_hz: Int = 48_000
    var audio_bit_depth: Int = 32   // siempre float-32; PROPERTY_OUTPUT_FRAMES_PER_BUFFER
                                    // devuelve tamaño de buffer en frames, NO bit depth
    var audio_latencia_us: Int = 0

    fun initialize(context: Context) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        // Usar el context real, no IVANNAApplication() que tiene mBase=null → NPE
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // PROPERTY_OUTPUT_SAMPLE_RATE ya refleja el DAC USB si es la salida activa
        val nativeRate = audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: 48_000
        // AudioTrack max = 192 kHz en el mejor caso; 384 kHz no está soportado → ERROR_BAD_VALUE
        audio_fs_hz = nativeRate.coerceIn(8_000, 192_000)

        nativeHandle = nativeCreateEngine(audio_fs_hz, audio_bit_depth)
        startAudioTrack()
        initialized = true
    }

    private fun startAudioTrack() {
        // ENCODING_PCM_FLOAT: 32-bit float, API 21+, alineado con AAUDIO_FORMAT_PCM_FLOAT
        var bufferSize = AudioTrack.getMinBufferSize(
            audio_fs_hz,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        // Si la tasa nativa no es soportada, cae a 48 kHz
        if (bufferSize <= 0) {
            audio_fs_hz = 48_000
            bufferSize = AudioTrack.getMinBufferSize(
                audio_fs_hz,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
        }

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(audio_fs_hz)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        audioTrack?.play()
        nativeStartProcessing(nativeHandle)
    }

    fun getLatencyMicros(): Int {
        audio_latencia_us = nativeGetLatency(nativeHandle)
        return audio_latencia_us
    }

    fun setFusionLevel(level: Float) {
        nativeSetFusionLevel(nativeHandle, level.coerceIn(0f, 1f))
    }

    fun getPhaseErrorRms(): Float = nativeGetPhaseError(nativeHandle)

    fun shutdown() {
        nativeDestroyEngine(nativeHandle)
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private external fun nativeCreateEngine(sampleRate: Int, bitDepth: Int): Long
    private external fun nativeStartProcessing(handle: Long)
    private external fun nativeGetLatency(handle: Long): Int
    private external fun nativeSetFusionLevel(handle: Long, level: Float)
    private external fun nativeGetPhaseError(handle: Long): Float
    private external fun nativeDestroyEngine(handle: Long)

    init {
        System.loadLibrary("ivanna_trascendental")
    }
}
