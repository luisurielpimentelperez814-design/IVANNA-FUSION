package com.ivannafusion

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.*

class SpatialAudioEngineV2 {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val bufferSize = 256
    private val sampleRate = 48000

    fun start() {
        if (isRunning) return
        isRunning = true

        IvannaNativeLib.nativeInitSpatialEngine(sampleRate, bufferSize)

        val recordBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufferSize)

        val trackBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(android.media.AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build())
            .setBufferSizeInBytes(trackBufferSize)
            .build()

        audioRecord?.startRecording()
        audioTrack?.play()

        scope.launch {
            val inputShort = ShortArray(bufferSize)
            val outputL = FloatArray(bufferSize)
            val outputR = FloatArray(bufferSize)

            while (isRunning) {
                val read = audioRecord?.read(inputShort, 0, bufferSize) ?: 0
                if (read > 0) {
                    val inputFloat = FloatArray(read) { i -> inputShort[i] / 32767.0f }
                    val mu = DSPState.mu
                    val posX = DSPState.posX
                    val posY = DSPState.posY
                    val posZ = DSPState.posZ
                    IvannaNativeLib.nativeRenderSpatialBlock(inputFloat, outputL, outputR, posX, posY, posZ, mu)
                    val mixed = FloatArray(read * 2)
                    for (i in 0 until read) {
                        mixed[i * 2] = outputL[i]
                        mixed[i * 2 + 1] = outputR[i]
                    }
                    val outShort = ShortArray(mixed.size) { i -> (mixed[i] * 32767.0f).toInt().toShort() }
                    audioTrack?.write(outShort, 0, outShort.size, AudioTrack.WRITE_BLOCKING)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioTrack?.stop()
        audioTrack?.release()
        IvannaNativeLib.nativeReleaseSpatialEngine()
    }
}
