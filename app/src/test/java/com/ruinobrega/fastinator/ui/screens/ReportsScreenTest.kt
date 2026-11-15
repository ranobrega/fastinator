package com.ruinobrega.fastinator.ui.screens

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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Spec §3.3/§3.4: week/month views with aggregated ring, stats, chart, arrow
 * navigation, and a share action for the latest export.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val zone = ZoneId.systemDefault()

  private fun thisWeekSession(): FastingSession {
    val today = LocalDate.now(zone)
    return FastingSession(
      id = "ui-r1",
      startTimeMillis = today.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(),
      endTimeMillis = today.atTime(21, 0).atZone(zone).toInstant().toEpochMilli(), // 13h -> 100.00%
    )
  }

  @Test
  fun weekViewShowsAggregateRingStatsChartAndShareAction() {
    composeTestRule.setContent {
      FastinatorTheme { ReportsScreen(sessions = listOf(thisWeekSession())) }
    }
    // Content is scrollable — assert existence rather than on-screen visibility
    composeTestRule.onNodeWithTag("reports_screen").assertExists()
    // Aggregated ring removed; the Daily Average text summary remains
    composeTestRule.onNodeWithTag("aggregate_ring_card").assertDoesNotExist()
    composeTestRule.onNodeWithTag("daily_average_card").assertExists()
    composeTestRule.onNodeWithText("Daily Average").assertExists()
    composeTestRule.onNodeWithTag("achievement_barchart_card").assertExists()
    composeTestRule.onNodeWithTag("nav_arrow_back").assertExists()
    composeTestRule.onNodeWithTag("nav_arrow_forward").assertExists()
    // Share JSON action removed from the top-right corner of this screen
    composeTestRule.onNodeWithTag("share_export_button").assertDoesNotExist()
    composeTestRule.onNodeWithText("Avg Achievement %").assertExists()
    // One fasting day (today); skipped = counted days so far this week (Sunday start) minus 1.
    // Computed dynamically so the test is stable on any day of the week.
    val today = LocalDate.now(zone)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val countedDays = ChronoUnit.DAYS.between(weekStart, today) + 1
    composeTestRule.onNodeWithText("1 / ${countedDays - 1}").assertExists()
  }

  @Test
  fun switchingToMonthAndNavigatingChangesTheRangeLabel() {
    composeTestRule.setContent {
      FastinatorTheme { ReportsScreen(sessions = emptyList()) }
    }
    val monthLabel = LocalDate.now(zone).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    composeTestRule.onNodeWithText("Month").performClick()
    composeTestRule.onNodeWithText(monthLabel).assertExists()

    val previousMonth = LocalDate.now(zone).minusMonths(1)
      .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    composeTestRule.onNodeWithTag("nav_arrow_back").performClick()
    composeTestRule.onNodeWithText(previousMonth).assertExists()
  }
}
