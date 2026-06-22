package com.ivannafusion.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ivanna_params")

class ParameterStore(private val context: Context) {
    
    object Keys {
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val COMP_THRESHOLD = floatPreferencesKey("comp_threshold")
        val COMP_RATIO = floatPreferencesKey("comp_ratio")
        val COMP_ATTACK = floatPreferencesKey("comp_attack")
        val COMP_RELEASE = floatPreferencesKey("comp_release")
        val COMP_ENABLED = booleanPreferencesKey("comp_enabled")
        val EXC_DRIVE = floatPreferencesKey("exc_drive")
        val EXC_MIX = floatPreferencesKey("exc_mix")
        val EXC_ENABLED = booleanPreferencesKey("exc_enabled")
        val SPATIAL_WIDTH = floatPreferencesKey("spatial_width")
        val SPATIAL_DEPTH = floatPreferencesKey("spatial_depth")
        val SPATIAL_ENABLED = booleanPreferencesKey("spatial_enabled")
        val CONV_TYPE = stringPreferencesKey("conv_type")
        val CONV_DECAY = floatPreferencesKey("conv_decay")
        val CONV_MIX = floatPreferencesKey("conv_mix")
        val CONV_ENABLED = booleanPreferencesKey("conv_enabled")
        val AI_INTENSITY = floatPreferencesKey("ai_intensity")
        val AI_MODE = stringPreferencesKey("ai_mode")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val GLOBAL_BYPASS = booleanPreferencesKey("global_bypass")
        val CURRENT_PRESET = stringPreferencesKey("current_preset")
    }

    suspend fun saveEQBands(bands: List<Map<String, Float>>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EQ_BANDS] = bands.joinToString(";") { band ->
                "${band["freq"]},${band["gain"]},${band["q"]},${band["enabled"]}"
            }
        }
    }

    fun getEQBands(): Flow<List<Map<String, Float>>> = context.dataStore.data.map { prefs ->
        val bandsStr = prefs[Keys.EQ_BANDS] ?: ""
        if (bandsStr.isEmpty()) {
            listOf(
                mapOf("freq" to 31f, "gain" to 0f, "q" to 1.4f, "enabled" to 1f),
                mapOf("freq" to 62f, "gain" to 0f, "q" to 1.4f, "enabled" to 1f),
                mapOf("freq" to 125f, "gain" to 0f, "q" to 1.4f, "enabled" to 1f),
                mapOf("freq" to 250f, "gain" to 0f, "q" to 1.4f, "enabled" to 1f),
                mapOf("freq" to 500f, "gain" to 0f, "q" to 1.4f, "enabled" to 1f),
                mapOf("freq" to 1000f, "gain" to 0f, "q" to 1.4f, "enabled" to 1f),
                mapOf("freq" to 2000f, "gain" to 0f, "q" to 1.4f, "enabled" to 1f),
                mapOf("freq" to 4000f, "gain" to 0f, "q" to 1.4f, "enabled" to 1f),
                mapOf("freq" to 8000f, "gain" to 0f, "q" to 1.4f, "enabled" to 1f),
                mapOf("freq" to 16000f, "gain" to 0f, "q" to 1.4f, "enabled" to 1f)
            )
        } else {
            bandsStr.split(";").map { band ->
                val parts = band.split(",")
                mapOf(
                    "freq" to parts[0].toFloat(),
                    "gain" to parts[1].toFloat(),
                    "q" to parts[2].toFloat(),
                    "enabled" to parts[3].toFloat()
                )
            }
        }
    }

    suspend fun saveCompressor(threshold: Float, ratio: Float, attack: Float, release: Float, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COMP_THRESHOLD] = threshold
            prefs[Keys.COMP_RATIO] = ratio
            prefs[Keys.COMP_ATTACK] = attack
            prefs[Keys.COMP_RELEASE] = release
            prefs[Keys.COMP_ENABLED] = enabled
        }
    }

    fun getCompressor(): Flow<Map<String, Any>> = context.dataStore.data.map { prefs ->
        mapOf(
            "threshold" to (prefs[Keys.COMP_THRESHOLD] ?: -20f),
            "ratio" to (prefs[Keys.COMP_RATIO] ?: 4f),
            "attack" to (prefs[Keys.COMP_ATTACK] ?: 10f),
            "release" to (prefs[Keys.COMP_RELEASE] ?: 100f),
            "enabled" to (prefs[Keys.COMP_ENABLED] ?: true)
        )
    }

    suspend fun saveExciter(drive: Float, mix: Float, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EXC_DRIVE] = drive
            prefs[Keys.EXC_MIX] = mix
            prefs[Keys.EXC_ENABLED] = enabled
        }
    }

    fun getExciter(): Flow<Map<String, Any>> = context.dataStore.data.map { prefs ->
        mapOf(
            "drive" to (prefs[Keys.EXC_DRIVE] ?: 0.5f),
            "mix" to (prefs[Keys.EXC_MIX] ?: 0.3f),
            "enabled" to (prefs[Keys.EXC_ENABLED] ?: true)
        )
    }

    suspend fun saveAI(intensity: Float, mode: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_INTENSITY] = intensity
            prefs[Keys.AI_MODE] = mode
            prefs[Keys.AI_ENABLED] = enabled
        }
    }

    fun getAI(): Flow<Map<String, Any>> = context.dataStore.data.map { prefs ->
        mapOf(
            "intensity" to (prefs[Keys.AI_INTENSITY] ?: 0.7f),
            "mode" to (prefs[Keys.AI_MODE] ?: "adaptive"),
            "enabled" to (prefs[Keys.AI_ENABLED] ?: true)
        )
    }

    suspend fun saveGlobal(volume: Float, bypass: Boolean, preset: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MASTER_VOLUME] = volume
            prefs[Keys.GLOBAL_BYPASS] = bypass
            prefs[Keys.CURRENT_PRESET] = preset
        }
    }

    fun getGlobal(): Flow<Map<String, Any>> = context.dataStore.data.map { prefs ->
        mapOf(
            "volume" to (prefs[Keys.MASTER_VOLUME] ?: 1.0f),
            "bypass" to (prefs[Keys.GLOBAL_BYPASS] ?: false),
            "preset" to (prefs[Keys.CURRENT_PRESET] ?: "default")
        )
    }
}
