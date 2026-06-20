package com.ivannafusion

import android.content.Context
import android.util.Log

class PresetManager(private val context: Context) {
    fun loadPreset(presetName: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        try { IvannaNativeLib.loadPreset(presetName); onSuccess() }
        catch (e: Exception) { onError(e.message ?: "Error") }
    }
    fun savePreset(presetName: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        try { IvannaNativeLib.savePreset(presetName); onSuccess() }
        catch (e: Exception) { onError(e.message ?: "Error") }
    }
    fun restoreLastPreset(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) { onSuccess() }
}
