package com.ruinobrega.fastinator.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ruinobrega.fastinator.model.FastingSession
import com.ruinobrega.fastinator.ui.theme.FastinatorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec §3.1: single Start/Stop button; ring center shows live elapsed HH:MM:SS
 * (the only seconds display) and achievement in 0.00% format.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TodayScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun idleStateShowsStartButtonAndPressingItStartsAFast() {
    var started = false
    composeTestRule.setContent {
      FastinatorTheme {
        TodayScreen(
          activeSession = null,
          lastCompletedSession = null,
          onStartFast = { started = true },
          onStopFast = {},
        )
      }
    }
    composeTestRule.onNodeWithTag("start_stop_button").assertIsDisplayed()
    composeTestRule.onNodeWithText("Start").assertIsDisplayed()
    composeTestRule.onNodeWithTag("start_stop_button").performClick()
    assertTrue("pressing Start must start a fast (spec §2)", started)
  }

  @Test
  fun fastingStateShowsHhMmSsElapsedAndTwoDecimalPercentageAndStop() {
    val fixedNow = 1762160000000L
    val elapsedMillis = (8L * 3600 + 24L * 60) * 1000 // 8h24m
    val active = FastingSession(id = "ui-active", startTimeMillis = fixedNow - elapsedMillis)
    var stopped = false
    composeTestRule.setContent {
      FastinatorTheme {
        TodayScreen(
          activeSession = active,
          lastCompletedSession = null,
          onStartFast = {},
          onStopFast = { stopped = true },
          previewCurrentTimeMillis = fixedNow,
        )
      }
    }
    // 8h24m elapsed -> HH:MM:SS in the ring center, 64.62% achievement (0.00% format)
    composeTestRule.onNodeWithText("08:24:00").assertIsDisplayed()
    composeTestRule.onNodeWithText("64.62%").assertIsDisplayed()
    composeTestRule.onNodeWithText("Stop").assertIsDisplayed()
    composeTestRule.onNodeWithTag("start_stop_button").performClick()
    assertTrue("pressing Stop must end the fast (spec §2)", stopped)
  }

  @Test
  fun idleStateShowsLastSessionSummary() {
    val fixedNow = 1762160000000L
    val last = FastingSession(
      id = "ui-last",
      startTimeMillis = fixedNow - 16L * 3600 * 1000,
      endTimeMillis = fixedNow - 2L * 3600 * 1000, // 14h fast -> 107.69%
    )
    composeTestRule.setContent {
      FastinatorTheme {
        TodayScreen(
          activeSession = null,
          lastCompletedSession = last,
          onStartFast = {},
          onStopFast = {},
          previewCurrentTimeMillis = fixedNow,
        )
      }
    }
    composeTestRule.onNodeWithTag("last_session_summary_card").assertExists()
    composeTestRule.onNodeWithText("14:00").assertExists() // elapsed HH:MM
    composeTestRule.onNodeWithText("107.69%").assertExists()
  }
}
