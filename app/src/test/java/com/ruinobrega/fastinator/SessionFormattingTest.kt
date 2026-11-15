package com.ruinobrega.fastinator

import com.ruinobrega.fastinator.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Spec §2 (v1.7 formatting rules) and §7 (java.time / DST safety) for the Session model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionFormattingTest {

  @Test
  fun durationsFormatAsHhMm() {
    assertEquals("00:00", Session.formatDuration(Duration.ZERO))
    assertEquals("13:00", Session.formatDuration(Duration.ofHours(13)))
    assertEquals("14:18", Session.formatDuration(Duration.ofHours(14).plusMinutes(18)))
  }

  @Test
  fun liveRingTimerFormatsAsHhMmSs() {
    // The only place in the app showing seconds (spec §2)
    val d = Duration.ofHours(8).plusMinutes(24).plusSeconds(5)
    assertEquals("08:24:05", Session.formatDurationWithSeconds(d))
    assertEquals("13:00:00", Session.formatDurationWithSeconds(Duration.ofHours(13)))
  }

  @Test
  fun percentagesFormatWithTwoDecimalsEverywhere() {
    assertEquals("0.00%", Session.formatPercentage(0f))
    assertEquals("64.62%", Session.formatPercentage(64.6153f))
    assertEquals("100.00%", Session.formatPercentage(100f))
    assertEquals("119.23%", Session.formatPercentage(119.2307f))
  }

  @Test
  fun clockTimesFormatAsHhMm24h() {
    val clock = Session.formatClockTime(Instant.parse("2026-11-03T11:45:00Z"))
    assertTrue("expected HH:MM, got $clock", clock.matches(Regex("\\d{2}:\\d{2}")))
  }

  @Test
  fun achievementFloatKeepsTwoDecimalsAndCanExceed100() {
    val start = Instant.parse("2026-11-01T00:00:00Z")
    val s = Session(start = start, end = null)
    // 15.5h elapsed -> 119.23%
    val at15h30m = start.plus(Duration.ofHours(15).plusMinutes(30))
    assertEquals(119.23f, s.achievementPercentFloat(at15h30m), 0.001f)
    assertTrue(s.achievementPercentFloat(at15h30m) > 100f)
  }

  @Test
  fun progressFractionClampsToFullRingAtTarget() {
    val start = Instant.parse("2026-11-01T00:00:00Z")
    val s = Session(start = start, end = null)
    assertEquals(0f, s.progressFraction(start), 0.0001f)
    assertEquals(0.5f, s.progressFraction(start.plus(Duration.ofHours(6).plusMinutes(30))), 0.0001f)
    assertEquals(1f, s.progressFraction(start.plus(Duration.ofHours(13))), 0.0001f)
    assertEquals(1f, s.progressFraction(start.plus(Duration.ofHours(20))), 0.0001f)
  }

  @Test
  fun elapsedAcrossDstSpringForwardIsComputedFromInstants() {
    // Europe/Berlin, 2026-03-29 02:00 -> 03:00 (spring forward): wall clock from
    // 2026-03-28 22:00 to 2026-03-29 10:00 is 12h, but the actual elapsed time is 11h.
    val zone = ZoneId.of("Europe/Berlin")
    val start = LocalDateTime.of(2026, 3, 28, 22, 0).atZone(zone).toInstant()
    val end = LocalDateTime.of(2026, 3, 29, 10, 0).atZone(zone).toInstant()
    val s = Session(start = start, end = end)
    assertEquals(Duration.ofHours(11), s.elapsedDuration())
    assertEquals("11:00", s.formattedElapsed())
  }

  @Test
  fun overnightSessionBelongsToStartDayAndKeepsElapsed() {
    val zone = ZoneId.of("UTC")
    val start = LocalDateTime.of(2026, 11, 17, 23, 0).atZone(zone).toInstant()
    val end = LocalDateTime.of(2026, 11, 18, 9, 0).atZone(zone).toInstant()
    val s = Session(start = start, end = end)
    assertEquals(Duration.ofHours(10), s.elapsedDuration())
    assertEquals(
      java.time.LocalDate.of(2026, 11, 17),
      s.start.atZone(zone).toLocalDate(),
    )
  }
}
