package com.ruinobrega.fastinator.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ruinobrega.fastinator.model.FastingSession
import com.ruinobrega.fastinator.ui.theme.FastinatorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Spec §3.2: day-grouped log, skipped days, rows with 0.00% achievement, add form
 * defaulting to yesterday 18:00, active session not editable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HistoryScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val zone = ZoneId.systemDefault()

  private fun todaySession(): FastingSession {
    val today = LocalDate.now(zone)
    return FastingSession(
      id = "ui-s1",
      startTimeMillis = today.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(),
      endTimeMillis = today.atTime(20, 0).atZone(zone).toInstant().toEpochMilli(), // 12h
    )
  }

  @Test
  fun rendersSessionRowWithTwoDecimalPercentageAndSkippedDay() {
    val today = LocalDate.now(zone)
    composeTestRule.setContent {
      FastinatorTheme { HistoryScreen(sessions = listOf(todaySession())) }
    }
    composeTestRule.onNodeWithTag("session_row_ui-s1").assertIsDisplayed()
    composeTestRule.onNodeWithText("92.31%").assertIsDisplayed() // 12h / 13h
    composeTestRule.onNodeWithTag("skipped_day_row_${today.minusDays(1)}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_session_fab").assertIsDisplayed()
    composeTestRule.onNodeWithTag("share_export_button").assertIsDisplayed()
  }

  @Test
  fun tappingActiveSessionDoesNotOpenEditForm() {
    // Spec §2/§3.1: a fast ends only via the Start/Stop button, so the active
    // session must not be editable from the log.
    val today = LocalDate.now(zone)
    val active = FastingSession(
      id = "ui-active",
      startTimeMillis = today.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(),
      endTimeMillis = null,
    )
    composeTestRule.setContent {
      FastinatorTheme { HistoryScreen(sessions = listOf(active)) }
    }
    composeTestRule.onNodeWithTag("session_row_ui-active").performClick()
    composeTestRule.onNodeWithTag("session_form_bottom_sheet").assertDoesNotExist()
  }

  @Test
  fun tappingCompletedSessionOpensPreFilledEditForm() {
    composeTestRule.setContent {
      FastinatorTheme { HistoryScreen(sessions = listOf(todaySession())) }
    }
    composeTestRule.onNodeWithTag("session_row_ui-s1").performClick()
    composeTestRule.onNodeWithTag("session_form_bottom_sheet").assertExists()
    composeTestRule.onNodeWithText("Edit Fasting Session").assertExists()
    composeTestRule.onNodeWithText("08:00").assertExists()
    composeTestRule.onNodeWithText("20:00").assertExists()
  }

  @Test
  fun fabOpensAddFormDefaultingToYesterday1800() {
    // Spec §3.2: the add form defaults to yesterday 18:00 for overnight entry.
    composeTestRule.setContent {
      FastinatorTheme { HistoryScreen(sessions = emptyList()) }
    }
    composeTestRule.onNodeWithTag("add_session_fab").performClick()
    composeTestRule.onNodeWithTag("session_form_bottom_sheet").assertExists()
    composeTestRule.onNodeWithText("Add Fasting Session").assertExists()
    composeTestRule.onNodeWithText("18:00").assertExists()
  }
}
