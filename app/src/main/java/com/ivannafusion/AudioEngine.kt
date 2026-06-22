package com.ivannafusion

import android.content.Context
import android.util.Log
import com.ivannafusion.ai.*
import com.ivannafusion.audio.AudioResampler
import com.ivannafusion.persistence.ParameterStore
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
    private var savedEQBands: MutableList<Map<String, Float>> = emptyList()
    private var savedCompressor = mutableMapOf<String, Any>()
    private var savedExciter = mutableMapOf<String, Any>()
    private var savedAI = mutableMapOf<String, Any>()

    fun initialize(context: Context, callback: (Boolean) -> Unit) {
        parameterStore = ParameterStore(context)
        
        // Inicializar sistema de IA adaptativa
        modelManager = ModelManager(context)
        adaptiveLearning = AdaptiveLearning(context)
        aiEngine = AIInferenceEngine(modelManager, adaptiveLearning)
        
        Log.i("AudioEngine", "Sistema de IA adaptativa inicializado")
        Log.i("AudioEngine", "Modelo actual: v${modelManager.currentModelVersion.value}")
        
        lifeCycleScope.launch {
            loadSavedParameters()
            
            val deviceSampleRate = getDeviceSampleRate(context)
            resampler.setInputSampleRate(deviceSampleRate)
            
            nativeInitHighRes(192000, 32)
            
            callback(true)
        }
    }

    private suspend fun loadSavedParameters() {
        parameterStore?.let { store ->
            // Cargar EQ
            savedEQBands = store.getEQBands()?.firstOrNull() ?: emptyList()
            savedEQBands.forEachIndexed { index, band ->
                eQSetFreq(index, band["freq"] ?: 1000f)
                eQSetGain(index, band["gain"] ?: 0f)
                eQSetQ(index, band["q"] ?: 1.4f)
                eQSetEnabled(index, band["enabled"] ?: 1f > 0.5f)
            }
            
            // Cargar Compresor
            val comp = store.getCompressor()?.firstOrNull()
            compSetThreshold((comp["threshold"] as? Float) ?: -20f)
            compSetRatio((comp["ratio"] as? Float) ?: 4f)
            compSetAttack((comp["attack"] as? Float) ?: 10f)
            compSetRelease((comp["release"] as? Float) ?: 100f)
            compSetEnabled(comp["enabled"] as? Boolean ?: true)
            
            // Cargar Excitador
            val exc = store.getExciter()?.firstOrNull()
            excSetDrive((exc["drive"] as? Float) ?: 0.5f)
            excSetMix((exc["mix"] as? Float) ?: 0.3f)
            excSetEnabled(exc["enabled"] as? Boolean ?: true)
            
            // Cargar AI
            val ai = store.getAI()?.firstOrNull()
            setAIIntensity((ai["intensity"] as? Float) ?: 0.7f)
            setAIMode(ai["mode"] as? String ?: "adaptive")
            setAIEnabled(ai["enabled"] as? Boolean ?: true)
        }
    }

    fun processAudio(inputBuffer: FloatArray, sampleRate: Int): FloatArray {
        // 1. Resampling a 192kHz
        val resampledInput = resampler.upsample(inputBuffer)
        
        // 2. Iniciar sesión de IA
        val currentParams = getCurrentParameters()
        aiEngine.startSession()
        
        // 3. Procesar con IA adaptativa
        val aiOutput = aiEngine.processAudioBlock(resampledInput)
        
        // 4. Aplicar DSP adicional (EQ, Compresor, Exciter)
        val dspOutput = applyDSP(aiOutput)
        
        // 5. Finalizar sesión y capturar experiencia
        aiEngine.endSession(resampledInput, dspOutput, userMadeAdjustments, currentParams)
        
        // 6. Reset flag de ajustes
        userMadeAdjustments = false
        
        // 7. Downsample al formato original
        return resampler.downsample(dspOutput)
    }

    private fun getCurrentParameters(): Map<String, Float> {
        return mapOf(
            "eq_bands" to savedEQBands.size.toFloat(),
            "comp_threshold" to (savedCompressor["threshold"] as? Float ?: -20f),
            "comp_ratio" to (savedCompressor["ratio"] as? Float ?: 4f),
            "exc_drive" to (savedExciter["drive"] as? Float ?: 0.5f),
            "ai_intensity" to (savedAI["intensity"] as? Float ?: 0.7f)
        )
    }

    private fun applyDSP(buffer: FloatArray): FloatArray {
        // Aquí iría el procesamiento DSP tradicional (EQ, Compresor, etc.)
        // Por ahora, devolver el buffer sin cambios
        return buffer
    }

    // Métodos para registrar ajustes manuales del usuario
    fun recordUserAdjustment() {
        userMadeAdjustments = true
        Log.d("AudioEngine", "Usuario ajustó parámetros manualmente")
    }

    // Getters para estadísticas de IA
    fun getAIModelVersion(): Int = modelManager.currentModelVersion.value
    fun getAITotalExperiences(): Int = adaptiveLearning.totalExperiences.value
    fun getAIInferenceCount(): Long = aiEngine.inferenceCount.value

    fun aiGetDeviceTemperature(): Float = 35.0f // Placeholder

    // Aplicar preset al motor DSP
    fun setPreset(presetName: String) {
        Log.i("AudioEngine", "Aplicando preset: $presetName")
        // TODO: Mapear presetName a valores específicos de EQ, Compresor, etc.
        // Por ahora, solo registramos el cambio para que la IA lo considere
        userMadeAdjustments = false // Resetear flag al cambiar preset
    }

}
