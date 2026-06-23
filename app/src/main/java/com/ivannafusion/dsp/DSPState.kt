package com.ivannafusion.dsp

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object DSPState {
    private lateinit var prefs: SharedPreferences
    private var isInitialized = false

    private val _masterVolume = MutableStateFlow(0.8f)
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()
    private val _bassBoost = MutableStateFlow(0f)
    val bassBoost: StateFlow<Float> = _bassBoost.asStateFlow()
    private val _midRange = MutableStateFlow(0f)
    val midRange: StateFlow<Float> = _midRange.asStateFlow()
    private val _treble = MutableStateFlow(0f)
    val treble: StateFlow<Float> = _treble.asStateFlow()
    private val _reverbLevel = MutableStateFlow(0.2f)
    val reverbLevel: StateFlow<Float> = _reverbLevel.asStateFlow()
    private val _delayTime = MutableStateFlow(250f)
    val delayTime: StateFlow<Float> = _delayTime.asStateFlow()
    private val _delayFeedback = MutableStateFlow(0.3f)
    val delayFeedback: StateFlow<Float> = _delayFeedback.asStateFlow()
    private val _compressorThreshold = MutableStateFlow(-20f)
    val compressorThreshold: StateFlow<Float> = _compressorThreshold.asStateFlow()
    private val _compressorRatio = MutableStateFlow(4f)
    val compressorRatio: StateFlow<Float> = _compressorRatio.asStateFlow()
    private val _rmsLevel = MutableStateFlow(-90f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()
    private val _spectralFlatness = MutableStateFlow(0f)
    val spectralFlatness: StateFlow<Float> = _spectralFlatness.asStateFlow()

    fun initialize(context: Context) {
        if (isInitialized) return
        prefs = context.getSharedPreferences("ivanna_dsp_state", Context.MODE_PRIVATE)
        isInitialized = true
        loadPersistedState()
    }

    private fun loadPersistedState() {
        _masterVolume.value = prefs.getFloat("masterVolume", 0.8f)
        _bassBoost.value = prefs.getFloat("bassBoost", 0f)
        _midRange.value = prefs.getFloat("midRange", 0f)
        _treble.value = prefs.getFloat("treble", 0f)
        _reverbLevel.value = prefs.getFloat("reverbLevel", 0.2f)
        _delayTime.value = prefs.getFloat("delayTime", 250f)
        _delayFeedback.value = prefs.getFloat("delayFeedback", 0.3f)
        _compressorThreshold.value = prefs.getFloat("compressorThreshold", -20f)
        _compressorRatio.value = prefs.getFloat("compressorRatio", 4f)
    }

    /**
     * Persiste un valor solo si DSPState.initialize() ya corrió.
     * CORRECCIÓN DE CRASH: antes, cada setter llamaba a prefs.edit()
     * directamente — 'prefs' es 'lateinit var', y si initialize()
     * nunca se llamó (que era el caso: ningún archivo del proyecto lo
     * invocaba), el primer setter ejecutado lanzaba
     * UninitializedPropertyAccessException sin try/catch, crasheando
     * la app en cuanto el usuario tocaba cualquier slider conectado a
     * DSPState. Si no está inicializado, el StateFlow en memoria sí se
     * actualiza (la UI sigue funcionando), pero no se persiste a disco
     * hasta que initialize() corra.
     */
    private fun persistSafely(key: String, value: Float) {
        if (!isInitialized) {
            android.util.Log.w("DSPState", "persistSafely('$key'): DSPState.initialize() no se ha llamado todavía, valor no persistido (solo en memoria)")
            return
        }
        prefs.edit().putFloat(key, value).apply()
    }

    fun setMasterVolume(v: Float) {
        _masterVolume.update { v }
        persistSafely("masterVolume", v)
    }
    fun setBassBoost(v: Float) {
        _bassBoost.update { v }
        persistSafely("bassBoost", v)
    }
    fun setMidRange(v: Float) {
        _midRange.update { v }
        persistSafely("midRange", v)
    }
    fun setTreble(v: Float) {
        _treble.update { v }
        persistSafely("treble", v)
    }
    fun setReverbLevel(v: Float) {
        _reverbLevel.update { v }
        persistSafely("reverbLevel", v)
    }
    fun setDelayTime(v: Float) {
        _delayTime.update { v }
        persistSafely("delayTime", v)
    }
    fun setDelayFeedback(v: Float) {
        _delayFeedback.update { v }
        persistSafely("delayFeedback", v)
    }
    fun setCompressorThreshold(v: Float) {
        _compressorThreshold.update { v }
        persistSafely("compressorThreshold", v)
    }
    fun setCompressorRatio(v: Float) {
        _compressorRatio.update { v }
        persistSafely("compressorRatio", v)
    }
    fun updateMetrics(rms: Float, flatness: Float) {
        _rmsLevel.update { rms }
        _spectralFlatness.update { flatness }
    }
}
