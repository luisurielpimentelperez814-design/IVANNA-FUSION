package com.ivannafusion.dsp

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Estado persistente real de TODOS los controles de la app, respaldado
 * por SharedPreferences. Antes de esta versión, solo 9 controles
 * (masterVolume, bassBoost, midRange, treble, reverbLevel, delayTime,
 * delayFeedback, compressorThreshold, compressorRatio) persistían
 * aquí — el resto de los ~30 controles de EffectsScreen.kt usaban
 * 'remember { mutableStateOf(...) }' local, que Compose descarta al
 * salir de la pantalla (causa real del bug reportado: "los controles
 * no retienen la configuración, se regresan al cambiar de ventana").
 */
object DSPState {
    private lateinit var prefs: SharedPreferences
    private var isInitialized = false

    // ── Helper genérico: evita repetir 30+ veces el mismo boilerplate
    // de MutableStateFlow + persistencia. Cada control se declara con
    // una sola línea: FloatPref("clave", valorPorDefecto, ::persistSafely).
    //
    // DISEÑO: se recibe la función de persistencia como parámetro
    // (en vez de que la clase llame directamente a un método privado
    // del 'object' contenedor) para evitar cualquier ambigüedad sobre
    // si 'inner class' es válida dentro de un 'object' en Kotlin —
    // documentación oficial confirma 'inner class' dentro de 'class',
    // pero no se encontró confirmación inequívoca para 'object'. Este
    // diseño con clases de nivel superior + lambda es sintaxis Kotlin
    // estándar sin ambigüedad, evitando arriesgar otro ciclo de fallo
    // de compilación en CI por una regla no verificada con certeza.
    class FloatPref(private val key: String, default: Float, private val persist: (String, Float) -> Unit) {
        private val flow = MutableStateFlow(default)
        val state: StateFlow<Float> = flow.asStateFlow()
        fun get(): Float = flow.value
        fun set(v: Float) {
            flow.update { v }
            persist(key, v)
        }
        fun loadFrom(p: SharedPreferences, default: Float) {
            flow.value = p.getFloat(key, default)
        }
    }

    class BoolPref(private val key: String, default: Boolean, private val persist: (String, Boolean) -> Unit) {
        private val flow = MutableStateFlow(default)
        val state: StateFlow<Boolean> = flow.asStateFlow()
        fun get(): Boolean = flow.value
        fun set(v: Boolean) {
            flow.update { v }
            persist(key, v)
        }
        fun loadFrom(p: SharedPreferences, default: Boolean) {
            flow.value = p.getBoolean(key, default)
        }
    }

    class StringPref(private val key: String, default: String, private val persist: (String, String) -> Unit) {
        private val flow = MutableStateFlow(default)
        val state: StateFlow<String> = flow.asStateFlow()
        fun get(): String = flow.value
        fun set(v: String) {
            flow.update { v }
            persist(key, v)
        }
        fun loadFrom(p: SharedPreferences, default: String) {
            flow.value = p.getString(key, default) ?: default
        }
    }

    // ── Controles existentes (ya persistían antes de este cambio) ──────
    private val masterVolumePref = FloatPref("masterVolume", 0.8f, ::persistSafely)
    val masterVolume: StateFlow<Float> = masterVolumePref.state
    private val bassBoostPref = FloatPref("bassBoost", 0f, ::persistSafely)
    val bassBoost: StateFlow<Float> = bassBoostPref.state
    private val midRangePref = FloatPref("midRange", 0f, ::persistSafely)
    val midRange: StateFlow<Float> = midRangePref.state
    private val treblePref = FloatPref("treble", 0f, ::persistSafely)
    val treble: StateFlow<Float> = treblePref.state
    private val reverbLevelPref = FloatPref("reverbLevel", 0.2f, ::persistSafely)
    val reverbLevel: StateFlow<Float> = reverbLevelPref.state
    private val delayTimePref = FloatPref("delayTime", 250f, ::persistSafely)
    val delayTime: StateFlow<Float> = delayTimePref.state
    private val delayFeedbackPref = FloatPref("delayFeedback", 0.3f, ::persistSafely)
    val delayFeedback: StateFlow<Float> = delayFeedbackPref.state
    private val compressorThresholdPref = FloatPref("compressorThreshold", 0.667f, ::persistSafely)
    val compressorThreshold: StateFlow<Float> = compressorThresholdPref.state
    private val compressorRatioPref = FloatPref("compressorRatio", 0.158f, ::persistSafely)
    val compressorRatio: StateFlow<Float> = compressorRatioPref.state

    // ── EQ: 10 bandas + bypass (antes solo vivían en remember local) ──
    private val eqGainPrefs = (0 until 10).map { FloatPref("eqGain_$it", 0.5f, ::persistSafely) }
    val eqGains: List<StateFlow<Float>> = eqGainPrefs.map { it.state }
    private val eqBypassPref = BoolPref("eqBypass", false, ::persistSafelyBool)
    val eqBypass: StateFlow<Boolean> = eqBypassPref.state
    fun setEqGain(band: Int, v: Float) { eqGainPrefs.getOrNull(band)?.set(v) }
    fun getEqGain(band: Int): Float = eqGainPrefs.getOrNull(band)?.get() ?: 0.5f
    fun setEqBypass(v: Boolean) = eqBypassPref.set(v)

    // ── Compresor: campos que faltaban (antes solo threshold/ratio) ───
    private val compressorAttackPref = FloatPref("compressorAttack", 0.1f, ::persistSafely)
    val compressorAttack: StateFlow<Float> = compressorAttackPref.state
    private val compressorReleasePref = FloatPref("compressorRelease", 0.3f, ::persistSafely)
    val compressorRelease: StateFlow<Float> = compressorReleasePref.state
    private val compressorKneePref = FloatPref("compressorKnee", 0.3f, ::persistSafely)
    val compressorKnee: StateFlow<Float> = compressorKneePref.state
    private val compressorMakeupPref = FloatPref("compressorMakeup", 0.0f, ::persistSafely)
    val compressorMakeup: StateFlow<Float> = compressorMakeupPref.state
    private val compressorBypassPref = BoolPref("compressorBypass", false, ::persistSafelyBool)
    val compressorBypass: StateFlow<Boolean> = compressorBypassPref.state
    fun setCompressorAttack(v: Float) = compressorAttackPref.set(v)
    fun setCompressorRelease(v: Float) = compressorReleasePref.set(v)
    fun setCompressorKnee(v: Float) = compressorKneePref.set(v)
    fun setCompressorMakeup(v: Float) = compressorMakeupPref.set(v)
    fun setCompressorBypass(v: Boolean) = compressorBypassPref.set(v)

    // ── Reverb completo (antes solo reverbLevel) ───────────────────────
    private val reverbTypePref = StringPref("reverbType", "HALL", ::persistSafelyString)
    val reverbType: StateFlow<String> = reverbTypePref.state
    private val reverbDecayPref = FloatPref("reverbDecay", 0.4f, ::persistSafely)
    val reverbDecay: StateFlow<Float> = reverbDecayPref.state
    private val reverbPreDelayPref = FloatPref("reverbPreDelay", 0.1f, ::persistSafely)
    val reverbPreDelay: StateFlow<Float> = reverbPreDelayPref.state
    private val reverbDampingPref = FloatPref("reverbDamping", 0.5f, ::persistSafely)
    val reverbDamping: StateFlow<Float> = reverbDampingPref.state
    private val reverbDiffusionPref = FloatPref("reverbDiffusion", 0.7f, ::persistSafely)
    val reverbDiffusion: StateFlow<Float> = reverbDiffusionPref.state
    private val reverbEarlyMixPref = FloatPref("reverbEarlyMix", 0.5f, ::persistSafely)
    val reverbEarlyMix: StateFlow<Float> = reverbEarlyMixPref.state
    private val reverbMixPref = FloatPref("reverbMix", 0.3f, ::persistSafely)
    val reverbMix: StateFlow<Float> = reverbMixPref.state
    fun setReverbType(v: String) = reverbTypePref.set(v)
    fun setReverbDecay(v: Float) = reverbDecayPref.set(v)
    fun setReverbPreDelay(v: Float) = reverbPreDelayPref.set(v)
    fun setReverbDamping(v: Float) = reverbDampingPref.set(v)
    fun setReverbDiffusion(v: Float) = reverbDiffusionPref.set(v)
    fun setReverbEarlyMix(v: Float) = reverbEarlyMixPref.set(v)
    fun setReverbMix(v: Float) = reverbMixPref.set(v)

    // ── Widener / espacial (antes solo en remember local) ──────────────
    private val widenerWidthPref = FloatPref("widenerWidth", 0.5f, ::persistSafely)
    val widenerWidth: StateFlow<Float> = widenerWidthPref.state
    private val widenerDepthPref = FloatPref("widenerDepth", 0.5f, ::persistSafely)
    val widenerDepth: StateFlow<Float> = widenerDepthPref.state
    private val widenerDiffusionPref = FloatPref("widenerDiffusion", 0.3f, ::persistSafely)
    val widenerDiffusion: StateFlow<Float> = widenerDiffusionPref.state
    private val widenerDelayPref = FloatPref("widenerDelay", 0.15f, ::persistSafely)
    val widenerDelay: StateFlow<Float> = widenerDelayPref.state
    private val widenerModRatePref = FloatPref("widenerModRate", 0.5f, ::persistSafely)
    val widenerModRate: StateFlow<Float> = widenerModRatePref.state
    private val widenerMixPref = FloatPref("widenerMix", 1.0f, ::persistSafely)
    val widenerMix: StateFlow<Float> = widenerMixPref.state
    fun setWidenerWidth(v: Float) = widenerWidthPref.set(v)
    fun setWidenerDepth(v: Float) = widenerDepthPref.set(v)
    fun setWidenerDiffusion(v: Float) = widenerDiffusionPref.set(v)
    fun setWidenerDelay(v: Float) = widenerDelayPref.set(v)
    fun setWidenerModRate(v: Float) = widenerModRatePref.set(v)
    fun setWidenerMix(v: Float) = widenerMixPref.set(v)

    // ── Métricas en vivo (no persistidas — siempre el valor más reciente) ──
    private val _rmsLevel = MutableStateFlow(-90f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()
    private val _spectralFlatness = MutableStateFlow(0f)
    val spectralFlatness: StateFlow<Float> = _spectralFlatness.asStateFlow()
    fun updateMetrics(rms: Float, flatness: Float) {
        _rmsLevel.update { rms }
        _spectralFlatness.update { flatness }
    }

    // ── Métricas reales de hardware (sample rate / formato), NO inventadas ──
    // Se consultan al sistema vía AudioManager — si el dispositivo no
    // soporta 192kHz/24-bit en su path de salida estándar (la mayoría
    // de teléfonos Android reportan 48000 o 44100 aquí, sin un DAC USB
    // externo), esto refleja el valor REAL, no un número fijo aspiracional.
    private val _deviceSampleRateHz = MutableStateFlow(48000)
    val deviceSampleRateHz: StateFlow<Int> = _deviceSampleRateHz.asStateFlow()
    private val _deviceFramesPerBuffer = MutableStateFlow(0)
    val deviceFramesPerBuffer: StateFlow<Int> = _deviceFramesPerBuffer.asStateFlow()
    private val _deviceSupportsHighRes = MutableStateFlow(false)
    val deviceSupportsHighRes: StateFlow<Boolean> = _deviceSupportsHighRes.asStateFlow()

    /**
     * Consulta el sample rate y buffer reales que AAudio/AudioManager
     * reportan para este dispositivo específico. "Alta resolución" se
     * marca true solo si el valor real reportado es >= 88200 Hz (umbral
     * estándar de la industria para hi-res audio) — NUNCA se fuerza
     * 192000 si el hardware no lo soporta.
     */
    fun detectRealHardwareCapabilities(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val sr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
            val fpb = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 0
            _deviceSampleRateHz.value = sr
            _deviceFramesPerBuffer.value = fpb
            _deviceSupportsHighRes.value = sr >= 88200
        } catch (e: Exception) {
            android.util.Log.w("DSPState", "No se pudo consultar PROPERTY_OUTPUT_SAMPLE_RATE real: ${e.message}")
        }
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        prefs = context.getSharedPreferences("ivanna_dsp_state", Context.MODE_PRIVATE)
        isInitialized = true
        loadPersistedState()
        detectRealHardwareCapabilities(context)
    }

    private fun loadPersistedState() {
        masterVolumePref.loadFrom(prefs, 0.8f)
        bassBoostPref.loadFrom(prefs, 0f)
        midRangePref.loadFrom(prefs, 0f)
        treblePref.loadFrom(prefs, 0f)
        reverbLevelPref.loadFrom(prefs, 0.2f)
        delayTimePref.loadFrom(prefs, 250f)
        delayFeedbackPref.loadFrom(prefs, 0.3f)
        compressorThresholdPref.loadFrom(prefs, 0.667f)
        compressorRatioPref.loadFrom(prefs, 0.158f)

        eqGainPrefs.forEach { it.loadFrom(prefs, 0.5f) }
        eqBypassPref.loadFrom(prefs, false)

        compressorAttackPref.loadFrom(prefs, 0.1f)
        compressorReleasePref.loadFrom(prefs, 0.3f)
        compressorKneePref.loadFrom(prefs, 0.3f)
        compressorMakeupPref.loadFrom(prefs, 0.0f)
        compressorBypassPref.loadFrom(prefs, false)

        reverbTypePref.loadFrom(prefs, "HALL")
        reverbDecayPref.loadFrom(prefs, 0.4f)
        reverbPreDelayPref.loadFrom(prefs, 0.1f)
        reverbDampingPref.loadFrom(prefs, 0.5f)
        reverbDiffusionPref.loadFrom(prefs, 0.7f)
        reverbEarlyMixPref.loadFrom(prefs, 0.5f)
        reverbMixPref.loadFrom(prefs, 0.3f)

        widenerWidthPref.loadFrom(prefs, 0.5f)
        widenerDepthPref.loadFrom(prefs, 0.5f)
        widenerDiffusionPref.loadFrom(prefs, 0.3f)
        widenerDelayPref.loadFrom(prefs, 0.15f)
        widenerModRatePref.loadFrom(prefs, 0.5f)
        widenerMixPref.loadFrom(prefs, 1.0f)
    }

    /**
     * Persiste un valor solo si DSPState.initialize() ya corrió.
     * CORRECCIÓN DE CRASH (ver historial): antes, los setters llamaban
     * a prefs.edit() directamente sin verificar si 'prefs' (lateinit
     * var) ya estaba inicializado.
     */
    private fun persistSafely(key: String, value: Float) {
        if (!isInitialized) {
            android.util.Log.w("DSPState", "persistSafely('$key'): DSPState.initialize() no se ha llamado todavía, valor no persistido (solo en memoria)")
            return
        }
        prefs.edit().putFloat(key, value).apply()
    }

    private fun persistSafelyBool(key: String, value: Boolean) {
        if (!isInitialized) return
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun persistSafelyString(key: String, value: String) {
        if (!isInitialized) return
        prefs.edit().putString(key, value).apply()
    }

    // ── Setters legacy conservados (compatibilidad con AudioEngine.kt) ──
    fun setMasterVolume(v: Float) = masterVolumePref.set(v)
    fun setBassBoost(v: Float) = bassBoostPref.set(v)
    fun setMidRange(v: Float) = midRangePref.set(v)
    fun setTreble(v: Float) = treblePref.set(v)
    fun setReverbLevel(v: Float) = reverbLevelPref.set(v)
    fun setDelayTime(v: Float) = delayTimePref.set(v)
    fun setDelayFeedback(v: Float) = delayFeedbackPref.set(v)
    fun setCompressorThreshold(v: Float) = compressorThresholdPref.set(v)
    fun setCompressorRatio(v: Float) = compressorRatioPref.set(v)
}
