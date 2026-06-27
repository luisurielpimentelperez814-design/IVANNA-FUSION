package com.ivannafusion

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OmegaEngineBridge {
    companion object {
        private const val TAG = "OmegaBridge"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var telemetryJob: Job? = null

    private val _deviceTemp = MutableStateFlow(0f)
    val deviceTemp: StateFlow<Float> = _deviceTemp.asStateFlow()

    private val _latencyMs = MutableStateFlow(0f)
    val latencyMs: StateFlow<Float> = _latencyMs.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun connect(): Boolean {
        if (_isConnected.value) return true
        _isConnected.value = true
        startTelemetryLoop()
        Log.d(TAG, "connect ok")
        return true
    }

    fun disconnect() {
        telemetryJob?.cancel()
        telemetryJob = null
        _isConnected.value = false
        Log.d(TAG, "disconnect")
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive && _isConnected.value) {
                _deviceTemp.value = ThermalMonitor.getMaxTemperature().toFloat()
                _latencyMs.value = DSPState.deviceBufferLatencyUs / 1000f
                delay(500)
            }
        }
    }

    private fun send(command: String) {
        Log.d(TAG, "cmd=$command")
    }

    fun setAiEnabled(enabled: Boolean) {
        DSPState.aiEnabled = enabled
        send("SET_AI_ENABLED:${if (enabled) 1 else 0}")
    }

    fun setAiAutoAdapt(enabled: Boolean) {
        DSPState.aiAutoAdapt = enabled
        send("SET_AI_AUTO_ADAPT:${if (enabled) 1 else 0}")
    }

    fun setAiSensitivity(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        DSPState.aiSensitivity = clamped
        send("SET_AI_SENSITIVITY:$clamped")
    }
}
