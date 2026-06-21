package com.ivannafusion

import android.content.Context
import android.util.Log

class PresetManager(private val context: Context) {
    companion object {
        private const val TAG = "PresetManager"
        private const val PREFS = "ivanna_presets"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadPreset(name: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            prefs.edit().putString("last_preset", name).apply()
            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Error")
        }
    }

    fun savePreset(name: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            prefs.edit().putString("preset_$name", "data").apply()
            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Error")
        }
    }

    fun restoreLastPreset(onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Error")
        }
    }

    fun getPresetList(): List<String> = listOf("clean_studio", "70s_rock", "psychedelic", "metal_heavy", "jazz_warm")
}
