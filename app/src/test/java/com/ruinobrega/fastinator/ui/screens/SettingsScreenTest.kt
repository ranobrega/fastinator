package com.ruinobrega.fastinator.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ruinobrega.fastinator.ui.theme.FastinatorTheme
import com.ruinobrega.fastinator.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec §5 (v1.5: no custom Drive backup UI), §3.6 (share latest export),
 * §6 (System/Light/Dark theme selector reporting the user's choice).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun themeSelectorOffersSystemLightDarkAndReportsChoice() {
    var chosen: ThemeMode? = null
    composeTestRule.setContent {
      FastinatorTheme {
        SettingsScreen(currentThemeMode = ThemeMode.SYSTEM, onThemeModeChange = { chosen = it })
      }
    }
    composeTestRule.onNodeWithText("System").assertExists()
    composeTestRule.onNodeWithText("Light").assertExists()
    composeTestRule.onNodeWithText("Dark").performScrollTo().performClick()
    assertEquals(ThemeMode.DARK, chosen)
  }

  @Test
  fun showsDataSafetyAndExportActionsButNoCustomDriveBackup() {
    composeTestRule.setContent {
      FastinatorTheme { SettingsScreen() }
    }
    composeTestRule.onNodeWithText("Data Safety").assertExists()
    composeTestRule.onNodeWithTag("button_share_export").assertExists()
    // The fake Drive backup UI was cut from v1 (spec §5)
    composeTestRule.onNodeWithText("Cloud Backup").assertDoesNotExist()
    composeTestRule.onNodeWithText("Backup now").assertDoesNotExist()
    composeTestRule.onNodeWithText("Restore").assertDoesNotExist()
  }
}
