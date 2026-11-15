package com.ruinobrega.fastinator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ruinobrega.fastinator.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Persisted app settings (spec §9: Preferences DataStore for key-value settings).
 * Holds the theme preference (spec §6 / changelog 1.8 — app follows the system
 * theme by default; an explicit user choice persists and overrides it) and the
 * fasting-notification toggle (spec §8.1 / changelog 1.11 — default ON).
 */
class SettingsPreferences private constructor(context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fastinator_settings")
    private val dataStore = context.applicationContext.dataStore

    val themeModeFlow: Flow<ThemeMode> = dataStore.data
        .map { prefs -> prefs[THEME_MODE_KEY]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM }
        .catch { e -> if (e is IOException) emit(ThemeMode.SYSTEM) else throw e }

    val fastingNotificationEnabledFlow: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[FASTING_NOTIFICATION_KEY] ?: true }
        .catch { e -> if (e is IOException) emit(true) else throw e }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }

    suspend fun setFastingNotificationEnabled(enabled: Boolean) {
        dataStore.edit { it[FASTING_NOTIFICATION_KEY] = enabled }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val FASTING_NOTIFICATION_KEY = booleanPreferencesKey("fasting_notification_enabled")

        @Volatile
        private var INSTANCE: SettingsPreferences? = null

        fun getInstance(context: Context): SettingsPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
