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
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Process

object AudioEngine {
    private var audioTrack: AudioTrack? = null
    private var nativeHandle: Long = 0

    var audio_fs_hz: Int = 48000
    var audio_bit_depth: Int = 24
    var audio_latencia_us: Int = 0

    fun initialize() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        // Detectar capacidades del hardware
        val audioManager = IVANNAApplication().applicationContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audio_fs_hz = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        audio_bit_depth = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 24

        // Si hay DAC USB externo, intentar 384 kHz
        if (hasUsbAudioDevice()) {
            audio_fs_hz = 384000
            audio_bit_depth = 32
        }

        nativeHandle = nativeCreateEngine(audio_fs_hz, audio_bit_depth)
        startAudioTrack()
    }

    private fun hasUsbAudioDevice(): Boolean {
        return try {
            val audioManager = IVANNAApplication().applicationContext?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any { it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE ||
                        it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET }
            } else false
        } catch (e: Exception) { false }
    }

    private fun startAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            audio_fs_hz,
            AudioFormat.CHANNEL_OUT_STEREO,
            if (audio_bit_depth == 32) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_24BIT_PACKED
        )

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
                    .setEncoding(if (audio_bit_depth == 32) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_24BIT_PACKED)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        audioTrack?.play()

        // Iniciar thread nativo de procesamiento
        nativeStartProcessing(nativeHandle)
    }

    fun getLatencyMicros(): Int {
        audio_latencia_us = nativeGetLatency(nativeHandle)
        return audio_latencia_us
    }

    fun setFusionLevel(level: Float) {
        nativeSetFusionLevel(nativeHandle, level.coerceIn(0f, 1f))
    }

    fun getPhaseErrorRms(): Float {
        return nativeGetPhaseError(nativeHandle)
    }

    fun shutdown() {
        nativeDestroyEngine(nativeHandle)
        audioTrack?.stop()
        audioTrack?.release()
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
