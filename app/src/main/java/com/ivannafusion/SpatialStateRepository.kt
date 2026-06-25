package com.ivannafusion

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "spatial_settings")

class SpatialStateRepository(private val context: Context) {
    companion object {
        val MU_KEY = intPreferencesKey("mu")
        val ENABLED_KEY = booleanPreferencesKey("spatial_enabled")
    }

    suspend fun saveMu(mu: Int) {
        context.dataStore.edit { preferences ->
            preferences[MU_KEY] = mu
        }
    }

    suspend fun saveEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLED_KEY] = enabled
        }
    }

    fun getMuFlow(): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[MU_KEY] ?: 500
        }
    }

    fun getEnabledFlow(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[ENABLED_KEY] ?: true
        }
    }
}
