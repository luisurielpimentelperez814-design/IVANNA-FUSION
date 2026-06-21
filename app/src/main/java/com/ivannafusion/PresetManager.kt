package com.ivannafusion

import android.content.Context

class PresetManager(context: Context) {
    private val prefs = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
    
    fun getPresets(): List<String> = listOf(
        "Studio Reference", "Bass Boost", "Vocal Clarity", "Live Room",
        "Cinematic", "Electronic", "Acoustic", "Podcast"
    )
    
    fun loadPreset(name: String) { prefs.edit().putString("current", name).apply() }
    fun savePreset(name: String, data: String) { prefs.edit().putString(name, data).apply() }
}
