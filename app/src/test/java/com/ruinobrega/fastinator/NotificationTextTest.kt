package com.ruinobrega.fastinator

import com.ruinobrega.fastinator.model.Session
import com.ruinobrega.fastinator.notification.notificationText
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec §8.1 (v1.11): the persistent fasting notification body must show the live
 * elapsed time in HH:MM (never seconds — no chronometer) and the achievement in
 * 0.00% format, e.g. "Elapsed 05:12 · 40.00%".
 */
class NotificationTextTest {

  @Test
  fun notificationTextUsesHhMmAndTwoDecimalPercent() {
    val start = Instant.parse("2026-11-14T20:00:00Z")
    val session = Session(start = start, end = null)
    // 5h12m elapsed; target is 13h -> 5.2 / 13 = 40.00%
    val now = start.toEpochMilli() + (5 * 3600 + 12 * 60) * 1000L
    assertEquals("Elapsed 05:12 · 40.00%", notificationText(session, now))
  }

  @Test
  fun notificationTextNeverShowsSeconds() {
    val start = Instant.parse("2026-11-14T20:00:00Z")
    val session = Session(start = start, end = null)
    // 47 seconds in: elapsed still rendered as HH:MM (00:00), never 00:00:47;
    // 47s of 13h = 0.1004% -> 0.10%
    val now = start.toEpochMilli() + 47_000L
    assertEquals("Elapsed 00:00 · 0.10%", notificationText(session, now))
  }

  @Test
  fun notificationTextCapsAtTarget() {
    val start = Instant.parse("2026-11-14T20:00:00Z")
    val session = Session(start = start, end = null)
    // 13h elapsed exactly -> 100.00%
    val now = start.toEpochMilli() + 13 * 3600 * 1000L
    assertEquals("Elapsed 13:00 · 100.00%", notificationText(session, now))
  }
}
