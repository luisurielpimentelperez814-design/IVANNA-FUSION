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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var audioEngine: AudioEngine
    private lateinit var audioCallbackManager: AudioCallbackManager    private lateinit var presetManager: PresetManager

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    ).let { perms ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms + Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "Permisos concedidos: $allGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        audioCallbackManager = AudioCallbackManager(
            getSystemService(AUDIO_SERVICE) as AudioManager
        )
        presetManager = PresetManager(this)
        audioEngine = AudioEngine()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(
                        audioEngine = audioEngine,
                        presetManager = presetManager,
                        onNavigateToPFEngine = { },
                        onNavigateToPresets = { },
                        onNavigateToSettings = { }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            audioEngine.release()
            audioCallbackManager.abandonAudioFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Error en cleanup", e)
        }
    }
}
