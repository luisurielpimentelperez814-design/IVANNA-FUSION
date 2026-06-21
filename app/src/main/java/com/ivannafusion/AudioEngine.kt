package com.ivannafusion

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlin.random.Random

class AudioEngine {
    companion object {
        private const val TAG = "AudioEngine"
        var audio_fs_hz: Int = 48000
            private set
        var audio_bit_depth: Int = 16
            private set
        var audio_latencia_us: Int = 5000
            private set
    }

    var initialized: Boolean = false
        private set

    private var mutationRate: Float = 0.01f
    private var generation: Int = 0
    private var bestFitness: Float = 0.0f
    private var phaseErrorRms: Float = 0.0f
    private var fusionLevel: Float = 0.5f
    private var latencyMicros: Long = 5000L
    private var isEnabled: Boolean = false
    private var detectedGenre: String = "Unknown"
    private var confidence: Float = 0.5f
    private var tempo: Float = 120.0f
    private var currentCurveName: String = "Flat"
    private var currentCurveDesc: String = "Sin procesar"

    private val omegaBridge = OmegaMagiskBridge()
    
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(context: Context, onResult: (Boolean) -> Unit = {}) {
        engineScope.launch {
            try {
                Log.d(TAG, "Inicializando AudioEngine en background...")
                
                val moduleInstalled = withContext(Dispatchers.IO) {
                    MagiskBridge.isModuleInstalled()
                }
                
                initialized = moduleInstalled
                isEnabled = initialized
                Log.d(TAG, "Módulo Magisk instalado: $initialized")                
                withContext(Dispatchers.IO) {
                    omegaBridge.connect()
                }
                
                withContext(Dispatchers.IO) {
                    DSPController.init()
                }
                
                Log.d(TAG, "AudioEngine inicializado correctamente")
                
                withContext(Dispatchers.Main) {
                    onResult(initialized)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inicializando", e)
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    fun startAudioCapture() {
        engineScope.launch {
            try {
                Log.d(TAG, "Iniciando captura")
                isEnabled = true
                withContext(Dispatchers.IO) {
                    MagiskBridge.sendCommand("start")
                    omegaBridge.setProcessingState(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
            }
        }
    }

    fun stopAudioCapture() {
        engineScope.launch {
            try {
                Log.d(TAG, "Deteniendo captura")
                isEnabled = false
                withContext(Dispatchers.IO) {
                    MagiskBridge.sendCommand("stop")
                    omegaBridge.setProcessingState(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
            }        }
    }

    fun release() {
        engineScope.launch {
            try {
                initialized = false
                isEnabled = false
                withContext(Dispatchers.IO) {
                    omegaBridge.disconnect()
                    DSPController.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
            }
        }
        engineScope.cancel()
    }

    fun restart() {
        engineScope.launch {
            withContext(Dispatchers.IO) {
                release()
                MagiskBridge.sendCommand("restart")
                omegaBridge.connect()
                DSPController.init()
                initialized = MagiskBridge.isModuleInstalled()
                isEnabled = initialized
            }
        }
    }

    fun getEvolutionState(): String = "Gen: $generation | Fit: ${String.format("%.3f", bestFitness)}"
    fun getLatencyMicros(): Long {
        val telemetry = omegaBridge.telemetry.value
        return if (telemetry.isConnected) (telemetry.latencyMs * 1000f).toLong() else latencyMicros
    }
    fun getPhaseErrorRms(): Float = phaseErrorRms
    fun getGeneration(): Int = generation
    fun getBestFitness(): Float = bestFitness
    fun getMutationRate(): Float = mutationRate
    fun isOmegaModuleConnected(): Boolean = omegaBridge.isConnected.value
    fun getDeviceTemperature(): Float = omegaBridge.telemetry.value.temperature

    fun setMutationRate(rate: Float) {
        mutationRate = rate.coerceIn(0f, 1f)
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("mutation", mutationRate)
            }        }
    }

    fun setFusionLevel(level: Float) {
        fusionLevel = level.coerceIn(0f, 1f)
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setWet(fusionLevel)
                omegaBridge.setVocoderMix(fusionLevel)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled) startAudioCapture() else stopAudioCapture()
    }

    fun initializeEvolution() {
        generation = 0
        bestFitness = 0.0f
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.sendCommand("evo:init")
            }
        }
        Log.d(TAG, "Evolución inicializada")
    }

    fun evolveStep() {
        generation++
        bestFitness = (bestFitness + Random.nextFloat() * 0.05f).coerceAtMost(1.0f)
        phaseErrorRms = Random.nextFloat() * 0.1f
        latencyMicros = 3000L + Random.nextLong(4000L)
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.sendCommand("evo:step")
            }
        }
    }

    fun setPreferredAudioConfig(sampleRate: Int, bitDepth: Int) {
        audio_fs_hz = sampleRate
        audio_bit_depth = bitDepth
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.sendCommand("config:sample_rate=$sampleRate;bit_depth=$bitDepth")
            }
        }
    }
    fun predictSamples(): FloatArray = FloatArray(128) { Random.nextFloat() * 2f - 1f }

    fun aiGetDetectedGenre(): String = detectedGenre
    fun aiGetConfidence(): Float = confidence
    fun aiGetTempo(): Float = tempo
    fun aiGetCurrentCurveName(): String = currentCurveName
    fun aiGetCurrentCurveDescription(): String = currentCurveDesc

    fun aiSetEnabled(enabled: Boolean) {
        isEnabled = enabled
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.sendCommand("ai:${if (enabled) "enable" else "disable"}")
            }
        }
    }

    fun aiSetAutoAdapt(auto: Boolean) {
        Log.d(TAG, "Auto adapt: $auto")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.sendCommand("ai:auto:${if (auto) "on" else "off"}")
            }
        }
    }

    fun aiSetSensitivity(sens: Float) {
        confidence = sens
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("ai_sensitivity", sens)
            }
        }
    }

    fun aiApplyCurrentCurve() {
        currentCurveName = "Applied"
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.sendCommand("ai:apply")
            }
        }
    }

    fun eqSetGain(band: Int, gain: Float) {
        Log.d(TAG, "EQ Band $band: $gain dB")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setEQBand(band, gain)                DSPController.eqSetGain(band, gain)
            }
        }
    }

    fun eqSetThreshold(thr: Float) {
        Log.d(TAG, "EQ Threshold: $thr")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("eq_threshold", thr)
            }
        }
    }

    fun eqSetRatio(ratio: Float) {
        Log.d(TAG, "EQ Ratio: $ratio")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("eq_ratio", ratio)
            }
        }
    }

    fun compSetThreshold(thr: Float) {
        Log.d(TAG, "Comp Threshold: $thr")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("comp_threshold", thr)
                DSPController.compSetThreshold(thr)
            }
        }
    }

    fun compSetRatio(ratio: Float) {
        Log.d(TAG, "Comp Ratio: $ratio")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("comp_ratio", ratio)
                DSPController.compSetRatio(ratio)
            }
        }
    }

    fun compSetAttack(attack: Float) {
        Log.d(TAG, "Comp Attack: $attack")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("comp_attack", attack)
                DSPController.compSetAttack(attack)
            }        }
    }

    fun compSetRelease(release: Float) {
        Log.d(TAG, "Comp Release: $release")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("comp_release", release)
                DSPController.compSetRelease(release)
            }
        }
    }

    fun convolverSetEnabled(enabled: Boolean) {
        Log.d(TAG, "Convolver: $enabled")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.sendCommand("convolver:${if (enabled) "enable" else "disable"}")
            }
        }
    }

    fun convolverLoadPreset(name: String) {
        Log.d(TAG, "Convolver preset: $name")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.loadPreset(name)
            }
        }
    }

    fun convolverSetMix(mix: Float) {
        Log.d(TAG, "Convolver mix: $mix")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setWet(mix)
                DSPController.excSetMix(mix)
            }
        }
    }

    fun surroundSetWidth(w: Float) {
        Log.d(TAG, "Surround Width: $w")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("surround_width", w)
            }
        }
    }
    fun surroundSetLevel(l: Float) {
        Log.d(TAG, "Surround Level: $l")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("surround_level", l)
            }
        }
    }

    fun surroundSetHeight(h: Float) {
        Log.d(TAG, "Surround Height: $h")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("surround_height", h)
            }
        }
    }

    fun surroundSetRoom(r: Float) {
        Log.d(TAG, "Surround Room: $r")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("surround_room", r)
            }
        }
    }

    fun widenerSetWidth(w: Float) {
        Log.d(TAG, "Widener: $w")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("widener_width", w)
            }
        }
    }

    fun bassSetAmount(a: Float) {
        Log.d(TAG, "Bass Amount: $a")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("bass_amount", a)
            }
        }
    }

    fun bassSetFrequency(f: Float) {
        Log.d(TAG, "Bass Freq: $f")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("bass_freq", f)            }
        }
    }

    fun upscalerSetAmount(a: Float) {
        Log.d(TAG, "Upscaler Amount: $a")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("upscaler_amount", a)
            }
        }
    }

    fun excSetDrive(drive: Float) {
        Log.d(TAG, "Exciter Drive: $drive")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                DSPController.excSetDrive(drive)
            }
        }
    }

    fun excSetMix(mix: Float) {
        Log.d(TAG, "Exciter Mix: $mix")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                DSPController.excSetMix(mix)
            }
        }
    }

    fun excSetHpfFreq(freq: Float) {
        Log.d(TAG, "Exciter HPF Freq: $freq")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                DSPController.excSetHpfFreq(freq)
            }
        }
    }

    fun upscalerSetCeiling(c: Float) {
        Log.d(TAG, "Upscaler Ceiling: $c")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.setParameter("upscaler_ceiling", c)
            }
        }
    }

    fun getMomentaryLoudness(): Float = -20f + Random.nextFloat() * 10f    fun getShortTermLoudness(): Float = -25f + Random.nextFloat() * 10f
    fun getIntegratedLoudness(): Float = -23f + Random.nextFloat() * 5f
    fun getPeakLevel(): Float = -1f - Random.nextFloat() * 5f
    fun getCorrelation(): Float = 0.8f + Random.nextFloat() * 0.2f
    fun getLoudnessRange(): Float = 10f + Random.nextFloat() * 5f

    fun setPreset(name: String) {
        Log.d(TAG, "Preset: $name")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.loadPreset(name)
                omegaBridge.setPreset(name)
                DSPController.loadPreset(if (name.contains("ock", true)) 1 else 0)
            }
        }
    }

    fun autoeqApply() {
        Log.d(TAG, "AutoEQ applied")
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.sendCommand("autoeq:apply")
            }
        }
    }

    fun reset() {
        generation = 0
        bestFitness = 0f
        fusionLevel = 0.5f
        engineScope.launch {
            withContext(Dispatchers.IO) {
                MagiskBridge.sendCommand("reset")
                omegaBridge.resetToDefaults()
            }
        }
    }
}
