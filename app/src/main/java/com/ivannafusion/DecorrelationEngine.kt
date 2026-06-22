package com.ivannafusion

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * DecorrelationEngine - Motor de decorrelación estéreo de alta calidad
 * 
 * Técnicas implementadas:
 * - Delay lines con modulación LFO (chorus-like)
 * - Allpass filters para difusión de fase
 * - Phase rotation y inversión
 * - Haas effect delays
 * - Mid/Side processing para width control
 * 
 * Uso: procesar señales mono a estéreo o ensanchar señales estéreo existentes
 */
class DecorrelationEngine(private val sampleRate: Int = 48000) {
    
    // Parámetros principales
    var width: Float = 1.0f        // 0.0 = mono, 1.0 = estéreo completo, 2.0 = ultra-wide
    var depth: Float = 0.5f        // Intensidad de modulación LFO
    var diffusion: Float = 0.3f    // Cantidad de allpass filters
    var delayMs: Float = 15.0f     // Delay base en milisegundos (Haas effect)
    var modulationRate: Float = 0.5f  // Hz del LFO
    var mix: Float = 1.0f          // Dry/Wet mix
    
    // Estado interno
    private val maxDelaySamples = (sampleRate * 0.1).toInt() // 100ms max
    private val delayLineL = CircularBuffer(maxDelaySamples)
    private val delayLineR = CircularBuffer(maxDelaySamples)
    
    // Allpass filters en cascada (8 stages para difusión máxima)
    private val allpassFiltersL = Array(8) { AllpassFilter(sampleRate) }
    private val allpassFiltersR = Array(8) { AllpassFilter(sampleRate) }
    
    // LFO para modulación
    private var lfoPhase: Double = 0.0
    private val lfoIncrement = 2.0 * PI * modulationRate / sampleRate
    
    /**
     * Procesa un frame estéreo y aplica decorrelación
     * @param inputL Canal izquierdo de entrada
     * @param inputR Canal derecho de entrada
     * @return Pair(outputL, outputR) con señal decorrelada
     */
    fun process(inputL: Float, inputR: Float): Pair<Float, Float> {
        if (mix <= 0.001f) return Pair(inputL, inputR)
        
        // Convertir a Mid/Side para control de width
        val mid = (inputL + inputR) * 0.5f
        val side = (inputL - inputR) * 0.5f
        
        // Aplicar width al side channel
        val sideProcessed = side * width
        
        // Convertir de vuelta a L/R
        var outL = mid + sideProcessed
        var outR = mid - sideProcessed
        
        // Aplicar delay de Haas (15-30ms típico)
        val delaySamples = (delayMs * sampleRate / 1000.0).toInt()
        delayLineL.write(outL)
        delayLineR.write(outR)
        
        val delayedL = delayLineL.read(delaySamples)
        val delayedR = delayLineR.read(delaySamples + (sampleRate * 0.002).toInt()) // 2ms offset
        
        // Modulación LFO para movimiento
        val lfoValue = sin(lfoPhase).toFloat()
        lfoPhase += lfoIncrement
        if (lfoPhase >= 2.0 * PI) lfoPhase -= 2.0 * PI
        
        val modulatedDelayL = delayedL + lfoValue * depth * 0.01f * sampleRate
        val modulatedDelayR = delayedR - lfoValue * depth * 0.01f * sampleRate
        
        // Aplicar difusión con allpass filters
        val activeStages = (diffusion * 8).toInt().coerceIn(0, 8)
        var diffusedL = modulatedDelayL
        var diffusedR = modulatedDelayR
        
        for (i in 0 until activeStages) {
            diffusedL = allpassFiltersL[i].process(diffusedL)
            diffusedR = allpassFiltersR[i].process(diffusedR)
        }
        
        // Mezclar dry/wet
        val finalL = inputL * (1f - mix) + diffusedL * mix
        val finalR = inputR * (1f - mix) + diffusedR * mix
        
        return Pair(finalL, finalR)
    }
    
    /**
     * Procesa un buffer estéreo completo
     */
    fun processBuffer(bufferL: FloatArray, bufferR: FloatArray) {
        require(bufferL.size == bufferR.size) { "Buffers deben tener el mismo tamaño" }
        
        for (i in bufferL.indices) {
            val (outL, outR) = process(bufferL[i], bufferR[i])
            bufferL[i] = outL
            bufferR[i] = outR
        }
    }
    
    /**
     * Preset: Natural Stereo (decorrelación suave)
     */
    fun presetNatural() {
        width = 1.2f
        depth = 0.3f
        diffusion = 0.4f
        delayMs = 12.0f
        modulationRate = 0.3f
        mix = 0.8f
    }
    
    /**
     * Preset: Wide Ambient (decorrelación agresiva)
     */
    fun presetWide() {
        width = 1.8f
        depth = 0.6f
        diffusion = 0.7f
        delayMs = 20.0f
        modulationRate = 0.8f
        mix = 1.0f
    }
    
    /**
     * Preset: Mono to Stereo (convierte mono a estéreo)
     */
    fun presetMonoToStereo() {
        width = 1.5f
        depth = 0.5f
        diffusion = 0.6f
        delayMs = 18.0f
        modulationRate = 0.5f
        mix = 1.0f
    }
    
    fun reset() {
        delayLineL.clear()
        delayLineR.clear()
        allpassFiltersL.forEach { it.reset() }
        allpassFiltersR.forEach { it.reset() }
        lfoPhase = 0.0
    }
}

/**
 * CircularBuffer - Delay line circular eficiente
 */
private class CircularBuffer(private val size: Int) {
    private val buffer = FloatArray(size)
    private var writeIndex = 0
    
    fun write(sample: Float) {
        buffer[writeIndex] = sample
        writeIndex = (writeIndex + 1) % size
    }
    
    fun read(delaySamples: Int): Float {
        val readIndex = (writeIndex - delaySamples + size) % size
        return buffer[readIndex]
    }
    
    fun clear() {
        buffer.fill(0f)
        writeIndex = 0
    }
}

/**
 * AllpassFilter - Filtro allpass de primer orden para difusión de fase
 */
private class AllpassFilter(sampleRate: Int) {
    private var delaySamples = (sampleRate * Random.nextFloat() * 0.01).toInt() // 0-10ms random
    private val buffer = FloatArray(maxOf(delaySamples, 1))
    private var writeIndex = 0
    private var feedback = 0.5f + Random.nextFloat() * 0.3f // 0.5-0.8 feedback
    
    fun process(input: Float): Float {
        if (delaySamples == 0) return input
        
        val delayed = buffer[writeIndex]
        val output = -input * feedback + delayed
        buffer[writeIndex] = input + delayed * feedback
        writeIndex = (writeIndex + 1) % delaySamples
        
        return output
    }
    
    fun reset() {
        buffer.fill(0f)
        writeIndex = 0
    }
}
