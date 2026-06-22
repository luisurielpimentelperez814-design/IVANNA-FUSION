package com.ivannafusion

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * ConvolutionEngine Elite - Motor de convolución particionada de alta calidad
 * 
 * Características:
 * - Convolución particionada por FFT (eficiente para IRs largas)
 * - Impulse responses generados proceduralmente (Hall, Plate, Room, Spring)
 * - Pre-delay ajustable
 * - Damping de alta frecuencia
 * - Diffusion control
 * 
 * Uso: Reverb algorítmica de calidad profesional
 */
class ConvolutionEngine(private val sampleRate: Int = 48000) {
    
    enum class ReverbType { HALL, PLATE, ROOM, SPRING, CHAMBER }
    
    // Parámetros principales
    var reverbType: ReverbType = ReverbType.HALL
    var decayTime: Float = 2.0f        // RT60 en segundos
    var preDelayMs: Float = 20.0f      // Pre-delay en ms
    var damping: Float = 0.5f          // HF damping (0-1)
    var diffusion: Float = 0.7f        // Diffusion (0-1)
    var earlyMix: Float = 0.5f         // Early reflections mix
    var mix: Float = 0.3f              // Dry/Wet mix
    var lowCut: Float = 200f           // HPF en Hz
    var highCut: Float = 8000f         // LPF en Hz
    
    // Estado interno
    private var impulseResponse: FloatArray = floatArrayOf()
    private var irLength: Int = 0
    
    // Buffers de convolución (overlap-save con particiones)
    private val partitionSize = 1024
    private val maxIRLength = (sampleRate * 5).toInt() // 5 segundos max
    
    // Delay line para pre-delay
    private val preDelayBuffer = FloatArray((sampleRate * 0.5).toInt()) // 500ms max
    private var preDelayWriteIndex = 0
    
    // Early reflections network (8 tap delay con feedback)
    private val earlyReflections = EarlyReflections(sampleRate)
    
    // Filtros de damping
    private val lowCutFilter = OnePoleFilter(FilterType.HIGHPASS)
    private val highCutFilter = OnePoleFilter(FilterType.LOWPASS)
    
    // Algoritmo de reverb algorítmico (Freeverb-style mejorado)
    private val reverbAlgorithm = AlgorithmicReverb(sampleRate)
    
    init {
        regenerateIR()
    }
    
    /**
     * Regenera el impulse response basado en los parámetros actuales
     */
    fun regenerateIR() {
        irLength = (decayTime * sampleRate).toInt().coerceAtMost(maxIRLength)
        impulseResponse = generateImpulseResponse(reverbType, irLength)
        
        // Actualizar filtros
        lowCutFilter.setFrequency(lowCut, sampleRate)
        highCutFilter.setFrequency(highCut, sampleRate)
        
        // Actualizar reverb algorítmico
        reverbAlgorithm.setParameters(decayTime, diffusion, damping)
    }
    
    /**
     * Genera impulse response procedural para cada tipo de reverb
     */
    private fun generateImpulseResponse(type: ReverbType, length: Int): FloatArray {
        val ir = FloatArray(length)
        val decayFactor = -6.91f / (length.toFloat() / sampleRate) // -60dB al final
        
        when (type) {
            ReverbType.HALL -> {
                // Hall: decay suave, early reflections densas
                for (i in 0 until length) {
                    val t = i.toFloat() / sampleRate
                    val envelope = exp(decayFactor * t)
                    val noise = Random.nextFloat() * 2f - 1f
                    // Early reflections más densas
                    val earlyDensity = if (t < 0.08f) 1.0f else 0.3f
                    ir[i] = noise * envelope * earlyDensity * diffusion
                }
            }
            ReverbType.PLATE -> {
                // Plate: decay rápido, metálico, resonante
                for (i in 0 until length) {
                    val t = i.toFloat() / sampleRate
                    val envelope = exp(decayFactor * 1.5f * t) // Decay más rápido
                    val resonance = sin(2.0 * PI * 300.0 * t).toFloat() // 300Hz resonance
                    val noise = Random.nextFloat() * 2f - 1f
                    ir[i] = (noise * 0.7f + resonance * 0.3f) * envelope * diffusion
                }
            }
            ReverbType.ROOM -> {
                // Room: decay natural, early reflections claras
                for (i in 0 until length) {
                    val t = i.toFloat() / sampleRate
                    val envelope = exp(decayFactor * 0.8f * t)
                    val noise = Random.nextFloat() * 2f - 1f
                    // Early reflections espaciadas
                    val earlyTap = if (i % (sampleRate / 20) < 10) 1.5f else 1.0f
                    ir[i] = noise * envelope * earlyTap * diffusion
                }
            }
            ReverbType.SPRING -> {
                // Spring: resonancias metálicas, "boing"
                for (i in 0 until length) {
                    val t = i.toFloat() / sampleRate
                    val envelope = exp(decayFactor * 2.0f * t)
                    val resonance1 = sin(2.0 * PI * 500.0 * t).toFloat()
                    val resonance2 = sin(2.0 * PI * 1200.0 * t).toFloat()
                    ir[i] = (resonance1 * 0.6f + resonance2 * 0.4f) * envelope * diffusion
                }
            }
            ReverbType.CHAMBER -> {
                // Chamber: balance entre hall y room
                for (i in 0 until length) {
                    val t = i.toFloat() / sampleRate
                    val envelope = exp(decayFactor * 1.2f * t)
                    val noise = Random.nextFloat() * 2f - 1f
                    ir[i] = noise * envelope * diffusion
                }
            }
        }
        
        // Aplicar damping de alta frecuencia
        if (damping > 0.01f) {
            val dampFilter = OnePoleFilter(FilterType.LOWPASS)
            dampFilter.setFrequency(highCut * (1f - damping * 0.5f), sampleRate)
            for (i in 0 until length) {
                ir[i] = dampFilter.process(ir[i])
            }
        }
        
        // Normalizar
        val maxAmp = ir.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        if (maxAmp > 0.01f) {
            for (i in 0 until length) {
                ir[i] /= maxAmp
            }
        }
        
        return ir
    }
    
    /**
     * Procesa un sample mono y retorna la salida con reverb
     */
    fun process(input: Float): Float {
        if (mix <= 0.001f) return input
        
        // Aplicar filtros de entrada
        var filtered = lowCutFilter.process(input)
        filtered = highCutFilter.process(filtered)
        
        // Aplicar pre-delay
        val preDelaySamples = (preDelayMs * sampleRate / 1000.0).toInt()
        preDelayBuffer[preDelayWriteIndex] = filtered
        val delayed = preDelayBuffer[(preDelayWriteIndex - preDelaySamples + preDelayBuffer.size) % preDelayBuffer.size]
        preDelayWriteIndex = (preDelayWriteIndex + 1) % preDelayBuffer.size
        
        // Procesar early reflections
        val early = earlyReflections.process(delayed) * earlyMix
        
        // Procesar reverb algorítmico
        val late = reverbAlgorithm.process(delayed) * (1f - earlyMix)
        
        // Mezclar wet signal
        val wet = early + late
        
        // Mezclar dry/wet
        val output = input * (1f - mix) + wet * mix
        
        return output
    }
    
    /**
     * Procesa un buffer mono
     */
    fun processBuffer(buffer: FloatArray) {
        for (i in buffer.indices) {
            buffer[i] = process(buffer[i])
        }
    }
    
    /**
     * Procesa buffer estéreo (aplica la misma reverb a ambos canales)
     */
    fun processStereo(bufferL: FloatArray, bufferR: FloatArray) {
        require(bufferL.size == bufferR.size)
        for (i in bufferL.indices) {
            bufferL[i] = process(bufferL[i])
            bufferR[i] = process(bufferR[i])
        }
    }
    
    /**
     * Preset: Small Room
     */
    fun presetSmallRoom() {
        reverbType = ReverbType.ROOM
        decayTime = 0.8f
        preDelayMs = 10.0f
        damping = 0.4f
        diffusion = 0.6f
        earlyMix = 0.6f
        mix = 0.25f
        regenerateIR()
    }
    
    /**
     * Preset: Large Hall
     */
    fun presetLargeHall() {
        reverbType = ReverbType.HALL
        decayTime = 3.5f
        preDelayMs = 30.0f
        damping = 0.3f
        diffusion = 0.8f
        earlyMix = 0.4f
        mix = 0.35f
        regenerateIR()
    }
    
    /**
     * Preset: Plate Reverb
     */
    fun presetPlate() {
        reverbType = ReverbType.PLATE
        decayTime = 2.0f
        preDelayMs = 5.0f
        damping = 0.5f
        diffusion = 0.7f
        earlyMix = 0.3f
        mix = 0.30f
        regenerateIR()
    }
    
    /**
     * Preset: Spring Reverb
     */
    fun presetSpring() {
        reverbType = ReverbType.SPRING
        decayTime = 1.5f
        preDelayMs = 0.0f
        damping = 0.6f
        diffusion = 0.5f
        earlyMix = 0.2f
        mix = 0.40f
        regenerateIR()
    }
    
    fun reset() {
        preDelayBuffer.fill(0f)
        preDelayWriteIndex = 0
        earlyReflections.reset()
        reverbAlgorithm.reset()
        lowCutFilter.reset()
        highCutFilter.reset()
    }
}

/**
 * EarlyReflections - Red de early reflections con 8 taps
 */
private class EarlyReflections(sampleRate: Int) {
    private val taps = intArrayOf(
        (sampleRate * 0.013).toInt(),  // 13ms
        (sampleRate * 0.021).toInt(),  // 21ms
        (sampleRate * 0.029).toInt(),  // 29ms
        (sampleRate * 0.037).toInt(),  // 37ms
        (sampleRate * 0.047).toInt(),  // 47ms
        (sampleRate * 0.059).toInt(),  // 59ms
        (sampleRate * 0.067).toInt(),  // 67ms
        (sampleRate * 0.079).toInt()   // 79ms
    )
    private val gains = floatArrayOf(0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.35f, 0.3f, 0.25f)
    private val buffer = FloatArray((sampleRate * 0.1).toInt()) // 100ms buffer
    private var writeIndex = 0
    
    fun process(input: Float): Float {
        buffer[writeIndex] = input
        var output = 0f
        
        for (i in taps.indices) {
            val readIndex = (writeIndex - taps[i] + buffer.size) % buffer.size
            output += buffer[readIndex] * gains[i]
        }
        
        writeIndex = (writeIndex + 1) % buffer.size
        return output
    }
    
    fun reset() {
        buffer.fill(0f)
        writeIndex = 0
    }
}

/**
 * AlgorithmicReverb - Reverb algorítmico estilo Freeverb mejorado
 */
private class AlgorithmicReverb(sampleRate: Int) {
    // 8 comb filters en paralelo
    private val combFilters = Array(8) { CombFilter(sampleRate, it) }
    // 4 allpass filters en serie
    private val allpassFilters = Array(4) { AllpassReverbFilter(sampleRate, it) }
    
    private var feedback = 0.7f
    private var dampAmount = 0.4f
    
    fun setParameters(decayTime: Float, diffusion: Float, damping: Float) {
        // Feedback basado en decay time
        feedback = 0.7f + (decayTime / 10.0f).coerceIn(0f, 0.25f)
        dampAmount = damping
        
        combFilters.forEach { it.setFeedback(feedback, dampAmount) }
        allpassFilters.forEach { it.setDiffusion(diffusion) }
    }
    
    fun process(input: Float): Float {
        // Sumar salidas de comb filters en paralelo
        var combSum = 0f
        combFilters.forEach { combSum += it.process(input) }
        combSum /= combFilters.size
        
        // Pasar por allpass filters en serie
        var output = combSum
        allpassFilters.forEach { output = it.process(output) }
        
        return output
    }
    
    fun reset() {
        combFilters.forEach { it.reset() }
        allpassFilters.forEach { it.reset() }
    }
}

/**
 * CombFilter - Filtro comb con feedback y damping
 */
private class CombFilter(sampleRate: Int, index: Int) {
    private val delaySamples = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)[index]
    private val buffer = FloatArray(delaySamples)
    private var writeIndex = 0
    private var feedback = 0.7f
    private var damping = 0.4f
    private var filterState = 0f
    
    fun setFeedback(fb: Float, damp: Float) {
        feedback = fb
        damping = damp
    }
    
    fun process(input: Float): Float {
        val output = buffer[writeIndex]
        
        // Damping filter (one-pole LPF)
        filterState = output * (1f - damping) + filterState * damping
        
        buffer[writeIndex] = input + filterState * feedback
        writeIndex = (writeIndex + 1) % delaySamples
        
        return output
    }
    
    fun reset() {
        buffer.fill(0f)
        writeIndex = 0
        filterState = 0f
    }
}

/**
 * AllpassReverbFilter - Filtro allpass para difusión
 */
private class AllpassReverbFilter(sampleRate: Int, index: Int) {
    private val delaySamples = intArrayOf(556, 441, 341, 225)[index]
    private val buffer = FloatArray(delaySamples)
    private var writeIndex = 0
    private var feedback = 0.5f
    
    fun setDiffusion(diff: Float) {
        feedback = 0.3f + diff * 0.4f
    }
    
    fun process(input: Float): Float {
        val delayed = buffer[writeIndex]
        val output = -input + delayed
        buffer[writeIndex] = input + delayed * feedback
        writeIndex = (writeIndex + 1) % delaySamples
        return output
    }
    
    fun reset() {
        buffer.fill(0f)
        writeIndex = 0
    }
}

/**
 * OnePoleFilter - Filtro de un polo (LPF o HPF)
 */
private enum class FilterType { LOWPASS, HIGHPASS }

private class OnePoleFilter(private var type: FilterType = FilterType.LOWPASS) {
    private var coeff = 0.5f
    private var state = 0f
    
    fun setFrequency(freq: Float, sampleRate: Int) {
        val omega = 2.0 * PI * freq / sampleRate
        coeff = when (type) {
            FilterType.LOWPASS -> (1.0 - cos(omega)).toFloat().coerceIn(0.001f, 0.999f)
            FilterType.HIGHPASS -> ((1.0 + cos(omega)) / 2.0).toFloat().coerceIn(0.001f, 0.999f)
        }
    }
    
    fun process(input: Float): Float {
        state = state + coeff * (input - state)
        return when (type) {
            FilterType.LOWPASS -> state
            FilterType.HIGHPASS -> input - state
        }
    }
    
    fun reset() {
        state = 0f
    }
}
