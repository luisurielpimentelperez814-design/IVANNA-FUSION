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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.ivannafusion.navigation.AppNavigation
import com.ivannafusion.ui.theme.IVANNATheme

class MainActivity : ComponentActivity() {
    companion object { private const val TAG = "MainActivity" }

    private lateinit var audioEngine: AudioEngine
    private lateinit var audioCallbackManager: AudioCallbackManager
    private lateinit var presetManager: PresetManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permisos: ${permissions.values.all { it }}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        audioCallbackManager = AudioCallbackManager(getSystemService(AUDIO_SERVICE) as AudioManager)
        presetManager = PresetManager(this)
        audioEngine = AudioEngine()

        setContent {
            IVANNATheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        audioEngine = audioEngine,
                        presetManager = presetManager
                    )
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
        val needRequest = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(needRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
    }
}
