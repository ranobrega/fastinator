package com.ruinobrega.fastinator.ui.screens
import com.ruinobrega.fastinator.debugTag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruinobrega.fastinator.model.DayAggregate
import com.ruinobrega.fastinator.model.FastingSession
import com.ruinobrega.fastinator.model.ReportAggregator
import com.ruinobrega.fastinator.ui.theme.FastinatorTheme
import com.ruinobrega.fastinator.ui.theme.OverTargetGold
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ReportPeriod {
    WEEK,
    MONTH
}

/**
 * Reports Screen for Fastinator: Weekly and monthly aggregation views.
 *
 * - Two tabs / segmented button: Week and Month.
 * - Week view: Sunday start, back/forward arrow navigation, range label.
 * - Month view: Calendar month, back/forward arrow navigation, range label.
 * - Metrics: Total fasting time, Average daily elapsed time, Average achievement %,
 *   Fasting days vs skipped days, and Best day (date + achievement %).
 * - Canvas bar chart of daily achievement % with empty/baseline bars for skipped days
 *   and subtle 100% horizontal reference line (values above 100% render above the line).
 * - Single reactive list of sessions; recomposes when state changes.
 */
@Composable
fun ReportsScreen(
    sessions: List<FastingSession>,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    var selectedPeriod by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ReportPeriod.WEEK) }

    // Navigation anchor date (survives tab switches via rememberSaveable epoch day)
    var anchorEpochDay by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableLongStateOf(LocalDate.now(zone).toEpochDay())
    }
    val anchorDate = remember(anchorEpochDay) { LocalDate.ofEpochDay(anchorEpochDay) }

    // Compute period date bounds (Sunday-start weeks / calendar months, spec §3.3/§3.4)
    val periodDates = remember(selectedPeriod, anchorDate) {
        when (selectedPeriod) {
            ReportPeriod.WEEK -> ReportAggregator.weekDates(anchorDate)
            ReportPeriod.MONTH -> ReportAggregator.monthDates(anchorDate)
        }
    }

    // Range Label formatting (e.g. "Nov 1 – Nov 7, 2026" or "November 2026")
    val rangeLabel = remember(selectedPeriod, periodDates, anchorDate) {
        when (selectedPeriod) {
            ReportPeriod.WEEK -> {
                val start = periodDates.first()
                val end = periodDates.last()
                val monthStart = start.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                val monthEnd = if (start.month == end.month) {
                    end.format(DateTimeFormatter.ofPattern("d, yyyy", Locale.getDefault()))
                } else {
                    end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
                }
                "$monthStart – $monthEnd"
            }
            ReportPeriod.MONTH -> {
                anchorDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            }
        }
    }

    // Aggregation is extracted into ReportAggregator (unit-tested). Reports are
    // derived views over the session log, never cached stored state (spec §3.5).
    // Only days up to today count as fasting/skipped and feed the averages.
    val reportAggregate = remember(periodDates, sessions, zone) {
        ReportAggregator.aggregate(periodDates, sessions, zone)
    }
    val dayAggregates = reportAggregate.dayAggregates
    val totalFastingSeconds = reportAggregate.totalFastingSeconds
    val fastingDaysCount = reportAggregate.fastingDaysCount
    val skippedDaysCount = reportAggregate.skippedDaysCount
    val averageDailySeconds = reportAggregate.averageDailySeconds
    val averageAchievementPercent = reportAggregate.averageAchievementPercent
    val bestDayAggregate = reportAggregate.bestDay

    val bestDayLabel = remember(bestDayAggregate) {
        if (bestDayAggregate != null) {
            val dateStr = bestDayAggregate.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
            "$dateStr (${String.format(Locale.getDefault(), "%.2f%%", bestDayAggregate.achievementPercentage)})"
        } else {
            "None"
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .debugTag("reports_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Period Selector: Week / Month Segmented Buttons
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .debugTag("period_segmented_button_row")
        ) {
            ReportPeriod.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = selectedPeriod == period,
                    onClick = { selectedPeriod = period },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ReportPeriod.entries.size),
                    label = {
                        Text(
                            text = if (period == ReportPeriod.WEEK) "Week" else "Month",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                )
            }
        }

        // 2. Date Navigation Bar (< Range Label >)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        val newDate = when (selectedPeriod) {
                            ReportPeriod.WEEK -> anchorDate.minusWeeks(1)
                            ReportPeriod.MONTH -> anchorDate.minusMonths(1)
                        }
                        anchorEpochDay = newDate.toEpochDay()
                    },
                    modifier = Modifier.debugTag("nav_arrow_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous period",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = rangeLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = {
                        val newDate = when (selectedPeriod) {
                            ReportPeriod.WEEK -> anchorDate.plusWeeks(1)
                            ReportPeriod.MONTH -> anchorDate.plusMonths(1)
                        }
                        anchorEpochDay = newDate.toEpochDay()
                    },
                    modifier = Modifier.debugTag("nav_arrow_forward")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next period",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2b. Daily Average summary (text only — no aggregated ring)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .debugTag("daily_average_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Daily Average",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Avg elapsed ${FastingSession.formatDuration(Duration.ofSeconds(averageDailySeconds))} vs 13:00 target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Avg achievement ${String.format(Locale.getDefault(), "%.2f%%", averageAchievementPercent)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Summary Stat Cards Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Row 1: Total Fasting Time & Average Daily Elapsed Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReportStatCard(
                    icon = Icons.Filled.HourglassTop,
                    label = "Total Fasting Time",
                    value = formatTotalDuration(totalFastingSeconds),
                    subtext = "Sum of all sessions",
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                ReportStatCard(
                    icon = Icons.Filled.HistoryToggleOff,
                    label = "Average Daily Time",
                    value = formatTotalDuration(averageDailySeconds),
                    subtext = "Daily elapsed average",
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Average Achievement % & Fasting vs Skipped Days
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReportStatCard(
                    icon = Icons.Filled.CheckCircle,
                    label = "Avg Achievement %",
                    value = String.format(Locale.getDefault(), "%.2f%%", averageAchievementPercent.toFloat()),
                    subtext = "vs 13h target (100.00%)",
                    iconTint = if (averageAchievementPercent >= 100) OverTargetGold else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                ReportStatCard(
                    icon = Icons.Filled.EventBusy,
                    label = "Fasting vs Skipped",
                    value = "$fastingDaysCount / $skippedDaysCount",
                    subtext = "$fastingDaysCount fasting • $skippedDaysCount skipped",
                    iconTint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3: Best Day Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = OverTargetGold.copy(alpha = 0.18f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = OverTargetGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Best Day",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = bestDayLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

        }

        // 4. Daily Achievement % Bar Chart Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .debugTag("achievement_barchart_card")
                .semantics(mergeDescendants = true) {
                    contentDescription = "Daily achievement bar chart against 13-hour fixed target. $fastingDaysCount active fasting days, $skippedDaysCount skipped days. Average achievement: ${String.format(Locale.getDefault(), "%.2f%%", averageAchievementPercent.toFloat())}."
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header above chart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Daily Achievement %",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Dashed line: 100.00% target (13:00)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Legend
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChartLegendDot(color = OverTargetGold, label = ">100%")
                        ChartLegendDot(color = MaterialTheme.colorScheme.primary, label = "Goal")
                    }
                }

                // Canvas Bar Chart
                DailyAchievementBarChart(
                    dayAggregates = dayAggregates,
                    period = selectedPeriod,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Custom Compose Canvas Bar Chart rendering:
 * - Daily achievement % across the range.
 * - Skipped days rendered as empty / baseline markers.
 * - Subtle horizontal reference line at exactly 100% (13h target).
 * - Bars above 100% render proportionally above the reference line.
 */
@Composable
fun DailyAchievementBarChart(
    dayAggregates: List<DayAggregate>,
    period: ReportPeriod,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val overTargetColor = OverTargetGold
    val skippedBaseColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val referenceLineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isWeek = period == ReportPeriod.WEEK

    // Determine scale: 100% line is our anchor, max scale allows values above 100%
    val maxAchievementInPeriod = dayAggregates.maxOfOrNull { it.achievementPercentage } ?: 100f
    val maxScale = maxOf(140f, (maxAchievementInPeriod * 1.15f))

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartHeight = size.height - 12.dp.toPx()
                val totalBars = dayAggregates.size
                if (totalBars == 0) return@Canvas

                val availableWidth = size.width
                val barWidth = if (isWeek) {
                    (availableWidth / totalBars) * 0.55f
                } else {
                    (availableWidth / totalBars) * 0.70f
                }
                val step = availableWidth / totalBars

                // 1. Draw 100% Target Reference Line (Subtle dashed horizontal line)
                val targetFraction = (100f / maxScale).coerceIn(0f, 1f)
                val targetY = chartHeight - (targetFraction * chartHeight)

                drawLine(
                    color = referenceLineColor,
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )

                // 2. Draw Daily Bars
                dayAggregates.forEachIndexed { index, day ->
                    val centerX = (index * step) + (step / 2f)
                    val left = centerX - (barWidth / 2f)

                    if (day.isSkipped) {
                        // Skipped day: empty / baseline subtle pill
                        val baseHeight = 4.dp.toPx()
                        drawRoundRect(
                            color = skippedBaseColor,
                            topLeft = Offset(left, chartHeight - baseHeight),
                            size = Size(barWidth, baseHeight),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    } else {
                        // Active fasting day: bar height proportional to achievement %
                        val fraction = (day.achievementPercentage / maxScale).coerceIn(0.04f, 1.0f)
                        val barHeight = fraction * chartHeight
                        val topY = chartHeight - barHeight

                        val barColor = if (day.achievementPercentage >= 100f) overTargetColor else primaryColor

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, topY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // X-Axis Day Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isWeek) {
                // Sunday start week labels: Sun, Mon, Tue, Wed, Thu, Fri, Sat
                dayAggregates.forEach { day ->
                    val dayName = day.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).take(3)
                    val dayNum = day.date.dayOfMonth.toString()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (day.isSkipped) textColor.copy(alpha = 0.5f) else textColor
                        )
                        Text(
                            text = dayNum,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Month labels: sample periodic marks (e.g. 1, 5, 10, 15, 20, 25, end)
                val marks = listOf(1, 5, 10, 15, 20, 25, dayAggregates.size)
                marks.forEach { dayNum ->
                    Text(
                        text = dayNum.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ReportStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    subtext: String,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ChartLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * Formats duration in seconds as HH:MM (spec §2: durations display as HH:MM).
 */
private fun formatTotalDuration(seconds: Long): String {
    return FastingSession.formatDuration(Duration.ofSeconds(seconds))
}

// -------------------------------------------------------------
// Previews
// -------------------------------------------------------------

/**
 * Preview-only fixture built locally (spec §4: previews must never use repository
 * sample data) — two weeks of sessions with one skipped day and one two-session day.
 */
private fun buildPreviewSessions(): List<FastingSession> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val list = mutableListOf<FastingSession>()

    fun add(dayOffset: Long, startH: Int, startM: Int, endH: Int, endM: Int, overnight: Boolean = false) {
        val date = today.minusDays(dayOffset)
        val start = date.atTime(startH, startM).atZone(zone).toInstant()
        val end = (if (overnight) date.plusDays(1) else date).atTime(endH, endM).atZone(zone).toInstant()
        list.add(FastingSession(startTimeMillis = start.toEpochMilli(), endTimeMillis = end.toEpochMilli()))
    }

    add(0, 1, 0, 14, 15)
    add(1, 20, 0, 9, 45, overnight = true)
    add(2, 2, 0, 10, 30)
    add(2, 13, 0, 19, 0)
    add(3, 19, 30, 8, 30, overnight = true)
    // day 4 skipped
    add(5, 18, 0, 9, 20, overnight = true)
    add(6, 21, 0, 9, 30, overnight = true)
    add(7, 19, 0, 8, 30, overnight = true)
    add(8, 20, 15, 10, 15, overnight = true)
    add(9, 19, 45, 8, 55, overnight = true)
    add(10, 21, 30, 9, 15, overnight = true)
    add(12, 20, 0, 9, 30, overnight = true)
    add(13, 19, 15, 8, 15, overnight = true)

    return list.sortedByDescending { it.startTimeMillis }
}

@Preview(name = "Reports Screen - Two Weeks Sample Data", showBackground = true)
@Composable
fun ReportsScreenPreview() {
    FastinatorTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ReportsScreen(sessions = buildPreviewSessions())
        }
    }
}

@Preview(name = "Reports Screen - Dark Theme", showBackground = true)
@Composable
fun ReportsScreenDarkPreview() {
    FastinatorTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ReportsScreen(sessions = buildPreviewSessions())
        }
    }
}
