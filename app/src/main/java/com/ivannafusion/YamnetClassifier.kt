/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2026 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 *
 * YamnetClassifier — wrapper real sobre el runtime TensorFlow Lite para
 * el modelo YAMNet (521 clases de eventos de audio, AudioSet-YouTube
 * corpus, MobileNet_v1).
 *
 * Por qué YAMNet y no un modelo "fabricado": entrenar un clasificador
 * de género/voz desde cero requiere un dataset etiquetado de audio y
 * GPU para entrenamiento — ninguno de los dos está disponible en este
 * proyecto. YAMNet es un modelo REAL, preentrenado, gratuito, ligero
 * (<5MB cuantizado) y verificado contra documentación oficial de
 * TensorFlow/Google. No reemplaza un clasificador de género musical
 * específico, pero sí distingue genuinamente Música / Habla / Silencio
 * / otros eventos — suficiente para que aiSetAutoAdapt() tenga una
 * señal real detrás en vez de un valor fijo.
 *
 * Modelo: https://tfhub.dev/google/lite-model/yamnet/classification/tflite/1
 * Tamaño: 4.13 MB — debe descargarse manualmente y colocarse en
 * app/src/main/assets/yamnet.tflite (ver assets/README_MODEL.txt).
 * No se incluye el binario en el repositorio porque go gradle/git no
 * deben versionar binarios grandes de terceros sin licencia explícita
 * verificada para redistribución en este repo.
 *
 * Entrada:  1-D float32, 15600 muestras (0.975s a 16kHz, mono, [-1,1])
 * Salida 0: float32 [1, 521] — score por clase AudioSet
 * Salida 1: float32 [1, 1024] — embedding (no usado aquí)
 * Salida 2: float32 [96, 64] — log-mel spectrogram (no usado aquí)
 */

package com.ivannafusion

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object YamnetClassifier {
    private const val TAG = "YamnetClassifier"
    private const val MODEL_FILE = "yamnet.tflite"
    private const val LABELS_FILE = "yamnet_class_map.csv"
    const val INPUT_SAMPLES = 15600   // 0.975s @ 16kHz — fijo, no configurable
    const val SAMPLE_RATE_HZ = 16000

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    // Índices reales de "Music"/"Speech"/"Silence" se resuelven por NOMBRE
    // contra el class map cargado en runtime, no se asumen como constantes
    // fijas (el orden exacto de las 521 clases depende del archivo real).
    private var musicIndex: Int = -1
    private var speechIndex: Int = -1
    private var silenceIndex: Int = -1

    val isLoaded: Boolean get() = interpreter != null

    /**
     * Carga el modelo y el class map desde assets/. Si cualquiera de los
     * dos archivos falta, isLoaded queda false y classify() devuelve
     * un resultado vacío — NUNCA se simula una clasificación falsa.
     */
    fun initialize(context: Context): Boolean {
        if (interpreter != null) return true
        return try {
            val modelBuffer = loadModelFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply { setNumThreads(2) }
            interpreter = Interpreter(modelBuffer, options)
            labels = loadLabels(context, LABELS_FILE)
            musicIndex = labels.indexOfFirst { it.equals("Music", ignoreCase = true) }
            speechIndex = labels.indexOfFirst { it.equals("Speech", ignoreCase = true) }
            silenceIndex = labels.indexOfFirst { it.equals("Silence", ignoreCase = true) }
            Log.i(TAG, "YAMNet cargado: ${labels.size} clases, music=$musicIndex speech=$speechIndex silence=$silenceIndex")
            true
        } catch (e: Exception) {
            Log.w(TAG, "YAMNet no disponible (¿faltan $MODEL_FILE / $LABELS_FILE en assets/?): ${e.message}")
            interpreter = null
            false
        }
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }

    data class Result(
        val topLabel: String,
        val topScore: Float,
        val musicScore: Float,
        val speechScore: Float,
        val silenceScore: Float,
        val inferenceTimeMs: Long
    )

    /**
     * Ejecuta una inferencia sobre exactamente INPUT_SAMPLES muestras
     * mono float32 en [-1, 1] a 16kHz. Si el modelo no está cargado,
     * devuelve null — el llamador debe tratar eso como "sin clasificador
     * disponible", no reintentar generar un resultado falso.
     */
    fun classify(samples: FloatArray): Result? {
        val interp = interpreter ?: return null
        if (samples.size != INPUT_SAMPLES) {
            Log.w(TAG, "classify(): se esperaban $INPUT_SAMPLES muestras, llegaron ${samples.size}")
            return null
        }

        val t0 = System.nanoTime()
        val scores = Array(1) { FloatArray(labels.size.takeIf { it > 0 } ?: 521) }
        val embeddings = Array(1) { FloatArray(1024) }
        val spectrogram = Array(96) { FloatArray(64) }

        val outputs: MutableMap<Int, Any> = HashMap()
        outputs[0] = scores
        outputs[1] = embeddings
        outputs[2] = spectrogram

        try {
            interp.runForMultipleInputsOutputs(arrayOf<Any>(samples), outputs)
        } catch (e: Exception) {
            Log.e(TAG, "Error en inferencia YAMNet", e)
            return null
        }
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000

        val frameScores = scores[0]
        var bestIdx = 0
        for (i in frameScores.indices) {
            if (frameScores[i] > frameScores[bestIdx]) bestIdx = i
        }

        return Result(
            topLabel = labels.getOrElse(bestIdx) { "Clase $bestIdx" },
            topScore = frameScores.getOrElse(bestIdx) { 0f },
            musicScore = if (musicIndex >= 0) frameScores.getOrElse(musicIndex) { 0f } else 0f,
            speechScore = if (speechIndex >= 0) frameScores.getOrElse(speechIndex) { 0f } else 0f,
            silenceScore = if (silenceIndex >= 0) frameScores.getOrElse(silenceIndex) { 0f } else 0f,
            inferenceTimeMs = elapsedMs
        )
    }

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        val afd = context.assets.openFd(fileName)
        val inputStream = FileInputStream(afd.fileDescriptor)
        val startOffset = afd.startOffset
        val declaredLength = afd.declaredLength
        return inputStream.channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(context: Context, fileName: String): List<String> {
        val result = mutableListOf<String>()
        context.assets.open(fileName).use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                var firstLine = true
                reader.forEachLine { line ->
                    if (firstLine) { firstLine = false; return@forEachLine } // header: index,mid,display_name
                    val parts = line.split(",")
                    if (parts.size >= 3) {
                        // display_name puede contener comas si está citado;
                        // unimos todo lo que sigue al segundo "," por seguridad.
                        result.add(parts.drop(2).joinToString(",").trim('"', ' '))
                    }
                }
            }
        }
        return result
    }
}
