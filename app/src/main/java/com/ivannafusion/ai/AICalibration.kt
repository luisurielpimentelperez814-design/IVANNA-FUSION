package com.ivannafusion.ai

import kotlin.math.exp
import kotlin.math.ln

/**
 * Calibración de sliders para IA adaptativa
 * Mapea valores de UI (0-1) a parámetros de procesamiento optimizados
 */
object AICalibration {
    
    /**
     * Calibración de intensidad de IA (0.0 - 1.0)
     * Mapeo exponencial para control más natural
     */
    fun calibrateIntensity(uiValue: Float): Float {
        // Mapeo exponencial: 0.0 -> 0.0, 0.5 -> 0.3, 1.0 -> 1.0
        return when {
            uiValue <= 0.0f -> 0.0f
            uiValue >= 1.0f -> 1.0f
            else -> {
                val x = uiValue.toDouble()
                // Curva exponencial suavizada
                (exp(2.0 * x) - 1.0).toFloat() / (exp(2.0) - 1.0).toFloat()
            }
        }
    }
    
    /**
     * Calibración de parámetros de EQ
     * Ganancia: -15dB a +15dB con curva logarítmica
     */
    fun calibrateEQGain(uiValue: Float): Float {
        // uiValue: 0.0 = -15dB, 0.5 = 0dB, 1.0 = +15dB
        val dbRange = 15.0
        val normalized = (uiValue - 0.5) * 2.0  // -1.0 a 1.0
        return (normalized * dbRange).toFloat()
    }
    
    /**
     * Calibración de Q factor (ancho de banda)
     * 0.1 (muy ancho) a 10.0 (muy estrecho)
     */
    fun calibrateEQQ(uiValue: Float): Float {
        // uiValue: 0.0 = Q=0.1, 1.0 = Q=10.0
        // Mapeo logarítmico para control más intuitivo
        val minQ = 0.1
        val maxQ = 10.0
        val logMin = ln(minQ)
        val logMax = ln(maxQ)
        return exp(logMin + uiValue * (logMax - logMin)).toFloat()
    }
    
    /**
     * Calibración de threshold de compresor
     * -60dB a 0dB
     */
    fun calibrateCompressorThreshold(uiValue: Float): Float {
        // uiValue: 0.0 = -60dB, 1.0 = 0dB
        return (uiValue * 60.0 - 60.0).toFloat()
    }
    
    /**
     * Calibración de ratio de compresor
     * 1:1 a 20:1
     */
    fun calibrateCompressorRatio(uiValue: Float): Float {
        // uiValue: 0.0 = 1:1, 1.0 = 20:1
        // Mapeo exponencial para ratios más comunes en el rango bajo
        val ratios = listOf(1f, 2f, 3f, 4f, 6f, 8f, 10f, 15f, 20f)
        val index = (uiValue * (ratios.size - 1)).toInt()
        return ratios[index.coerceIn(0, ratios.size - 1)]
    }
    
    /**
     * Calibración de attack/release times
     * Attack: 0.1ms a 100ms
     * Release: 10ms a 2000ms
     */
    fun calibrateAttackTime(uiValue: Float): Float {
        val minMs = 0.1
        val maxMs = 100.0
        val logMin = ln(minMs)
        val logMax = ln(maxMs)
        return exp(logMin + uiValue * (logMax - logMin)).toFloat()
    }
    
    fun calibrateReleaseTime(uiValue: Float): Float {
        val minMs = 10.0
        val maxMs = 2000.0
        val logMin = ln(minMs)
        val logMax = ln(maxMs)
        return exp(logMin + uiValue * (logMax - logMin)).toFloat()
    }
    
    /**
     * Calibración de drive/excitación
     * 0% a 200% con curva exponencial
     */
    fun calibrateDrive(uiValue: Float): Float {
        // uiValue: 0.0 = 0%, 0.5 = 50%, 1.0 = 200%
        return when {
            uiValue <= 0.5f -> uiValue * 2.0f  // 0% a 100%
            else -> 1.0f + (uiValue - 0.5f) * 2.0f  // 100% a 200%
        }
    }
    
    /**
     * Calibración de mix (dry/wet)
     * 0% dry a 100% wet
     */
    fun calibrateMix(uiValue: Float): Float {
        return uiValue.coerceIn(0f, 1f)
    }
    
    /**
     * Calibración de width espacial
     * 0% (mono) a 200% (ultra-wide)
     */
    fun calibrateSpatialWidth(uiValue: Float): Float {
        return uiValue * 2.0f
    }
    
    /**
     * Calibración de decay de reverb
     * 0.1s a 10s con mapeo logarítmico
     */
    fun calibrateReverbDecay(uiValue: Float): Float {
        val minSec = 0.1
        val maxSec = 10.0
        val logMin = ln(minSec)
        val logMax = ln(maxSec)
        return exp(logMin + uiValue * (logMax - logMin)).toFloat()
    }
    
    /**
     * Aplicar todas las calibraciones a un preset
     */
    fun calibratePreset(preset: Map<String, Float>): Map<String, Float> {
        return mapOf(
            "ai_intensity" to calibrateIntensity(preset["ai_intensity"] ?: 0.5f),
            "eq_gain_0" to calibrateEQGain(preset["eq_gain_0"] ?: 0.5f),
            "eq_q_0" to calibrateEQQ(preset["eq_q_0"] ?: 0.5f),
            "comp_threshold" to calibrateCompressorThreshold(preset["comp_threshold"] ?: 0.5f),
            "comp_ratio" to calibrateCompressorRatio(preset["comp_ratio"] ?: 0.5f),
            "comp_attack" to calibrateAttackTime(preset["comp_attack"] ?: 0.5f),
            "comp_release" to calibrateReleaseTime(preset["comp_release"] ?: 0.5f),
            "exc_drive" to calibrateDrive(preset["exc_drive"] ?: 0.5f),
            "exc_mix" to calibrateMix(preset["exc_mix"] ?: 0.5f),
            "spatial_width" to calibrateSpatialWidth(preset["spatial_width"] ?: 0.5f),
            "conv_decay" to calibrateReverbDecay(preset["conv_decay"] ?: 0.5f),
            "conv_mix" to calibrateMix(preset["conv_mix"] ?: 0.5f)
        )
    }
}
