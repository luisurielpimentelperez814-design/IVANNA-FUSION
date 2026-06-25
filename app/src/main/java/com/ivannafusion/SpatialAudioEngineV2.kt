package com.ivannafusion

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*

class SpatialAudioEngineV2(
    private val sampleRate: Int = 48000,
    private val bufferSize: Int = 64  // Tamaño de bloque (ajustable)
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    // Referencia al estado global (legacy) o al nuevo repositorio
    private val muController = MuController()

    fun start() {
        if (isRunning) return
        isRunning = true

        // Inicializar AudioRecord
        val recordBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            recordBufferSize
        )

        // Inicializar AudioTrack para la salida
        val trackBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(trackBufferSize)
            .build()

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            Log.e("SpatialAudioEngineV2", "Error al inicializar AudioRecord o AudioTrack")
            isRunning = false
            return
        }

        // Iniciar el loop de procesamiento
        scope.launch {
            val inputBuffer = ShortArray(bufferSize)
            val omegaL = ShortArray(bufferSize)
            val omegaR = ShortArray(bufferSize)
            val outputBuffer = ShortArray(bufferSize * 2)  // estéreo

            // Iniciar μ controller
            muController.startUpdating()

            audioRecord?.startRecording()
            audioTrack?.play()

            while (isRunning) {
                val read = audioRecord?.read(inputBuffer, 0, bufferSize) ?: 0
                if (read > 0) {
                    // Obtener μ actualizado
                    val mu = muController.getCurrentMu()

                    // Llamar al motor espacial nativo
                    IvannaNativeLib.nativeRenderSpatial(inputBuffer, omegaL, omegaR, mu)

                    // Aplicar la ecuación de equilibrio (ω-engine) directamente en Kotlin o llamar a otra función nativa
                    // Pero ya lo hacemos dentro de nativeRenderSpatial, que llama a omega_engine.
                    // En esta versión, omegaL y omegaR ya son la salida final después de omega_engine.

                    // Empaquetar en estéreo (intercalado L, R, L, R...)
                    for (i in 0 until bufferSize) {
                        outputBuffer[i * 2] = omegaL[i]
                        outputBuffer[i * 2 + 1] = omegaR[i]
                    }

                    // Escribir en AudioTrack
                    audioTrack?.write(outputBuffer, 0, outputBuffer.size)
                } else {
                    // Si no hay datos, esperar un poco
                    delay(1)
                }
            }

            // Liberar recursos
            audioRecord?.stop()
            audioRecord?.release()
            audioTrack?.stop()
            audioTrack?.release()
            muController.stopUpdating()
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
    }
}
