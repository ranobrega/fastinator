package com.ruinobrega.fastinator

import com.ruinobrega.fastinator.model.ReportAggregator
import com.ruinobrega.fastinator.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Spec §3.3-§3.5: Sunday-start weeks, calendar months, multi-session day sums,
 * skipped days counting as zero, future days not penalized, best day.
 */
class ReportAggregatorTest {

  private val zone = ZoneId.of("UTC")

  private fun session(id: String, start: String, end: String): Session = Session(
    id = id,
    start = Instant.parse(start),
    end = Instant.parse(end),
  )

  @Test
  fun weekDatesStartOnSundayAndSpanSevenDays() {
    // 2026-11-18 is a Wednesday; the week runs Sunday 2026-11-15 .. Saturday 2026-11-21
    val dates = ReportAggregator.weekDates(LocalDate.of(2026, 11, 18))
    assertEquals(7, dates.size)
    assertEquals(LocalDate.of(2026, 11, 15), dates.first())
    assertEquals(LocalDate.of(2026, 11, 21), dates.last())
  }

  @Test
  fun monthDatesCoverTheWholeCalendarMonth() {
    val dates = ReportAggregator.monthDates(LocalDate.of(2026, 11, 18))
    assertEquals(30, dates.size)
    assertEquals(LocalDate.of(2026, 11, 1), dates.first())
    assertEquals(LocalDate.of(2026, 11, 30), dates.last())
  }

  @Test
  fun multiSessionDaySumsElapsedAgainstFixed13hTarget() {
    val period = ReportAggregator.weekDates(LocalDate.of(2026, 11, 18))
    val sessions = listOf(
      session("a", "2026-11-17T02:00:00Z", "2026-11-17T04:00:00Z"), // 2h
      session("b", "2026-11-17T13:00:00Z", "2026-11-17T16:00:00Z"), // 3h
    )
    val agg = ReportAggregator.aggregate(period, sessions, zone, today = LocalDate.of(2026, 11, 18))

    val day = agg.dayAggregates.first { it.date == LocalDate.of(2026, 11, 17) }
    assertEquals(5 * 3600L, day.totalSeconds)
    assertEquals(5f * 3600f / Session.TARGET_SECONDS.toFloat() * 100f, day.achievementPercentage, 0.001f)
    assertEquals(18000L, agg.totalFastingSeconds)
    assertEquals(LocalDate.of(2026, 11, 17), agg.bestDay?.date)
  }

  @Test
  fun skippedDaysCountAsZeroAndFutureDaysAreNotPenalized() {
    val period = ReportAggregator.weekDates(LocalDate.of(2026, 11, 18)) // Nov 15..21
    val sessions = listOf(session("a", "2026-11-17T02:00:00Z", "2026-11-17T04:00:00Z"))
    val agg = ReportAggregator.aggregate(period, sessions, zone, today = LocalDate.of(2026, 11, 18))

    // Counted days: Nov 15, 16, 17, 18 -> 1 fasting, 3 skipped. Nov 19-21 are future.
    assertEquals(1, agg.fastingDaysCount)
    assertEquals(3, agg.skippedDaysCount)
    // Average daily elapsed over counted days only: 2h / 4 days = 30min
    assertEquals(1800L, agg.averageDailySeconds)
    // Average achievement: one fasting day at 15.38% spread over four counted days
    assertEquals(15.3846f / 4f, agg.averageAchievementPercent, 0.001f)
  }

  @Test
  fun emptyPeriodHasZeroedMetricsAndNoBestDay() {
    val period = ReportAggregator.weekDates(LocalDate.of(2026, 11, 18))
    val agg = ReportAggregator.aggregate(period, emptyList(), zone, today = LocalDate.of(2026, 11, 18))
    assertEquals(0L, agg.totalFastingSeconds)
    assertEquals(0L, agg.averageDailySeconds)
    assertEquals(0f, agg.averageAchievementPercent, 0.0001f)
    assertEquals(0, agg.fastingDaysCount)
    assertEquals(4, agg.skippedDaysCount)
    assertNull(agg.bestDay)
  }

  @Test
  fun groupByDayAssignsOvernightSessionToItsStartDay() {
    val overnight = session("o", "2026-11-17T23:00:00Z", "2026-11-18T09:00:00Z")
    val dates = listOf(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 17))
    val grouped = ReportAggregator.groupByDay(listOf(overnight), dates, zone)
    assertEquals(1, grouped.getValue(LocalDate.of(2026, 11, 17)).size)
    assertTrue(grouped.getValue(LocalDate.of(2026, 11, 18)).isEmpty())
  }
}
