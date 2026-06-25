package com.ivannafusion

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * OmegaEngineBridge — puente Kotlin → omega_daemon vía Unix socket abstracto.
 *
 * CORRECCIÓN: La versión anterior usaba Socket("127.0.0.1", 8500) (TCP).
 * El daemon solo escucha en un socket abstracto Unix ("omega_daemon_socket").
 * Un socket TCP nunca podía conectar → todos los comandos se descartaban
 * silenciosamente → controles completamente desconectados del efecto.
 *
 * También corregido: el protocolo de comandos pasó de JSON ad-hoc a las
 * cadenas de texto que el daemon realmente parsea:
 *   SET_PROCESSING:1 | SET_INTENSITY:0.8 | SET_VOCODER_MIX:0.5
 *   SET_BYPASS:1 | SET_PRESET:classic_rock | RESET_DEFAULTS | GET_TELEMETRY
 *
 * Al conectar, el daemon envía 1 byte carrier + SCM_RIGHTS(shm_fd).
 * Kotlin no necesita el fd, así que se consume el byte y se continúa.
 */
class OmegaEngineBridge {

    companion object {
        private const val TAG         = "OmegaEngineBridge"
        private const val SOCKET_NAME = "omega_daemon_socket"
        private const val CONNECT_RETRY_MS = 2000L
        private const val TELEMETRY_POLL_MS = 1000L
    }

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _vocoderMix = MutableStateFlow(0.8f)
    val vocoderMix: StateFlow<Float> = _vocoderMix.asStateFlow()

    private val _deviceTemp = MutableStateFlow(35.0f)
    val deviceTemp: StateFlow<Float> = _deviceTemp.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _latencyMs = MutableStateFlow(0.0f)
    val latencyMs: StateFlow<Float> = _latencyMs.asStateFlow()

    private var socket: LocalSocket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var scope: CoroutineScope? = null

    // ── Conexión ──────────────────────────────────────────────────────────────

    fun connect() {
        disconnect()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope?.launch { connectLoop() }
    }

    private suspend fun connectLoop() {
        while (coroutineContext.isActive) {
            try {
                val s = LocalSocket()
                s.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))

                // El daemon envía 1 byte carrier + SCM_RIGHTS(shm_fd) al conectar.
                // La APK no necesita el fd (el daemon actualiza la shm directamente).
                // Consumimos el byte para no bloquear el lector de telemetría.
                s.inputStream.read()

                socket = s
                writer = PrintWriter(s.outputStream, true)
                reader = BufferedReader(InputStreamReader(s.inputStream))
                _isConnected.value = true
                Log.i(TAG, "Conectado a omega_daemon via socket abstracto")

                startTelemetryPolling()
                return  // conexión exitosa — salir del loop de retry
            } catch (e: Exception) {
                _isConnected.value = false
                Log.w(TAG, "No se pudo conectar al daemon (${e.message}) — reintento en ${CONNECT_RETRY_MS}ms")
                delay(CONNECT_RETRY_MS)
            }
        }
    }

    fun disconnect() {
        scope?.cancel()
        runCatching {
            writer?.close()
            reader?.close()
            socket?.close()
        }
        socket = null; writer = null; reader = null
        _isConnected.value = false
        Log.d(TAG, "Desconectado de omega_daemon")
    }

    // ── Comandos (protocolo texto igual al que parsea el daemon) ──────────────

    private fun send(cmd: String) {
        if (!_isConnected.value) {
            Log.w(TAG, "Comando descartado (sin conexión): $cmd")
            return
        }
        runCatching {
            writer?.println(cmd)
        }.onFailure {
            Log.e(TAG, "Error enviando '$cmd': ${it.message}")
            _isConnected.value = false
        }
    }

    fun setProcessingState(enabled: Boolean) {
        _isProcessing.value = enabled
        send("SET_PROCESSING:${if (enabled) 1 else 0}")
    }

    fun setVocoderMix(mix: Float) {
        val v = mix.coerceIn(0f, 1f)
        _vocoderMix.value = v
        send("SET_VOCODER_MIX:$v")
    }

    fun setIntensity(level: Float) {
        send("SET_INTENSITY:${level.coerceIn(0f, 1f)}")
    }

    fun setBypass(enabled: Boolean) {
        send("SET_BYPASS:${if (enabled) 1 else 0}")
    }

    fun applyPreset(presetName: String) {
        Log.d(TAG, "Aplicando preset: $presetName")
        send("SET_PRESET:$presetName")
    }

    fun resetToDefaults() {
        send("RESET_DEFAULTS")
    }

    fun setThermalThrottle(enabled: Boolean) {
        send("SET_THERMAL_THROTTLE:${if (enabled) 1 else 0}")
    }

    // ── Telemetría ────────────────────────────────────────────────────────────

    private fun startTelemetryPolling() {
        scope?.launch {
            while (isActive && _isConnected.value) {
                try {
                    send("GET_TELEMETRY")
                    val line = reader?.readLine() ?: break
                    parseTelemetry(line)
                    delay(TELEMETRY_POLL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en polling de telemetría: ${e.message}")
                    _isConnected.value = false
                    break
                }
            }
            // Si se perdió la conexión, relanzar el loop de reconexión
            if (coroutineContext.isActive) {
                _isConnected.value = false
                delay(CONNECT_RETRY_MS)
                connectLoop()
            }
        }
    }

    private fun parseTelemetry(json: String) {
        fun extractFloat(key: String): Float? =
            Regex("\"$key\"\\s*:\\s*([0-9.]+)").find(json)?.groupValues?.get(1)?.toFloatOrNull()
        extractFloat("temp")?.let    { _deviceTemp.value = it }
        extractFloat("latency")?.let { _latencyMs.value  = it }
    }
}
