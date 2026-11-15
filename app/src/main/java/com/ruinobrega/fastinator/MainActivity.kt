package com.ruinobrega.fastinator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.ruinobrega.fastinator.data.SettingsPreferences
import com.ruinobrega.fastinator.ui.FastinatorApp
import com.ruinobrega.fastinator.ui.theme.FastinatorTheme
import com.ruinobrega.fastinator.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsPreferences.getInstance(applicationContext)
        setContent {
            // Spec §6 (v1.8): follow the system theme by default; an explicit user
            // choice is persisted in DataStore and overrides the system theme.
            val currentThemeMode by settings.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            // Spec §8.1 (v1.11): fasting-notification toggle, default ON, persisted.
            val notificationsEnabled by settings.fastingNotificationEnabledFlow.collectAsState(initial = true)
            FastinatorTheme(themeMode = currentThemeMode) {
                FastinatorApp(
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = { mode ->
                        lifecycleScope.launch { settings.setThemeMode(mode) }
                    },
                    notificationsEnabled = notificationsEnabled,
                    onNotificationsEnabledChange = { enabled ->
                        lifecycleScope.launch { settings.setFastingNotificationEnabled(enabled) }
                    }
                )
            }
        }
    }
}
