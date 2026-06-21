package com.ivannafusion

import android.content.Context
import android.util.Log

class PresetManager(private val context: Context) {
    companion object {
        private const val TAG = "PresetManager"
        private const val PREFS_NAME = "ivanna_presets"
        private const val KEY_LAST_PRESET = "last_preset"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPreset(name: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            Log.d(TAG, "Cargando preset: $name")
            prefs.edit().putString(KEY_LAST_PRESET, name).apply()
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando preset", e)
            onError(e.message ?: "Error desconocido")
        }
    }

    fun savePreset(name: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            Log.d(TAG, "Guardando preset: $name")
            prefs.edit().putString("preset_$name", "data").apply()
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando preset", e)
            onError(e.message ?: "Error desconocido")
        }
    }

    fun restoreLastPreset(onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val lastPreset = prefs.getString(KEY_LAST_PRESET, "clean_studio")
            Log.d(TAG, "Restaurando último preset: $lastPreset")            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error restaurando preset", e)
            onError(e.message ?: "Error desconocido")
        }
    }
}
