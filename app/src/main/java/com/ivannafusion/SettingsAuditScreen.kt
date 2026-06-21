package com.ivannafusion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsAuditScreen(audioEngine: AudioEngine) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚙️ Configuración", fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sample Rate: ${AudioEngine.audio_fs_hz} Hz")
                Text("Bit Depth: ${AudioEngine.audio_bit_depth} bits")
                Text("Latencia: ${AudioEngine.audio_latencia_us} μs")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            audioEngine.setPreferredAudioConfig(48000, 16)
        }) {
            Text("Configurar Audio 48kHz/16bit")
        }

        Button(onClick = {
            audioEngine.setPreferredAudioConfig(44100, 24)
        }) {
            Text("Configurar Audio 44.1kHz/24bit")
        }

        Button(onClick = {
            audioEngine.restart()
        }) {
            Text("Reiniciar Motor")
        }
    }
}
