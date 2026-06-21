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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.ivannafusion.navigation.AppNavigation
import com.ivannafusion.ui.theme.IVANNAFusionTheme

class MainActivity : ComponentActivity() {
    companion object { private const val TAG = "MainActivity" }

    private lateinit var audioEngine: AudioEngine
    private lateinit var audioCallbackManager: AudioCallbackManager
    private lateinit var presetManager: PresetManager
    
    // Estado de permisos
    private var permissionsGranted by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionsGranted = allGranted
        Log.d(TAG, "Permisos concedidos: $allGranted")
        
        if (allGranted) {
            // Inicializar solo después de que los permisos sean concedidos
            initializeComponents()
        } else {
            Log.w(TAG, "Permisos denegados - la app funcionará con funcionalidad limitada")
            // Aún así inicializar componentes pero sin captura de audio
            initializeComponents()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // NO llamar checkPermissions() aquí todavía
        // Primero configurar la UI
        
        // Inicializar componentes que NO requieren permisos
        presetManager = PresetManager(this)
        audioEngine = AudioEngine()
        audioCallbackManager = AudioCallbackManager(getSystemService(AUDIO_SERVICE) as AudioManager)
        
        // Verificar permisos de forma segura
        permissionsGranted = hasAllPermissions()
        
        setContent {
            IVANNAFusionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // UI principal
                    AppNavigation(audioEngine = audioEngine, presetManager = presetManager)
                    
                    // Solicitar permisos DESPUÉS de que la UI esté lista
                    LaunchedEffect(Unit) {
                        if (!permissionsGranted) {
                            // Pequeño delay para asegurar que la UI está completamente renderizada
                            kotlinx.coroutines.delay(500)
                            checkPermissions()
                        } else {
                            // Ya tenemos permisos, inicializar componentes
                            initializeComponents()
                        }
                    }
                }
            }
        }
    }
    
    private fun hasAllPermissions(): Boolean {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return perms.all { 
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
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
                // Si falla, continuar sin permisos
                initializeComponents()
            }
        } else {
            // Ya tenemos todos los permisos
            permissionsGranted = true
            initializeComponents()
        }
    }
    
    private fun initializeComponents() {
        Log.d(TAG, "Inicializando componentes...")
        try {
            // Solicitar focus de audio
            audioCallbackManager.requestAudioFocus()
            
            // Inicializar el motor de audio en background
            audioEngine.initialize(this) { success ->
                Log.d(TAG, "AudioEngine inicializado: $success")
            }
            
            // Silenciar ruidos no deseados
            audioCallbackManager.muteUnwantedNoise()
            
            Log.d(TAG, "Componentes inicializados correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando componentes", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            audioEngine.release()
            audioCallbackManager.abandonAudioFocus()
            audioCallbackManager.restoreAudioStreams()
        } catch (e: Exception) { 
            Log.e(TAG, "Cleanup error", e) 
        }
    }
}
