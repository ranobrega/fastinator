package com.ruinobrega.fastinator.model

import java.time.Duration
import java.time.Instant

/**
 * Session validation rules (spec §2), extracted as a pure, unit-testable function:
 * - a session's end must be after its start
 * - start/end must be in the past or now
 * - sessions may not overlap in time (an active session has an open end)
 * - minimum duration of 1 minute
 *
 * Violations are returned as inline error messages and block saving.
 */
object SessionValidator {

    /**
     * True if [start, end) overlaps any existing session. The session with
     * [excludeId] (the one being edited) is ignored. An active session
     * (null end) is treated as open-ended.
     */
    fun isOverlapping(
        start: Instant,
        end: Instant,
        existing: List<Session>,
        excludeId: String? = null,
    ): Boolean {
        return existing.any { session ->
            if (session.id == excludeId) {
                false
            } else {
                val sessionEnd = session.end ?: Instant.ofEpochMilli(Long.MAX_VALUE)
                maxOf(start, session.start) < minOf(end, sessionEnd)
            }
        }
    }

    /**
     * Returns the list of validation error messages for a candidate session.
     * Empty list means the session can be saved.
     */
    fun validate(
        start: Instant,
        end: Instant,
        now: Instant,
        existing: List<Session>,
        excludeId: String? = null,
    ): List<String> {
        val errors = mutableListOf<String>()
        if (!end.isAfter(start)) {
            errors.add("End time must be after start time.")
        }
        if (start.isAfter(now) || end.isAfter(now)) {
            errors.add("Fasting sessions cannot be in the future.")
        }
        val durationSeconds = Duration.between(start, end).seconds.coerceAtLeast(0L)
        if (durationSeconds > 0 && isOverlapping(start, end, existing, excludeId)) {
            errors.add("Session overlaps with an existing fast.")
        }
        if (durationSeconds in 1..59) {
            errors.add("Session duration must be at least 1 minute.")
        }
        return errors
    }
}
