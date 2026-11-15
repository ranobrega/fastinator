package com.ruinobrega.fastinator.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Per-day aggregation row for reports and charts.
 */
data class DayAggregate(
    val date: LocalDate,
    val totalSeconds: Long,
    val achievementPercentage: Float,
    val isSkipped: Boolean,
)

/**
 * Aggregated metrics for a week or month period (spec §3.3/§3.4).
 */
data class ReportAggregate(
    val periodDates: List<LocalDate>,
    val dayAggregates: List<DayAggregate>,
    val totalFastingSeconds: Long,
    val averageDailySeconds: Long,
    val averageAchievementPercent: Float,
    val fastingDaysCount: Int,
    val skippedDaysCount: Int,
    val bestDay: DayAggregate?,
)

/**
 * Report aggregation logic (spec §3.3-§3.5), extracted as pure, unit-testable
 * functions. Reports are derived views over the session log, never cached state:
 * a day with multiple sessions contributes the sum of their elapsed times, and
 * its achievement % is summed elapsed vs the fixed 13h target. Skipped days
 * count as zero. Only days up to today count as fasting/skipped and feed the
 * averages — future days of the current period are not penalized.
 */
object ReportAggregator {

    /** The 7 days of the week containing [anchor], Sunday start (spec §3.3). */
    fun weekDates(anchor: LocalDate): List<LocalDate> {
        val sunday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return (0..6).map { sunday.plusDays(it.toLong()) }
    }

    /** All days of the calendar month containing [anchor] (spec §3.4). */
    fun monthDates(anchor: LocalDate): List<LocalDate> {
        val yearMonth = YearMonth.from(anchor)
        return (1..yearMonth.lengthOfMonth()).map { anchor.withDayOfMonth(it) }
    }

    /** Groups sessions by the day they started (overnight fasts belong to the start day, spec §2). */
    fun groupByDay(
        sessions: List<Session>,
        dates: List<LocalDate>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, List<Session>> {
        return dates.associateWith { date ->
            sessions
                .filter { it.start.atZone(zone).toLocalDate() == date }
                .sortedByDescending { it.startTimeMillis }
        }
    }

    /** Aggregates [sessions] over [periodDates]. */
    fun aggregate(
        periodDates: List<LocalDate>,
        sessions: List<Session>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
        nowMillis: Long = System.currentTimeMillis(),
    ): ReportAggregate {
        val dayAggregates = periodDates.map { date ->
            val daySessions = sessions.filter { it.start.atZone(zone).toLocalDate() == date }
            val totalSeconds = daySessions.sumOf {
                it.getElapsedSeconds(it.endTimeMillis ?: nowMillis)
            }
            val achievement = totalSeconds.toFloat() / Session.TARGET_SECONDS.toFloat() * 100f
            DayAggregate(
                date = date,
                totalSeconds = totalSeconds,
                achievementPercentage = achievement,
                isSkipped = totalSeconds == 0L,
            )
        }

        val totalFastingSeconds = dayAggregates.sumOf { it.totalSeconds }
        val countedDays = dayAggregates.filter { !it.date.isAfter(today) }
        val fastingDaysCount = countedDays.count { !it.isSkipped }
        val skippedDaysCount = countedDays.size - fastingDaysCount
        val averageDailySeconds = if (countedDays.isNotEmpty()) totalFastingSeconds / countedDays.size else 0L
        val averageAchievementPercent = if (countedDays.isNotEmpty()) {
            countedDays.sumOf { it.achievementPercentage.toDouble() }.toFloat() / countedDays.size
        } else {
            0f
        }
        val bestDay = dayAggregates.asSequence().filter { !it.isSkipped }.maxByOrNull { it.achievementPercentage }

        return ReportAggregate(
            periodDates = periodDates,
            dayAggregates = dayAggregates,
            totalFastingSeconds = totalFastingSeconds,
            averageDailySeconds = averageDailySeconds,
            averageAchievementPercent = averageAchievementPercent,
            fastingDaysCount = fastingDaysCount,
            skippedDaysCount = skippedDaysCount,
            bestDay = bestDay,
        )
    }
}
