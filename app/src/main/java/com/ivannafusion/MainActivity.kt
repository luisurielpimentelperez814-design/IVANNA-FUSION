package com.ivannafusion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.ivannafusion.navigation.AppNavigation
import com.ivannafusion.ui.theme.IVANNAFusionTheme
import kotlinx.coroutines.delay
import com.ivannafusion.ai.TrainingWorker

class MainActivity : ComponentActivity() {
    companion object { private const val TAG = "MainActivity" }

    private var audioEngine: AudioEngine? = null
    private var audioCallbackManager: AudioCallbackManager? = null
    private var presetManager: PresetManager? = null
    
    // Estado de inicialización
    private var appState by mutableStateOf(AppState.LOADING)

    // Estado real de la captura de audio interno (Spotify/YouTube/etc),
    // expuesto para que AIScreen pueda mostrarlo y ofrecer el botón de
    // activar/desactivar sin que la pantalla conozca MediaProjection.
    var isPlaybackCaptureActive by mutableStateOf(false)
        private set

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, PlaybackCaptureService::class.java).apply {
                action = PlaybackCaptureService.ACTION_START
                putExtra(PlaybackCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(PlaybackCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(serviceIntent)
            isPlaybackCaptureActive = true
            Log.i(TAG, "Captura de audio interno autorizada e iniciada")
        } else {
            Log.w(TAG, "Usuario denegó el permiso de captura de audio interno")
            isPlaybackCaptureActive = false
        }
    }

    /**
     * Inicia el flujo real de AudioPlaybackCapture: muestra el diálogo
     * del sistema ("Comenzar a grabar/transmitir"), y si el usuario lo
     * acepta, arranca PlaybackCaptureService. Este diálogo lo controla
     * Android, no se puede omitir ni mostrar de forma distinta.
     */
    fun requestPlaybackCapture() {
        bindPlaybackCaptureCallback()
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    /**
     * NOTA: PlaybackCaptureService corre en un proceso/Service distinto
     * del ciclo de vida directo de la Activity, así que su callback
     * onAudioCaptured se conecta aquí, justo después de que el usuario
     * aprueba el permiso e inicia el servicio — audioEngine ya debe
     * existir en ese punto (initializeComponents() corrió antes,
     * porque la captura solo se ofrece desde AIScreen, que requiere
     * AppState.READY).
     */
    private fun bindPlaybackCaptureCallback() {
        // El binding real (instancia de PlaybackCaptureService) requeriría
        // bindService(); para mantenerlo simple, AudioEngine se conecta
        // como companion-level callback estático en el propio servicio.
        PlaybackCaptureService.globalAudioCallback = { mono ->
            audioEngine?.feedExternalMonoAudio(mono, PlaybackCaptureService.CAPTURE_SAMPLE_RATE)
        }
    }

    fun stopPlaybackCapture() {
        val serviceIntent = Intent(this, PlaybackCaptureService::class.java).apply {
            action = PlaybackCaptureService.ACTION_STOP
        }
        startService(serviceIntent)
        isPlaybackCaptureActive = false
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "Permisos concedidos: $allGranted")
        
        // Inicializar componentes DESPUÉS de recibir la respuesta de permisos
        if (allGranted) {
            initializeComponents()
        } else {
            Log.w(TAG, "Permisos denegados - funcionalidad limitada")
            initializeComponentsLimited()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            IVANNAFusionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (appState) {
                        AppState.LOADING -> {
                            // Pantalla de carga mientras se solicitan permisos
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            
                            // Solicitar permisos DESPUÉS de que la UI esté lista
                            LaunchedEffect(Unit) {
                                delay(300) // Pequeño delay para asegurar que la UI está renderizada
                                checkPermissions()
                            }
                        }
                        AppState.READY -> {
                            val engine = audioEngine
                            val presets = presetManager
                            if (engine != null && presets != null) {
                                AppNavigation(audioEngine = engine, presetManager = presets)
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun checkPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val toRequest = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            try {
                requestPermissionLauncher.launch(toRequest.toTypedArray())
            } catch (e: Exception) {
                Log.e(TAG, "Error lanzando diálogo de permisos", e)
                initializeComponentsLimited()
            }
        } else {
            // Ya tenemos todos los permisos
            initializeComponents()
        }
    }
    
    private fun initializeComponents() {
        Log.d(TAG, "Inicializando componentes (permisos completos)...")
        try {
            // Crear instancias LAZY aquí, no en onCreate
            if (audioEngine == null) {
                audioEngine = AudioEngine()
            }
            if (presetManager == null) {
                presetManager = PresetManager(this)
            }
            if (audioCallbackManager == null) {
                audioCallbackManager = AudioCallbackManager(getSystemService(AUDIO_SERVICE) as AudioManager)
            }
            
            // Solicitar focus de audio
            audioCallbackManager?.requestAudioFocus()
            
            // Inicializar el motor de audio en background
            audioEngine?.initialize(this) { success ->
                Log.d(TAG, "AudioEngine inicializado: $success")
            }
            
            // Silenciar ruidos no deseados
            audioCallbackManager?.muteUnwantedNoise()
            
            // Marcar como listo
            appState = AppState.READY
            
            // Programar entrenamiento periódico de IA
            TrainingWorker.schedule(this@MainActivity)
            Log.i(TAG, "Training Worker programado para ejecutarse cada 6 horas")
            Log.d(TAG, "Componentes inicializados correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando componentes", e)
            initializeComponentsLimited()
        }
    }
    
    private fun initializeComponentsLimited() {
        Log.d(TAG, "Inicializando componentes (funcionalidad limitada)...")
        try {
            if (audioEngine == null) {
                audioEngine = AudioEngine()
            }
            if (presetManager == null) {
                presetManager = PresetManager(this)
            }
            if (audioCallbackManager == null) {
                audioCallbackManager = AudioCallbackManager(getSystemService(AUDIO_SERVICE) as AudioManager)
            }
            
            // Marcar como listo incluso sin permisos completos
            appState = AppState.READY
        } catch (e: Exception) {
            Log.e(TAG, "Error en inicialización limitada", e)
            // Forzar ready incluso con error para no dejar la app colgada
            appState = AppState.READY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            audioEngine?.release()
            audioCallbackManager?.abandonAudioFocus()
            audioCallbackManager?.restoreAudioStreams()
        } catch (e: Exception) { 
            Log.e(TAG, "Cleanup error", e) 
        }
    }
}

enum class AppState {
    LOADING,
    READY
}
