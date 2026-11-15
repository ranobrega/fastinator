package com.ruinobrega.fastinator.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Data class representing a fasting session in Fastinator.
 *
 * Requirements:
 * - start: Instant
 * - end: Instant? (null indicates active ongoing session)
 * - Fixed 13h target duration (100% achievement)
 * - Computed properties for elapsed, achievementPercent (can exceed 100%), and targetReachedAt
 */
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val start: Instant,
    val end: Instant? = null,
) {
    /**
     * Secondary constructor for epoch-millisecond initialization.
     */
    constructor(
        id: String = UUID.randomUUID().toString(),
        startTimeMillis: Long,
        endTimeMillis: Long? = null,
    ) : this(
        id = id,
        start = Instant.ofEpochMilli(startTimeMillis),
        end = endTimeMillis?.let { Instant.ofEpochMilli(it) }
    )

    companion object {
        const val TARGET_HOURS: Long = 13L
        val TARGET_DURATION: Duration = Duration.ofHours(TARGET_HOURS)
        val TARGET_SECONDS: Long = TARGET_DURATION.seconds
        val TARGET_MILLIS: Long = TARGET_SECONDS * 1000L

        fun formatDuration(duration: Duration): String {
            val totalSeconds = duration.seconds.coerceAtLeast(0L)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)
        }

        fun formatDurationWithSeconds(duration: Duration): String {
            val totalSeconds = duration.seconds.coerceAtLeast(0L)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }

        fun formatElapsed(totalSeconds: Long): String {
            return formatDuration(Duration.ofSeconds(totalSeconds))
        }

        fun formatClockTime(instant: Instant): String {
            return try {
                val localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime()
                val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                localTime.format(formatter)
            } catch (_: Exception) {
                "--:--"
            }
        }

        fun formatClockTime(epochMillis: Long): String {
            return formatClockTime(Instant.ofEpochMilli(epochMillis))
        }

        fun formatPercentage(percent: Float): String {
            return String.format(Locale.getDefault(), "%.2f%%", percent)
        }

        fun formatPercentage(percent: Int): String {
            return String.format(Locale.getDefault(), "%.2f%%", percent.toFloat())
        }
    }

    /**
     * Active status: null end means session is actively in progress.
     */
    val isActive: Boolean
        get() = end == null

    /**
     * Target reached at: Instant exactly 13 hours after start.
     */
    val targetReachedAt: Instant
        get() = start + TARGET_DURATION

    /**
     * Formatted 24h clock string when target was/will be reached (e.g. "11:00").
     */
    val targetReachedAtFormatted: String
        get() = formatClockTime(targetReachedAt)

    val targetReachedClockTime: String
        get() = targetReachedAtFormatted

    /**
     * Elapsed duration between start and end (or current time if active).
     */
    fun elapsedDuration(now: Instant = Instant.now()): Duration {
        val termination = end ?: now
        val duration = Duration.between(start, termination)
        return if (duration.isNegative) Duration.ZERO else duration
    }

    /**
     * Formatted elapsed duration string "HH:MM".
     */
    fun formattedElapsed(now: Instant = Instant.now()): String {
        return formatDuration(elapsedDuration(now))
    }

    fun formattedElapsed(currentTimeMillis: Long): String {
        return formattedElapsed(Instant.ofEpochMilli(currentTimeMillis))
    }

    /**
     * Formatted elapsed duration string "HH:MM:SS".
     */
    fun formattedElapsedWithSeconds(now: Instant = Instant.now()): String {
        return formatDurationWithSeconds(elapsedDuration(now))
    }

    fun formattedElapsedWithSeconds(currentTimeMillis: Long): String {
        return formattedElapsedWithSeconds(Instant.ofEpochMilli(currentTimeMillis))
    }

    /**
     * Achievement percentage against the fixed 13h target. Can exceed 100%.
     */
    fun achievementPercent(now: Instant = Instant.now()): Int {
        val seconds = elapsedDuration(now).seconds
        return ((seconds.toDouble() / TARGET_SECONDS.toDouble()) * 100).toInt()
    }

    fun achievementPercentFloat(now: Instant = Instant.now()): Float {
        val seconds = elapsedDuration(now).seconds
        return (seconds.toFloat() / TARGET_SECONDS.toFloat() * 100f)
    }

    fun getAchievementPercentage(currentTimeMillis: Long = System.currentTimeMillis()): Int {
        return achievementPercent(Instant.ofEpochMilli(currentTimeMillis))
    }

    /**
     * Progress fraction for circular progress ring (0.0f to 1.0f).
     */
    fun progressFraction(now: Instant = Instant.now()): Float {
        val seconds = elapsedDuration(now).seconds
        return (seconds.toFloat() / TARGET_SECONDS.toFloat()).coerceIn(0f, 1f)
    }

    fun getProgressFraction(currentTimeMillis: Long = System.currentTimeMillis()): Float {
        return progressFraction(Instant.ofEpochMilli(currentTimeMillis))
    }

    fun getElapsedSeconds(currentTimeMillis: Long = System.currentTimeMillis()): Long {
        return elapsedDuration(Instant.ofEpochMilli(currentTimeMillis)).seconds
    }

    fun isTargetAchieved(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return achievementPercent(Instant.ofEpochMilli(currentTimeMillis)) >= 100
    }

    val startTimeMillis: Long
        get() = start.toEpochMilli()

    val endTimeMillis: Long?
        get() = end?.toEpochMilli()
}

typealias FastingSession = Session
