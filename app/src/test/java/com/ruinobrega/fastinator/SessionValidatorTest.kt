package com.ruinobrega.fastinator

import com.ruinobrega.fastinator.model.Session
import com.ruinobrega.fastinator.model.SessionValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Spec §2 validation rules: end after start, no future times, no overlaps,
 * minimum 1 minute. Violations block saving with an inline error.
 */
class SessionValidatorTest {

  private val now = Instant.parse("2026-11-18T12:00:00Z")

  private fun session(id: String, start: String, end: String?): Session = Session(
    id = id,
    start = Instant.parse(start),
    end = end?.let { Instant.parse(it) },
  )

  @Test
  fun validSessionHasNoErrors() {
    val errors = SessionValidator.validate(
      start = Instant.parse("2026-11-18T10:00:00Z"),
      end = Instant.parse("2026-11-18T11:00:00Z"),
      now = now,
      existing = emptyList(),
    )
    assertEquals(emptyList<String>(), errors)
  }

  @Test
  fun endMustBeAfterStart() {
    val base = Instant.parse("2026-11-18T10:00:00Z")
    assertTrue(
      SessionValidator.validate(base, base.minusSeconds(1), now, emptyList())
        .any { it.contains("after start") }
    )
    assertTrue(
      SessionValidator.validate(base, base, now, emptyList())
        .any { it.contains("after start") }
    )
  }

  @Test
  fun startAndEndMustNotBeInTheFuture() {
    val errors = SessionValidator.validate(
      start = Instant.parse("2026-11-18T13:00:00Z"),
      end = Instant.parse("2026-11-18T14:00:00Z"),
      now = now,
      existing = emptyList(),
    )
    assertTrue(errors.any { it.contains("future") })
  }

  @Test
  fun overlappingSessionIsRejected() {
    val existing = listOf(session("a", "2026-11-18T09:00:00Z", "2026-11-18T11:00:00Z"))
    val errors = SessionValidator.validate(
      start = Instant.parse("2026-11-18T10:30:00Z"),
      end = Instant.parse("2026-11-18T12:00:00Z"),
      now = now,
      existing = existing,
    )
    assertTrue(errors.any { it.contains("overlaps") })
  }

  @Test
  fun touchingBoundariesDoNotOverlap() {
    val existing = listOf(session("a", "2026-11-18T09:00:00Z", "2026-11-18T11:00:00Z"))
    val errors = SessionValidator.validate(
      start = Instant.parse("2026-11-18T11:00:00Z"),
      end = Instant.parse("2026-11-18T12:00:00Z"),
      now = now,
      existing = existing,
    )
    assertEquals(emptyList<String>(), errors)
  }

  @Test
  fun sessionBeingEditedIsExcludedFromOverlapCheck() {
    val existing = listOf(session("a", "2026-11-18T09:00:00Z", "2026-11-18T11:00:00Z"))
    val errors = SessionValidator.validate(
      start = Instant.parse("2026-11-18T10:30:00Z"),
      end = Instant.parse("2026-11-18T12:00:00Z"),
      now = now,
      existing = existing,
      excludeId = "a",
    )
    assertEquals(emptyList<String>(), errors)
  }

  @Test
  fun activeSessionBlocksOverlappingEntries() {
    val active = listOf(session("active", "2026-11-18T11:00:00Z", null))
    val errors = SessionValidator.validate(
      start = Instant.parse("2026-11-18T11:30:00Z"),
      end = Instant.parse("2026-11-18T12:00:00Z"),
      now = now,
      existing = active,
    )
    assertTrue(errors.any { it.contains("overlaps") })
  }

  @Test
  fun sessionsShorterThanOneMinuteAreRejected() {
    val errors = SessionValidator.validate(
      start = Instant.parse("2026-11-18T10:00:00Z"),
      end = Instant.parse("2026-11-18T10:00:30Z"),
      now = now,
      existing = emptyList(),
    )
    assertTrue(errors.any { it.contains("at least 1 minute") })
  }

  @Test
  fun overnightSessionIsValid() {
    val errors = SessionValidator.validate(
      start = Instant.parse("2026-11-17T20:00:00Z"),
      end = Instant.parse("2026-11-18T09:30:00Z"),
      now = now,
      existing = emptyList(),
    )
    assertEquals(emptyList<String>(), errors)
  }
}
