package com.ivannafusion

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Gestor de callbacks de audio y focus.
 * Elimina pitidos no deseados y gestiona correctamente el audio del sistema.
 */
class AudioCallbackManager(private val audioManager: AudioManager) {
    companion object {
        private const val TAG = "AudioCallbackManager"
    }

    private var audioFocusRequest: AudioFocusRequest? = null
    private var isAudioFocusOwned = false

    fun requestAudioFocus(): Boolean {
        return try {
            Log.d(TAG, "Solicitando audio focus...")

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        onAudioFocusChange(focusChange)
                    }
                    .build()

                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                isAudioFocusOwned = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                
                Log.d(TAG, "Audio focus resultado: $result")
                isAudioFocusOwned
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange -> onAudioFocusChange(focusChange) },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                isAudioFocusOwned = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                
                Log.d(TAG, "Audio focus resultado (legacy): $result")
                isAudioFocusOwned
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error solicitando audio focus", e)
            false
        }
    }

    fun abandonAudioFocus() {
        try {
            Log.d(TAG, "Liberando audio focus...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { }
            }

            isAudioFocusOwned = false
            Log.d(TAG, "Audio focus liberado")
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando audio focus", e)
        }
    }

    private fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus ganado")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus perdido transitoriamente")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus perdido - duck")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus perdido permanentemente")
            }
        }
    }

    fun setAudioOutputDevice(device: Int) {
        try {
            Log.d(TAG, "Configurando dispositivo de audio: $device")
            
            audioManager.setSpeakerphoneOn(device == AudioManager.ROUTE_SPEAKER)
            audioManager.setVolumeControlStream(AudioManager.STREAM_MUSIC)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando dispositivo de audio", e)
        }
    }

    fun muteUnwantedNoise() {
        try {
            Log.d(TAG, "Silenciando ruidos no deseados...")
            
            @Suppress("DEPRECATION")
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error silenciando ruidos", e)
        }
    }

    fun restoreAudioStreams() {
        try {
            Log.d(TAG, "Restaurando volumen de streams...")
            
            val maxNotif = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            
            @Suppress("DEPRECATION")
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxNotif / 2, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm / 2, 0)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restaurando streams", e)
        }
    }

    fun getAudioState(): String {
        return try {
            val musicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val focusOwned = if (isAudioFocusOwned) "Sí" else "No"
            
            "Focus: $focusOwned | Vol: $musicVol/$maxMusicVol"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
