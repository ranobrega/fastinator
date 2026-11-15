package com.ruinobrega.fastinator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ruinobrega.fastinator.data.SettingsPreferences
import com.ruinobrega.fastinator.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec §6 (v1.8) + §8.1 (v1.11) + §9: the app follows the system theme by default;
 * an explicit user choice persists in Preferences DataStore and overrides the system
 * theme. The fasting-notification toggle defaults to ON and persists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsPreferencesTest {

  @Test
  fun defaultsToSystemTheme() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = SettingsPreferences.getInstance(context)
    assertEquals(ThemeMode.SYSTEM, prefs.themeModeFlow.first())
  }

  @Test
  fun explicitChoicePersistsAcrossReads() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = SettingsPreferences.getInstance(context)
    prefs.setThemeMode(ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, prefs.themeModeFlow.first())
    prefs.setThemeMode(ThemeMode.LIGHT)
    assertEquals(ThemeMode.LIGHT, prefs.themeModeFlow.first())
    prefs.setThemeMode(ThemeMode.SYSTEM)
    assertEquals(ThemeMode.SYSTEM, prefs.themeModeFlow.first())
  }

  @Test
  fun fastingNotificationToggleDefaultsOn() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = SettingsPreferences.getInstance(context)
    assertTrue(prefs.fastingNotificationEnabledFlow.first())
  }

  @Test
  fun fastingNotificationTogglePersists() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = SettingsPreferences.getInstance(context)
    prefs.setFastingNotificationEnabled(false)
    assertFalse(prefs.fastingNotificationEnabledFlow.first())
    prefs.setFastingNotificationEnabled(true)
    assertTrue(prefs.fastingNotificationEnabledFlow.first())
  }
}
