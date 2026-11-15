package com.ruinobrega.fastinator.ui.screens
import com.ruinobrega.fastinator.debugTag

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruinobrega.fastinator.data.shareLatestExport
import com.ruinobrega.fastinator.model.FastingSession
import com.ruinobrega.fastinator.model.ReportAggregator
import com.ruinobrega.fastinator.ui.components.SessionFormBottomSheet
import com.ruinobrega.fastinator.ui.theme.FastinatorTheme
import com.ruinobrega.fastinator.ui.theme.OverTargetGold
import com.ruinobrega.fastinator.ui.theme.StopRed
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * History Screen for Fastinator: a daily session log.
 * - Reverse-chronological list grouped by day with sticky date headers.
 * - Skipped days rendered with muted lower-emphasis cards.
 * - Session rows showing start/end, elapsed, achievement %, and supporting-text target reached time.
 * - FloatingActionButton to add session manually via M3 DatePicker/TimePickers.
 * - Tap row to edit session with Save/Delete.
 * - Confirmation dialog for today's most recent session; immediate delete with Undo snackbar for older sessions.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessions: List<FastingSession>,
    modifier: Modifier = Modifier,
    onAddSession: (FastingSession) -> Unit = {},
    onUpdateSession: (FastingSession) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onRestoreSession: (FastingSession) -> Unit = {},
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Bottom Sheet form state
    var isSheetOpen by remember { mutableStateOf(false) }
    var selectedSessionForEdit by remember { mutableStateOf<FastingSession?>(null) }

    // Confirmation dialog state for deleting today's most recent session
    var sessionPendingDeleteConfirmation by remember { mutableStateOf<FastingSession?>(null) }

    // Determine today's most recent session for confirmation dialog policy
    val todaysMostRecentSession = remember(sessions) {
        sessions
            .filter { Instant.ofEpochMilli(it.startTimeMillis).atZone(zone).toLocalDate() == today }
            .maxByOrNull { it.startTimeMillis }
    }

    // Determine calendar date span (at least 10 days)
    val earliestSessionDate = remember(sessions) {
        val minDate = sessions.minOfOrNull {
            Instant.ofEpochMilli(it.startTimeMillis).atZone(zone).toLocalDate()
        } ?: today.minusDays(10)
        if (minDate.isAfter(today.minusDays(10))) today.minusDays(10) else minDate
    }

    // Grouping by day: Generate list of dates from today down to earliestDate (reverse chronological)
    val dateList = remember(earliestSessionDate, today) {
        val daysCount = Period.between(earliestSessionDate, today).days.coerceAtLeast(10)
        (0..daysCount).map { today.minusDays(it.toLong()) }
    }

    // Map dates to sessions — grouping by start day via the unit-tested aggregator
    // (overnight fasts belong to the day they started, spec §2)
    val sessionsByDate = remember(sessions, dateList, zone) {
        ReportAggregator.groupByDay(sessions, dateList, zone)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .debugTag("history_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedSessionForEdit = null
                    isSheetOpen = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.debugTag("add_session_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add session manually"
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Share action: opens the share sheet for the latest exported JSON file (spec §3.6)
            item(key = "share_export_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { shareLatestExport(context) },
                        modifier = Modifier.debugTag("share_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share latest export"
                        )
                    }
                }
            }

            dateList.forEach { date ->
                val daySessions = sessionsByDate[date].orEmpty()

                // Sticky date header for each day
                stickyHeader(key = "header_${date}") {
                    StickyDayHeader(date = date, today = today)
                }

                if (daySessions.isEmpty()) {
                    // Distinct skipped row for days with no recorded fasting sessions
                    item(key = "skipped_${date}") {
                        SkippedDayRow(date = date)
                    }
                } else {
                    // Session rows for the day
                    items(daySessions, key = { it.id }) { session ->
                        HistorySessionRowItem(
                            session = session,
                            // Spec §2/§3.1: a fast ends only via the Start/Stop button,
                            // so the active session cannot be edited (and thereby ended) here.
                            onClick = {
                                if (!session.isActive) {
                                    selectedSessionForEdit = session
                                    isSheetOpen = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Form Bottom Sheet (Add / Edit)
    if (isSheetOpen) {
        SessionFormBottomSheet(
            sessionToEdit = selectedSessionForEdit,
            allSessions = sessions,
            onDismiss = {
                isSheetOpen = false
                selectedSessionForEdit = null
            },
            onSave = { updatedOrNewSession ->
                if (selectedSessionForEdit != null) {
                    onUpdateSession(updatedOrNewSession)
                } else {
                    onAddSession(updatedOrNewSession)
                }
                isSheetOpen = false
                selectedSessionForEdit = null
            },
            onDelete = { sessionToDelete ->
                val isTodaysLatest = sessionToDelete.id == todaysMostRecentSession?.id

                if (isTodaysLatest) {
                    // Show confirmation dialog specifically for today's most recent session
                    sessionPendingDeleteConfirmation = sessionToDelete
                } else {
                    // For older sessions, delete immediately and show an Undo snackbar
                    onDeleteSession(sessionToDelete.id)
                    isSheetOpen = false
                    selectedSessionForEdit = null

                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Session deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onRestoreSession(sessionToDelete)
                        }
                    }
                }
            }
        )
    }

    // Confirmation Dialog for Today's Most Recent Session
    sessionPendingDeleteConfirmation?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingDeleteConfirmation = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = StopRed
                )
            },
            title = {
                Text(
                    text = "Delete Today's Session?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete today's most recent fasting session (${session.formattedElapsed(session.endTimeMillis ?: System.currentTimeMillis())})? This action cannot be automatically undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(session.id)
                        sessionPendingDeleteConfirmation = null
                        isSheetOpen = false
                        selectedSessionForEdit = null

                        // Undo snackbar also after the confirmed delete (spec §3.2)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Session deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onRestoreSession(session)
                            }
                        }
                    }
                ) {
                    Text("Delete", color = StopRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDeleteConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Sticky Date Header in reverse-chronological order.
 */
@Composable
fun StickyDayHeader(
    date: LocalDate,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    val title = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
            date.format(formatter)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            if (date == today) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Current Day",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Muted, lower-emphasis row for days with no recorded session so the user clearly sees gaps.
 */
@Composable
fun SkippedDayRow(
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .debugTag("skipped_day_row_${date}")
            .semantics(mergeDescendants = true) {
                contentDescription = "Skipped day. No fasting session recorded. Zero hours elapsed."
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.EventBusy,
                contentDescription = "Skipped day",
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )

            Column {
                Text(
                    text = "No fast recorded",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Skipped day • 00:00 elapsed",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Individual session row showing:
 * - Start time & End time in 24h
 * - Elapsed duration
 * - Achievement %
 * - Supporting-text style for the 13h target reached time (NOT a separate chip)
 * - Tappable to open edit mode
 */
@Composable
fun HistorySessionRowItem(
    session: FastingSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val endMillis = session.endTimeMillis ?: System.currentTimeMillis()
    val elapsed = session.formattedElapsed(endMillis)
    val achievement = session.achievementPercentFloat(Instant.ofEpochMilli(endMillis))
    val achievementFormatted = FastingSession.formatPercentage(achievement)
    val isMet = achievement >= 100f
    val isOver = achievement > 100f

    val startClock = FastingSession.formatClockTime(session.startTimeMillis)
    val endClock = FastingSession.formatClockTime(endMillis)
    val targetReachedClock = session.targetReachedClockTime

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .debugTag("session_row_${session.id}")
            .semantics(mergeDescendants = true) {
                contentDescription = "Fasting session from $startClock to $endClock. Elapsed duration: $elapsed hours. Achievement: $achievementFormatted. 13-hour target reached at $targetReachedClock."
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Column: Times & Duration
                Column(modifier = Modifier.weight(1f)) {
                    // Start & End times in 24h clock
                    Text(
                        text = "$startClock – $endClock",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Elapsed duration
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = elapsed,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Right Column: Achievement %
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isOver) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = OverTargetGold,
                                modifier = Modifier.size(16.dp)
                            )
                        } else if (isMet) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = achievementFormatted,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = when {
                                isOver -> OverTargetGold
                                isMet -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Supporting-text style for the 13h target reached time (NOT a separate chip)
            Text(
                text = "13h target reached at $targetReachedClock",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

/**
 * Preview-only fixture built locally (spec §4: previews must never use repository
 * sample data). 10+ days, one skipped day, one day with two sessions.
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

    add(0, 1, 0, 14, 15)              // today: 13h15m
    add(1, 20, 0, 9, 45, overnight = true) // 13h45m
    add(2, 2, 0, 10, 30)              // two sessions on day 2
    add(2, 13, 0, 19, 0)
    // day 3 skipped
    add(4, 19, 30, 8, 30, overnight = true)
    add(5, 18, 0, 9, 20, overnight = true)
    add(6, 21, 0, 9, 30, overnight = true)
    add(7, 19, 0, 8, 30, overnight = true)
    add(8, 20, 15, 10, 15, overnight = true)
    add(9, 19, 45, 8, 55, overnight = true)
    add(10, 21, 30, 9, 15, overnight = true)

    return list.sortedByDescending { it.startTimeMillis }
}

@Preview(name = "History Screen - 10+ Days Log", showBackground = true)
@Composable
fun HistoryScreenPreview() {
    FastinatorTheme(darkTheme = false) {
        HistoryScreen(sessions = buildPreviewSessions())
    }
}

@Preview(name = "History Screen - Dark Theme", showBackground = true)
@Composable
fun HistoryScreenDarkPreview() {
    FastinatorTheme(darkTheme = true) {
        HistoryScreen(sessions = buildPreviewSessions())
    }
}
