package com.ivannafusion

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

class AudioCallbackManager(private val audioManager: AudioManager) {
    companion object {
        private const val TAG = "AudioCallbackManager"
    }

    private var audioFocusRequest: AudioFocusRequest? = null
    private var isAudioFocusOwned = false

    fun requestAudioFocus(): Boolean {
        return try {
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
                isAudioFocusOwned
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange -> onAudioFocusChange(focusChange) },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                isAudioFocusOwned = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                isAudioFocusOwned
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error solicitando audio focus", e)            false
        }
    }

    fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { }
            }
            isAudioFocusOwned = false
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando audio focus", e)
        }
    }

    private fun onAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "Audio focus change: $focusChange")
    }

    fun muteUnwantedNoise() {
        try {
            @Suppress("DEPRECATION")
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error silenciando ruidos", e)
        }
    }

    fun restoreAudioStreams() {
        try {
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
            val focusOwned = if (isAudioFocusOwned) "Sí" else "No"            "Focus: $focusOwned | Vol: $musicVol/$maxMusicVol"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
