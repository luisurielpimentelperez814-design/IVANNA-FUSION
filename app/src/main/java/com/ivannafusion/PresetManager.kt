package com.ivannafusion

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.File

private const val PRESETS_DATASTORE = "ivanna_presets"
private val Context.presetsDataStore by preferencesDataStore(name = PRESETS_DATASTORE)

/**
 * Gestor de presets y persistencia de estado.
 * Asegura que los parámetros de audio y configuraciones persistan entre sesiones.
 */
class PresetManager(private val context: Context) {
    companion object {
        private const val TAG = "PresetManager"
        private const val PRESETS_DIR = "presets"
        private val CURRENT_PRESET_KEY = stringPreferencesKey("current_preset")
        private val LAST_PARAMS_KEY = stringPreferencesKey("last_params")
    }

    private val presetsPath = File(context.filesDir, PRESETS_DIR).apply {
        if (!exists()) mkdirs()
    }

    /**
     * Carga un preset y restaura sus parámetros
     */
    fun loadPreset(presetName: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Cargando preset: $presetName")

                // Intentar cargar del motor nativo
                val loaded = IvannaNativeLib.nativeLoadPreset(presetName)
                
                if (loaded) {
                    // Obtener parámetros actuales después de cargar
                    val params = IvannaNativeLib.nativeGetCurrentParams()
                    
                    // Guardar en persistencia
                    savePresistentPreset(presetName, params)
                    
                    Log.d(TAG, "Preset cargado: $presetName")
                    onSuccess()
                } else {
                    // Intentar cargar desde archivo local si nativeLoadPreset falló
                    val localPreset = loadLocalPreset(presetName)
                    if (localPreset != null) {
                        val applied = IvannaNativeLib.nativeSetParams(localPreset)
                        if (applied) {
                            savePresistentPreset(presetName, localPreset)
                            Log.d(TAG, "Preset cargado desde archivo local: $presetName")
                            onSuccess()
                        } else {
                            throw Exception("No se pudo aplicar parámetros del preset local")
                        }
                    } else {
                        throw Exception("Preset no encontrado: $presetName")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando preset", e)
                onError(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Guarda un preset personalizado
     */
    fun savePreset(presetName: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Guardando preset: $presetName")

                // Obtener parámetros actuales
                val params = IvannaNativeLib.nativeGetCurrentParams()

                // Guardar en motor nativo
                val saved = IvannaNativeLib.nativeSavePreset(presetName)
                
                if (saved) {
                    // Guardar localmente como backup
                    saveLocalPreset(presetName, params)
                    
                    // Guardar en persistencia
                    savePresistentPreset(presetName, params)
                    
                    Log.d(TAG, "Preset guardado: $presetName")
                    onSuccess()
                } else {
                    throw Exception("nativeSavePreset retornó false")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando preset", e)
                onError(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Restaura el último preset utilizado
     */
    fun restoreLastPreset(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.presetsDataStore.data.collect { preferences ->
                    val lastPreset = preferences[CURRENT_PRESET_KEY]
                    val lastParams = preferences[LAST_PARAMS_KEY]

                    when {
                        lastPreset != null -> {
                            loadPreset(lastPreset, onSuccess, onError)
                        }
                        lastParams != null -> {
                            Log.d(TAG, "Restaurando parámetros guardados")
                            val applied = IvannaNativeLib.nativeSetParams(lastParams)
                            if (applied) {
                                onSuccess()
                            } else {
                                throw Exception("Error al restaurar parámetros")
                            }
                        }
                        else -> {
                            Log.w(TAG, "No hay preset previo para restaurar")
                            onSuccess()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restaurando preset", e)
                onError(e.message ?: "Error desconocido")
            }
        }
    }

    private suspend fun savePresistentPreset(presetName: String, params: String) {
        context.presetsDataStore.edit { preferences ->
            preferences[CURRENT_PRESET_KEY] = presetName
            preferences[LAST_PARAMS_KEY] = params
        }
    }

    private fun saveLocalPreset(name: String, params: String) {
        try {
            val file = File(presetsPath, "$name.json")
            file.writeText(params)
            Log.d(TAG, "Preset local guardado: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando preset local", e)
        }
    }

    private fun loadLocalPreset(name: String): String? {
        return try {
            val file = File(presetsPath, "$name.json")
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando preset local", e)
            null
        }
    }

    fun listPresetsCollect(block: suspend (List<String>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val localPresets = presetsPath.listFiles()
                ?.filter { it.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
            
            block(localPresets)
        }
    }
}
