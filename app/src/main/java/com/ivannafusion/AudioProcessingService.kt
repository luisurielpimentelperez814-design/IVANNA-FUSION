package com.ivannafusion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

/**
 * Servicio de procesamiento de audio en background.
 * Ejecuta el motor de audio incluso cuando la app no está en foreground.
 */
class AudioProcessingService : Service() {
    companion object {
        private const val TAG = "AudioProcessingService"
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "ivanna_audio_channel"
    }

    private var audioEngine: AudioEngine? = null
    private var audioCallbackManager: AudioCallbackManager? = null
    private lateinit var serviceScope: CoroutineScope
    private var isProcessing = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AudioProcessingService creado")
        
        serviceScope = CoroutineScope(Dispatchers.Default + Job())
        
        // Crear notification channel
        createNotificationChannel()
        
        // Inicializar managers
        audioCallbackManager = AudioCallbackManager(
            getSystemService(AUDIO_SERVICE) as AudioManager
        )
        audioEngine = AudioEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand llamado")
        
        // Mostrar notificación de foreground
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Iniciar procesamiento de audio
        if (!isProcessing) {
            startAudioProcessing()
        }
        
        return START_STICKY
    }

    private fun startAudioProcessing() {
        try {
            Log.d(TAG, "Iniciando procesamiento de audio")
            
            // Solicitar audio focus
            audioCallbackManager?.requestAudioFocus()
            audioCallbackManager?.muteUnwantedNoise()
            
            // Inicializar motor de audio
            if (audioEngine?.initialize() == true) {
                // Iniciar captura
                audioEngine?.startAudioCapture()
                isProcessing = true
                Log.d(TAG, "Procesamiento de audio iniciado")
            } else {
                Log.e(TAG, "Error inicializando audio engine")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en startAudioProcessing", e)
        }
    }

    private fun stopAudioProcessing() {
        try {
            Log.d(TAG, "Deteniendo procesamiento de audio")
            
            audioEngine?.stopAudioCapture()
            audioEngine?.release()
            audioCallbackManager?.abandonAudioFocus()
            audioCallbackManager?.restoreAudioStreams()
            
            isProcessing = false
            Log.d(TAG, "Procesamiento de audio detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error en stopAudioProcessing", e)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IVANNA FUSION")
            .setContentText("Procesando audio...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IVANNA Audio Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación de procesamiento de audio IVANNA FUSION"
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AudioProcessingService destruido")
        
        stopAudioProcessing()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
