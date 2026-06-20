package com.ivannafusion

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue

/**
 * Motor de audio que conecta Kotlin con funciones C++ nativas.
 * Gestiona grabación, procesamiento evolutivo y predicción de fase.
 */
class AudioEngine {
    companion object {
        private const val TAG = "AudioEngine"
        private const val SAMPLE_RATE = 48000
        private const val BUFFER_SIZE = 4096
    }

    private var audioRecord: AudioRecord? = null
    private var isProcessing = false
    private var processingThread: Thread? = null
    private val audioQueue = LinkedBlockingQueue<FloatArray>(10)

    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "Inicializando AudioEngine...")
            
            // Inicializar motor de audio nativo
            if (!IvannaNativeLib.nativeInitAudioEngine(SAMPLE_RATE, BUFFER_SIZE)) {
                Log.e(TAG, "Error: nativeInitAudioEngine falló")
                return false
            }

            // Inicializar kernel evolutivo
            if (!IvannaNativeLib.nativeInitializeEvolution(
                populationSize = 64,
                generations = 100
            )) {
                Log.e(TAG, "Error: nativeInitializeEvolution falló")
                return false
            }

            // Inicializar phase oracle
            if (!IvannaNativeLib.nativeSetPhaseParameters(
                alpha = 0.5f,
                beta = 0.3f,
                gamma = 0.2f
            )) {
                Log.e(TAG, "Error: nativeSetPhaseParameters falló")
                return false
            }

            Log.d(TAG, "AudioEngine inicializado exitosamente")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en initialize()", e)
            false
        }
    }

    fun startAudioCapture(): Boolean {
        return try {
            Log.d(TAG, "Iniciando captura de audio...")
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                bufferSize * 2
            )

            audioRecord?.startRecording()
            isProcessing = true
            startProcessingThread()
            
            Log.d(TAG, "Captura de audio iniciada")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar captura de audio", e)
            false
        }
    }

    private fun startProcessingThread() {
        processingThread = Thread {
            val buffer = FloatArray(BUFFER_SIZE)
            val outputBuffer = FloatArray(BUFFER_SIZE)

            while (isProcessing) {
                try {
                    // Leer audio
                    val readCount = audioRecord?.read(buffer, 0, BUFFER_SIZE, AudioRecord.READ_BLOCKING) ?: 0

                    if (readCount > 0) {
                        // Procesar con motor nativo (evolutionary + phase oracle)
                        val processedBuffer = processAudioStep(buffer, readCount)
                        
                        // Aplicar predicción de fase
                        val predictedBuffer = IvannaNativeLib.nativePredictSamples(
                            processedBuffer,
                            processedBuffer.size
                        )

                        // Encolar para reproducción
                        audioQueue.offer(predictedBuffer)
                    }

                    // Evolucionar algoritmo genético
                    IvannaNativeLib.nativeEvolveStep()
                    
                    // Log de fitness cada 10 pasos
                    val gen = IvannaNativeLib.nativeGetGeneration()
                    if (gen % 10 == 0) {
                        val fitness = IvannaNativeLib.nativeGetBestFitness()
                        Log.d(TAG, "Gen: $gen, Fitness: $fitness")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error en processing thread", e)
                }
            }
        }.apply { start() }
    }

    private fun processAudioStep(buffer: FloatArray, count: Int): FloatArray {
        val trimmed = buffer.copyOfRange(0, count)
        val output = FloatArray(count)
        
        return try {
            IvannaNativeLib.nativeProcessAudio(trimmed, output)
            output
        } catch (e: Exception) {
            Log.e(TAG, "Error en nativeProcessAudio", e)
            trimmed
        }
    }

    fun stopAudioCapture() {
        Log.d(TAG, "Deteniendo captura de audio...")
        isProcessing = false
        
        try {
            processingThread?.join(1000)
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener captura", e)
        }
    }

    fun getProcessedAudio(): FloatArray? = audioQueue.poll()

    fun release() {
        Log.d(TAG, "Liberando AudioEngine...")
        stopAudioCapture()
        
        try {
            IvannaNativeLib.nativeReleaseAudioEngine()
            IvannaNativeLib.nativeReleaseAI()
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar recursos", e)
        }
    }

    fun getEvolutionState(): String {
        return try {
            val gen = IvannaNativeLib.nativeGetGeneration()
            val fitness = IvannaNativeLib.nativeGetBestFitness()
            val phase = IvannaNativeLib.nativeGetPhaseState()
            
            "Gen: $gen | Fitness: %.4f | Phase: %.4f".format(fitness, phase)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
