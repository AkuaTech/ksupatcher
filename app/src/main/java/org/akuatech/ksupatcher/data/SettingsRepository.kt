package org.akuatech.ksupatcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val lastVersionCheckKey = stringPreferencesKey("last_version_check")
    private val disclaimerAcceptedKey = booleanPreferencesKey("disclaimer_accepted")

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

    val lastVersionCheckFlow: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[lastVersionCheckKey]
    }

    suspend fun setLastVersionCheck(timestamp: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastVersionCheckKey] = timestamp
        }
    }

    val disclaimerAcceptedFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[disclaimerAcceptedKey] ?: false
    }

    suspend fun setDisclaimerAccepted() {
        context.settingsDataStore.edit { prefs ->
            prefs[disclaimerAcceptedKey] = true
        }
    }
}
