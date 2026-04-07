package org.akuatech.ksupatcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(
    private val context: Context
) {
    private val rootStatusKey = stringPreferencesKey("root_status")
    private val kmiKey = stringPreferencesKey("kmi_version")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val rootStatusFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[rootStatusKey] ?: "UNKNOWN"
    }

    suspend fun setRootStatus(status: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[rootStatusKey] = status
        }
    }

    val kmiFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[kmiKey] ?: "android12-5.10"
    }

    suspend fun setKmi(kmi: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[kmiKey] = kmi
        }
    }

    val themeModeFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[themeModeKey] ?: "auto"
    }

    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[themeModeKey] = mode
        }
    }
}
