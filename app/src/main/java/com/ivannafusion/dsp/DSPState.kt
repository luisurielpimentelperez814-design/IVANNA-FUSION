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

    fun setMasterVolume(v: Float) {
        _masterVolume.update { v }
        prefs.edit().putFloat("masterVolume", v).apply()
    }
    fun setBassBoost(v: Float) {
        _bassBoost.update { v }
        prefs.edit().putFloat("bassBoost", v).apply()
    }
    fun setMidRange(v: Float) {
        _midRange.update { v }
        prefs.edit().putFloat("midRange", v).apply()
    }
    fun setTreble(v: Float) {
        _treble.update { v }
        prefs.edit().putFloat("treble", v).apply()
    }
    fun setReverbLevel(v: Float) {
        _reverbLevel.update { v }
        prefs.edit().putFloat("reverbLevel", v).apply()
    }
    fun setDelayTime(v: Float) {
        _delayTime.update { v }
        prefs.edit().putFloat("delayTime", v).apply()
    }
    fun setDelayFeedback(v: Float) {
        _delayFeedback.update { v }
        prefs.edit().putFloat("delayFeedback", v).apply()
    }
    fun setCompressorThreshold(v: Float) {
        _compressorThreshold.update { v }
        prefs.edit().putFloat("compressorThreshold", v).apply()
    }
    fun setCompressorRatio(v: Float) {
        _compressorRatio.update { v }
        prefs.edit().putFloat("compressorRatio", v).apply()
    }
    fun updateMetrics(rms: Float, flatness: Float) {
        _rmsLevel.update { rms }
        _spectralFlatness.update { flatness }
    }
}
