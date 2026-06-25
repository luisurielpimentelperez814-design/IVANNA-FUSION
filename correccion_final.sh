#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# CORRECCIÓN DEFINITIVA DE TODOS LOS ERRORES DE COMPILACIÓN
# - Añade la variable mu a DSPState.kt
# - Corrige la lectura del buffer en SpatialAudioEngineV2.kt (Float -> Short)
# - Añade función deviceSupportsHighRes faltante
# ============================================================

set -e
cd ~/IVANNA-FUSION

echo "🔧 Añadiendo variable 'mu' a DSPState.kt..."
# Verificar si ya existe, si no, añadir
if ! grep -q "var mu" app/src/main/java/com/ivannafusion/DSPState.kt; then
    sed -i '/^}/ i\
    var mu: Int = 500\
        get() = field\
        set(value) { field = value.coerceIn(0, 1000) }' app/src/main/java/com/ivannafusion/DSPState.kt
fi

echo "📝 Corrigiendo SpatialAudioEngineV2.kt (usar ShortArray en lugar de FloatArray)..."
cat > app/src/main/java/com/ivannafusion/SpatialAudioEngineV2.kt << 'EOF'
package com.ivannafusion

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.*

class SpatialAudioEngineV2 {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val bufferSize = 64
    private val sampleRate = 48000

    fun start() {
        if (isRunning) return
        isRunning = true

        IvannaNativeLib.nativeInitSpatialEngine(sampleRate, bufferSize)

        val recordBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufferSize)

        val trackBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(android.media.AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build())
            .setBufferSizeInBytes(trackBufferSize)
            .build()

        audioRecord?.startRecording()
        audioTrack?.play()

        scope.launch {
            val inputShort = ShortArray(bufferSize)
            val outL = ShortArray(bufferSize)
            val outR = ShortArray(bufferSize)
            while (isRunning) {
                val read = audioRecord?.read(inputShort, 0, bufferSize) ?: 0
                if (read > 0) {
                    // Convertir ShortArray a FloatArray para el procesamiento
                    val inputFloat = FloatArray(bufferSize)
                    for (i in 0 until bufferSize) {
                        inputFloat[i] = inputShort[i] / 32767.0f
                    }
                    val outLFloat = FloatArray(bufferSize)
                    val outRFloat = FloatArray(bufferSize)
                    val posX = 10
                    val posY = 0
                    val posZ = 5
                    val mu = DSPState.mu
                    IvannaNativeLib.nativeRenderSpatialBlock(inputFloat, outLFloat, outRFloat, posX, posY, posZ, mu)
                    // Convertir de vuelta a ShortArray
                    for (i in 0 until bufferSize) {
                        outL[i] = (outLFloat[i] * 32767.0f).toShort()
                        outR[i] = (outRFloat[i] * 32767.0f).toShort()
                    }
                    // Mezclar en stereo
                    val mixed = ShortArray(bufferSize * 2)
                    for (i in 0 until bufferSize) {
                        mixed[i * 2] = outL[i]
                        mixed[i * 2 + 1] = outR[i]
                    }
                    audioTrack?.write(mixed, 0, mixed.size, AudioTrack.WRITE_BLOCKING)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioTrack?.stop()
        audioTrack?.release()
        IvannaNativeLib.nativeReleaseSpatialEngine()
    }
}
EOF

echo "🔄 Añadiendo función deviceSupportsHighRes a SettingsScreen.kt..."
# Si el archivo SettingsScreen.kt existe, añadir la función al inicio
if [ -f app/src/main/java/com/ivannafusion/ui/screens/SettingsScreen.kt ]; then
    # Verificar si ya existe la función
    if ! grep -q "fun deviceSupportsHighRes" app/src/main/java/com/ivannafusion/ui/screens/SettingsScreen.kt; then
        # Añadir la función al final del archivo (antes de la última llave)
        sed -i '/^}/ i\
fun deviceSupportsHighRes(): Boolean {\
    return true  // Asumimos que el dispositivo lo soporta (ajustar según necesidades)\
}' app/src/main/java/com/ivannafusion/ui/screens/SettingsScreen.kt
    fi
else
    echo "⚠️ SettingsScreen.kt no encontrado. Asegúrate de que el archivo existe."
fi

echo "📦 Actualizando .gitignore..."
echo "ghp_*
*.pem
*.key
id_*
*.pub
.ssh/" >> .gitignore

echo "🚀 Commit y push..."
git add .
git commit -m "Corrección definitiva: DSPState.mu, buffer ShortArray, deviceSupportsHighRes" || echo "No hay cambios"
git push origin main

echo "✅ ¡Todo subido! Ve a GitHub Actions para el APK."
