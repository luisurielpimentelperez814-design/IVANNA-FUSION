package com.ivannafusion.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ivannafusion.AudioEngine
import com.ivannafusion.ShmManager
import com.ivannafusion.ThermalMonitor
import com.ivannafusion.ui.components.IVANNACard
import com.ivannafusion.ui.components.IVANNAHeader
import com.ivannafusion.ui.components.StatusChip
import com.ivannafusion.ui.theme.AccentCyan
import com.ivannafusion.ui.theme.AccentEmerald
import com.ivannafusion.ui.theme.SignalHot
import com.ivannafusion.ui.theme.TextPrimary
import com.ivannafusion.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(audioEngine: AudioEngine, onBack: () -> Unit) {
    val context = LocalContext.current
    val shmStatus by ShmManager.shmStatus.collectAsState()

    var rmsDb by remember { mutableFloatStateOf(-60f) }
    var correlation by remember { mutableFloatStateOf(1f) }
    var latencyUs by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var fitness by remember { mutableFloatStateOf(0f) }
    var genre by remember { mutableStateOf("—") }
    var bpm by remember { mutableFloatStateOf(0f) }
    var kalmanPhase by remember { mutableFloatStateOf(0f) }
    var kalmanFreq by remember { mutableFloatStateOf(0f) }
    var seqCounter by remember { mutableLongStateOf(0L) }
    var activeBuffer by remember { mutableIntStateOf(0) }
    var maxTemp by remember { mutableIntStateOf(0) }

    LaunchedEffect(audioEngine) {
        while (true) {
            ShmManager.refreshCanonicalVars()
            rmsDb = audioEngine.aiGetRmsDb()
            correlation = audioEngine.getCorrelation()
            latencyUs = audioEngine.getLatencyMicros()
            generation = audioEngine.getGeneration()
            fitness = audioEngine.getBestFitness()
            genre = audioEngine.aiGetDetectedGenre()
            bpm = audioEngine.aiGetTempo()
            kalmanPhase = ShmManager.kalman_fase_rad
            kalmanFreq = ShmManager.kalman_frec_hz
            seqCounter = ShmManager.shm_seq_counter
            activeBuffer = ShmManager.shm_buffer_activo
            maxTemp = ThermalMonitor.getMaxTemperature().toInt()
            delay(250)
        }
    }

    fun exportSnapshot() {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val out = JSONObject().apply {
                put("timestamp", stamp)
                put("shm_status", shmStatus)
                put("rms_db", rmsDb)
                put("correlation", correlation)
                put("latency_us", latencyUs)
                put("generation", generation)
                put("fitness", fitness)
                put("genre", genre)
                put("bpm", bpm)
                put("kalman_phase_rad", kalmanPhase)
                put("kalman_freq_hz", kalmanFreq)
                put("seq_counter", seqCounter)
                put("active_buffer", activeBuffer)
                put("temp_cpu0_c", ThermalMonitor.temp_cpu_core0)
                put("temp_gpu_c", ThermalMonitor.temp_gpu)
                put("temp_npu_c", ThermalMonitor.temp_npu)
                put("temp_pmic_c", ThermalMonitor.temp_pmic)
                put("sched_core_hint", ThermalMonitor.sched_nucleo_activo)
                put("sched_budget_mw", ThermalMonitor.sched_budget_mW)
                put("sched_throttle_predicho", ThermalMonitor.sched_throttle_predicho)
            }
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, "ivanna_diagnostics_$stamp.json")
            file.writeText(out.toString(2))
            Toast.makeText(context, "Diagnóstico exportado: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        IVANNAHeader(
            title = "MONITOR AVANZADO",
            subtitle = "SHM, telemetría térmica y estado del motor"
        ) {
            IconButton(onClick = { exportSnapshot() }) {
                Icon(Icons.Default.Save, contentDescription = "Exportar", tint = AccentCyan)
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = AccentCyan)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IVANNACard(title = "Estado general") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(
                        text = if (ShmManager.shmInitialized) "SHM ACTIVA" else "SHM OFF",
                        color = if (ShmManager.shmInitialized) AccentEmerald else SignalHot
                    )
                    StatusChip(
                        text = if (maxTemp >= 42) "TÉRMICA ALTA" else "TÉRMICA OK",
                        color = if (maxTemp >= 42) SignalHot else AccentEmerald
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("$shmStatus", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            IVANNACard(title = "Motor de audio") {
                MetricRow("RMS", "%.1f dBFS".format(rmsDb))
                MetricRow("Correlación estéreo", "%.3f".format(correlation))
                MetricRow("Latencia", "${latencyUs} µs")
                MetricRow("Generación evolutiva", "$generation")
                MetricRow("Fitness", "%.4f".format(fitness))
                MetricRow("Género detectado", genre)
                MetricRow("Tempo estimado", if (bpm > 0f) "%.1f BPM".format(bpm) else "—")
            }

            IVANNACard(title = "Plano compartido / SHM") {
                MetricRow("Kalman fase", "%.4f rad".format(kalmanPhase))
                MetricRow("Kalman frecuencia", "%.2f Hz".format(kalmanFreq))
                MetricRow("Secuencia", "$seqCounter")
                MetricRow("Buffer activo", "$activeBuffer")
            }

            IVANNACard(title = "Térmica y scheduler") {
                MetricRow("CPU0", "${ThermalMonitor.temp_cpu_core0} °C")
                MetricRow("GPU", "${ThermalMonitor.temp_gpu} °C")
                MetricRow("NPU", "${ThermalMonitor.temp_npu} °C")
                MetricRow("PMIC", "${ThermalMonitor.temp_pmic} °C")
                MetricRow("Máxima", "$maxTemp °C")
                MetricRow("Núcleos sugeridos", "${ThermalMonitor.sched_nucleo_activo}")
                MetricRow("Budget térmico", "${ThermalMonitor.sched_budget_mW} mW")
                MetricRow(
                    "Throttle predicho",
                    if (ThermalMonitor.sched_throttle_predicho) "sí" else "no"
                )
            }

            Button(onClick = { exportSnapshot() }, modifier = Modifier.fillMaxWidth()) {
                Text("EXPORTAR SNAPSHOT")
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
    Spacer(Modifier.height(6.dp))
}
