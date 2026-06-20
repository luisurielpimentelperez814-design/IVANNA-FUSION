package com.ivannafusion

import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var audioEngine: AudioEngine
    private lateinit var audioCallbackManager: AudioCallbackManager
    private lateinit var presetManager: PresetManager
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "Inicializando MainActivity...")

        audioCallbackManager = AudioCallbackManager(
            getSystemService(AUDIO_SERVICE) as AudioManager
        )
        presetManager = PresetManager(this)
        audioEngine = AudioEngine()

        setContent {
            IVANNAFusionTheme {
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        var engineState by remember { mutableStateOf("Inicializando...") }
        var audioState by remember { mutableStateOf("Esperando...") }
        var isAudioRunning by remember { mutableStateOf(false) }
        var selectedPreset by remember { mutableStateOf("clean_studio") }
        var presetList by remember { mutableStateOf(listOf("clean_studio", "70s_rock", "psychedelic")) }

        LaunchedEffect(Unit) {
            Log.d(TAG, "LaunchedEffect: Inicializando...")
            
            audioCallbackManager.requestAudioFocus()
            audioCallbackManager.muteUnwantedNoise()
            
            val audioInitialized = audioEngine.initialize()
            if (!audioInitialized) {
                Log.e(TAG, "Error inicializando audio engine")
                engineState = "ERROR: Audio Engine"
                return@LaunchedEffect
            }

            presetManager.restoreLastPreset(
                onSuccess = {
                    Log.d(TAG, "Preset restaurado exitosamente")
                    engineState = "Listo"
                },
                onError = { error ->
                    Log.e(TAG, "Error restaurando preset: $error")
                    engineState = "ERROR: $error"
                }
            )

            while (isAudioRunning) {
                engineState = audioEngine.getEvolutionState()
                audioState = audioCallbackManager.getAudioState()
                delay(500)
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🎵 IVANNA FUSION",
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                StatusCard("Motor de Audio", engineState)
                Spacer(modifier = Modifier.height(12.dp))
                StatusCard("Audio Focus", audioState)

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            audioEngine.startAudioCapture()
                            isAudioRunning = true
                            Log.d(TAG, "Audio iniciado")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isAudioRunning
                    ) {
                        Text("▶ Iniciar")
                    }

                    Button(
                        onClick = {
                            audioEngine.stopAudioCapture()
                            isAudioRunning = false
                            Log.d(TAG, "Audio detenido")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isAudioRunning
                    ) {
                        Text("⏹ Detener")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Presets", fontSize = 18.sp, modifier = Modifier.align(Alignment.Start))
                
                presetList.forEach { preset ->
                    PresetButton(
                        name = preset,
                        isSelected = selectedPreset == preset,
                        onClick = {
                            selectedPreset = preset
                            presetManager.loadPreset(
                                preset,
                                onSuccess = {
                                    Log.d(TAG, "Preset cargado: $preset")
                                    engineState = "Preset: $preset"
                                },
                                onError = { error ->
                                    Log.e(TAG, "Error cargando preset: $error")
                                    engineState = "ERROR: $error"
                                }
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                var customPresetName by remember { mutableStateOf("") }
                TextField(
                    value = customPresetName,
                    onValueChange = { customPresetName = it },
                    label = { Text("Nombre de nuevo preset") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (customPresetName.isNotBlank()) {
                            presetManager.savePreset(
                                customPresetName,
                                onSuccess = {
                                    Log.d(TAG, "Preset guardado: $customPresetName")
                                    presetList = presetList + customPresetName
                                    customPresetName = ""
                                    engineState = "Preset guardado"
                                },
                                onError = { error ->
                                    Log.e(TAG, "Error guardando preset: $error")
                                    engineState = "ERROR: $error"
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("💾 Guardar Preset")
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    "Estado: ${if (isAudioRunning) "🟢 EN EJECUCIÓN" else "🔴 DETENIDO"}",
                    fontSize = 14.sp,
                    color = if (isAudioRunning) Color.Green else Color.Red
                )
            }
        }
    }

    @Composable
    private fun StatusCard(label: String, value: String) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    @Composable
    private fun PresetButton(name: String, isSelected: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(name)
        }
    }

    @Composable
    private fun IVANNAFusionTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF00BCD4),
                secondary = Color(0xFF9C27B0),
                background = Color(0xFF1A1A1A),
                surface = Color(0xFF2A2A2A)
            )
        ) {
            content()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destruyendo MainActivity...")
        
        audioEngine.stopAudioCapture()
        audioEngine.release()
        audioCallbackManager.abandonAudioFocus()
        audioCallbackManager.restoreAudioStreams()
    }
}
