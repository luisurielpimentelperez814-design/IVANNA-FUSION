package com.ivannafusion

import android.media.AudioManager
import android.util.Log

class AudioCallbackManager(private val audioManager: AudioManager) {
    private var isAudioFocusOwned = false
    fun requestAudioFocus(): Boolean { isAudioFocusOwned = true; return true }
    fun abandonAudioFocus() { isAudioFocusOwned = false }
    fun muteUnwantedNoise() {}
    fun restoreAudioStreams() {}
    fun getAudioState(): String = if (isAudioFocusOwned) "Focus: Sí" else "Focus: No"
}
