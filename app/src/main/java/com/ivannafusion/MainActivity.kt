package com.ivannafusion

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
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
