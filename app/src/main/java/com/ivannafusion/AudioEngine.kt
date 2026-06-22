package com.ivannafusion

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.ivannafusion.ai.*
import com.ivannafusion.audio.AudioResampler
import com.ivannafusion.persistence.ParameterStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class AudioEngine {
    private val resampler = AudioResampler(targetSampleRate = 192000, targetBitDepth = 32)
    private var parameterStore: ParameterStore? = null

    // Sistema de IA Adaptativa
    private lateinit var modelManager: ModelManager
    private lateinit var adaptiveLearning: AdaptiveLearning
    private lateinit var aiEngine: AIInferenceEngine
    private var userMadeAdjustments = false

    // Estado persistente de parámetros
    private var savedEQBands: List<Map<String, Any>> = emptyList()
    private var savedCompressor: Map<String, Any> = emptyMap()
    private var savedExciter: Map<String, Any> = emptyMap()
    private var savedAI: Map<String, Any> = emptyMap()

    // Referencia al motor nativo
    private val nativeLib = IvannaNativeLib()

    fun initialize(context: Context, callback: (Boolean) -> Unit) {
        parameterStore = ParameterStore(context)

        // Inicializar sistema de IA adaptativa
        modelManager = ModelManager(context)
        adaptiveLearning = AdaptiveLearning(context)
        aiEngine = AIInferenceEngine(modelManager, adaptiveLearning)

        Log.i("AudioEngine", "Sistema de IA adaptativa inicializado")
        Log.i("AudioEngine", "Modelo actual: v${modelManager.currentModelVersion.value}")

        // Usar CoroutineScope correcto        CoroutineScope(Dispatchers.Main).launch {
            try {
                loadSavedParameters()

                val deviceSampleRate = getDeviceSampleRate(context)
                resampler.setInputSampleRate(deviceSampleRate)

                // Inicializar motor nativo
                nativeLib.nativeSetEnabled(true)
                nativeLib.nativeReset()

                callback(true)
            } catch (e: Exception) {
                Log.e("AudioEngine", "Error en inicialización", e)
                callback(false)
            }
        }
    }

    private suspend fun loadSavedParameters() {
        parameterStore?.let { store ->
            try {
                // Cargar EQ
                savedEQBands = store.getEQBands()?.firstOrNull() ?: emptyList()
                savedEQBands.forEachIndexed { index, band ->
                    val freq = (band["freq"] as? Number)?.toFloat() ?: 1000f
                    val gain = (band["gain"] as? Number)?.toFloat() ?: 0f
                    val q = (band["q"] as? Number)?.toFloat() ?: 1.4f
                    val enabled = (band["enabled"] as? Boolean) ?: true

                    nativeLib.nativeEqSetGain(index, gain)
                }

                // Cargar Compresor
                savedCompressor = store.getCompressor()?.firstOrNull() ?: emptyMap()
                val threshold = (savedCompressor["threshold"] as? Number)?.toFloat() ?: -20f
                val ratio = (savedCompressor["ratio"] as? Number)?.toFloat() ?: 4f
                val attack = (savedCompressor["attack"] as? Number)?.toFloat() ?: 10f
                val release = (savedCompressor["release"] as? Number)?.toFloat() ?: 100f

                nativeLib.nativeCompSetThreshold(0, threshold)
                nativeLib.nativeCompSetRatio(0, ratio)
                nativeLib.nativeCompSetAttack(0, attack)
                nativeLib.nativeCompSetRelease(0, release)

                // Cargar Excitador
                savedExciter = store.getExciter()?.firstOrNull() ?: emptyMap()

                // Cargar AI
                savedAI = store.getAI()?.firstOrNull() ?: emptyMap()                val aiEnabled = (savedAI["enabled"] as? Boolean) ?: true
                val aiSensitivity = (savedAI["intensity"] as? Number)?.toFloat() ?: 0.7f

                nativeLib.nativeAiSetEnabled(aiEnabled)
                nativeLib.nativeAiSetSensitivity(aiSensitivity)

                Log.i("AudioEngine", "Parámetros cargados correctamente")
            } catch (e: Exception) {
                Log.e("AudioEngine", "Error cargando parámetros", e)
            }
        }
    }

    fun processAudio(inputBuffer: FloatArray, sampleRate: Int): FloatArray {
        val resampledInput = resampler.upsample(inputBuffer)
        val currentParams = getCurrentParameters()
        aiEngine.startSession()
        val aiOutput = aiEngine.processAudioBlock(resampledInput)
        val dspOutput = applyDSP(aiOutput)
        aiEngine.endSession(resampledInput, dspOutput, userMadeAdjustments, currentParams)
        userMadeAdjustments = false
        return resampler.downsample(dspOutput)
    }

    private fun getCurrentParameters(): Map<String, Float> {
        return mapOf(
            "eq_bands" to savedEQBands.size.toFloat(),
            "comp_threshold" to ((savedCompressor["threshold"] as? Number)?.toFloat() ?: -20f),
            "comp_ratio" to ((savedCompressor["ratio"] as? Number)?.toFloat() ?: 4f),
            "exc_drive" to ((savedExciter["drive"] as? Number)?.toFloat() ?: 0.5f),
            "ai_intensity" to ((savedAI["intensity"] as? Number)?.toFloat() ?: 0.7f)
        )
    }

    private fun applyDSP(buffer: FloatArray): FloatArray {
        return buffer
    }

    fun recordUserAdjustment() {
        userMadeAdjustments = true
        Log.d("AudioEngine", "Usuario ajustó parámetros manualmente")
    }

    fun getAIModelVersion(): Int = modelManager.currentModelVersion.value
    fun getAITotalExperiences(): Int = adaptiveLearning.totalExperiences.value
    fun getAIInferenceCount(): Long = aiEngine.inferenceCount.value
    fun aiGetDeviceTemperature(): Float = 35.0f

    fun setPreset(presetName: String) {
        Log.i("AudioEngine", "Aplicando preset: $presetName")        val presetId = when (presetName.lowercase()) {
            "rock", "classic rock" -> 1
            else -> 0
        }
        nativeLib.nativeSetPreset(presetId)
        userMadeAdjustments = false
    }

    private fun getDeviceSampleRate(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return sampleRateStr?.toIntOrNull() ?: 48000
    }
}
