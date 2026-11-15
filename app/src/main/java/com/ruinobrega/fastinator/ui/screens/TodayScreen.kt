package com.ruinobrega.fastinator.ui.screens
import com.ruinobrega.fastinator.debugTag

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruinobrega.fastinator.model.FastingSession
import com.ruinobrega.fastinator.ui.components.CircularFastingRing
import com.ruinobrega.fastinator.ui.theme.FastinatorTheme
import com.ruinobrega.fastinator.ui.theme.OverTargetGold
import com.ruinobrega.fastinator.ui.theme.StartGreenContainer
import com.ruinobrega.fastinator.ui.theme.StartGreenOnContainer
import com.ruinobrega.fastinator.ui.theme.StopRed
import com.ruinobrega.fastinator.ui.theme.StopRedContainer
import com.ruinobrega.fastinator.ui.theme.StopRedOnContainer
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Today Screen for Fastinator.
 * Visual centerpiece circular ring, single Start/Stop button, and last session summary row.
 */
@Composable
fun TodayScreen(
    activeSession: FastingSession?,
    lastCompletedSession: FastingSession?,
    onStartFast: () -> Unit,
    onStopFast: () -> Unit,
    modifier: Modifier = Modifier,
    // Spec §8.1 (v1.11): whether the persistent fasting notification is enabled
    notificationsEnabled: Boolean = true,
    // Preview override for current time
    previewCurrentTimeMillis: Long? = null,
) {
    val isFasting = activeSession != null

    // Spec §8.1 (v1.11): POST_NOTIFICATIONS (API 33+) is requested when a fast
    // starts while the notification toggle is on. If denied, the fast still
    // starts — the foreground service runs, the notification just isn't shown.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result intentionally ignored: starting the fast never depends on it */ }

    // Live clock ticker: updates every second for proper clock behavior
    var currentTimeMillis by remember {
        mutableLongStateOf(previewCurrentTimeMillis ?: System.currentTimeMillis())
    }

    LaunchedEffect(isFasting, previewCurrentTimeMillis) {
        if (previewCurrentTimeMillis != null) {
            currentTimeMillis = previewCurrentTimeMillis
            return@LaunchedEffect
        }
        if (isFasting) {
            while (true) {
                currentTimeMillis = System.currentTimeMillis()
                // Update every second for proper clock behavior
                delay(1_000L)
            }
        }
    }

    // Computed values
    val elapsedFormatted = if (activeSession != null) {
        activeSession.formattedElapsedWithSeconds(currentTimeMillis)
    } else {
        "00:00:00"
    }

    val achievementPercentageFormatted = if (activeSession != null) {
        FastingSession.formatPercentage(activeSession.achievementPercentFloat(Instant.ofEpochMilli(currentTimeMillis)))
    } else {
        "0.00%"
    }

    // Spec §3.1: ring center shows the clock time the 13h target is/was reached —
    // the active session's while fasting, the last completed session's while idle.
    val targetClockTime = when {
        activeSession != null -> activeSession.targetReachedClockTime
        lastCompletedSession != null -> lastCompletedSession.targetReachedClockTime
        else -> "--:--"
    }

    val progressFraction = if (activeSession != null) {
        activeSession.getProgressFraction(currentTimeMillis)
    } else {
        0f
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .widthIn(max = 600.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Subtle top sub-header indicating fixed 13h target
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Fixed Target: 13:00 (100.00%)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 1. Centerpiece Circular Progress Ring
        CircularFastingRing(
            isFasting = isFasting,
            elapsedFormatted = elapsedFormatted,
            achievementPercentageFormatted = achievementPercentageFormatted,
            targetClockTime = targetClockTime,
            progressFraction = progressFraction,
            size = 280.dp,
            strokeWidth = 20.dp
        )

        // 2. Single Start/Stop Button
        // When idle: Filled button "Start" (persisted to Room via SessionRepository.startFast).
        // While fasting: Tonal / Error-toned button "Stop" (end timestamp persisted via stopFast).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isFasting) {
                Button(
                    onClick = {
                        if (notificationsEnabled && Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onStartFast()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .debugTag("start_stop_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StartGreenContainer,
                        contentColor = StartGreenOnContainer
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Start",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = { onStopFast() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .debugTag("start_stop_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = StopRedContainer,
                        contentColor = StopRedOnContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        tint = StopRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Stop",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = StopRed
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Summary row of the last completed session
        LastSessionSummaryCard(
            lastSession = lastCompletedSession,
            currentTimeMillis = currentTimeMillis
        )
    }
}

/**
 * Summary row of the last completed session displaying:
 * - Elapsed time
 * - Target time (13:00)
 * - Achievement %
 * - Clock time the 13h target was reached
 */
@Composable
fun LastSessionSummaryCard(
    lastSession: FastingSession?,
    currentTimeMillis: Long,
    modifier: Modifier = Modifier,
) {
    val end = lastSession?.endTimeMillis
    val summaryDesc = if (lastSession != null && end != null) {
        val elapsed = lastSession.formattedElapsed(end)
        val achievement = lastSession.achievementPercentFloat(Instant.ofEpochMilli(end))
        val achievementFormatted = FastingSession.formatPercentage(achievement)
        "Last completed fasting session. Elapsed duration: $elapsed. Fixed target: 13 hours. Achievement: $achievementFormatted. 13-hour target reached at ${lastSession.targetReachedClockTime}."
    } else {
        "Last completed session. No previous fasting session recorded yet."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .debugTag("last_session_summary_card")
            .semantics(mergeDescendants = true) {
                contentDescription = summaryDesc
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Last Completed Session",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (lastSession != null && lastSession.isTargetAchieved(lastSession.endTimeMillis ?: currentTimeMillis)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Target reached",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            val endMillis = lastSession?.endTimeMillis
            if (lastSession != null && endMillis != null) {
                val elapsed = lastSession.formattedElapsed(endMillis)
                val achievement = lastSession.achievementPercentFloat(Instant.ofEpochMilli(endMillis))
                val achievementFormatted = FastingSession.formatPercentage(achievement)
                val targetTime = "13:00"
                val targetReachedClock = lastSession.targetReachedClockTime

                // 4-column summary metric row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // 1. Elapsed Time (HH:MM per spec §2 — only the live ring timer shows seconds)
                    MetricItem(
                        label = "Elapsed",
                        value = elapsed,
                        subtext = "HH:MM",
                        highlight = achievement >= 100f
                    )

                    // 2. Target Time (Fixed 13:00)
                    MetricItem(
                        label = "Target",
                        value = targetTime,
                        subtext = "fixed",
                        highlight = false
                    )

                    // 3. Achievement %
                    MetricItem(
                        label = "Achieved",
                        value = achievementFormatted,
                        subtext = if (achievement >= 100f) "Goal met" else "Under target",
                        highlight = achievement >= 100f,
                        highlightColor = if (achievement >= 100f) OverTargetGold else MaterialTheme.colorScheme.primary
                    )

                    // 4. Clock time 13h target reached
                    MetricItem(
                        label = "13h Target",
                        value = targetReachedClock,
                        subtext = "clock time",
                        highlight = false
                    )
                }
            } else {
                // Empty state cue when no session has been completed yet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "No previous fasting session recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    subtext: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = if (highlight) highlightColor else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtext,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// -------------------------------------------------------------
// Previews
// -------------------------------------------------------------

/**
 * Preview with a session started exactly 8h 24m ago in Light Theme.
 * Target duration: 13h -> 8h24m elapsed / 13h = ~64% achievement.
 */
@Preview(name = "Today Screen - Fasting 8h 24m (Light)", showBackground = true)
@Composable
fun TodayScreenFastingPreview() {
    val fixedNow = 1762160000000L
    val elapsedMillis = (8L * 3600L + 24L * 60L) * 1000L // 8h 24m
    val activeSession = FastingSession(
        id = "preview_active_session",
        startTimeMillis = fixedNow - elapsedMillis
    )

    val lastCompleted = FastingSession(
        id = "preview_last_session",
        startTimeMillis = fixedNow - (30L * 3600L * 1000L),
        endTimeMillis = fixedNow - (16L * 3600L * 1000L) // 14h duration (107% achievement)
    )

    FastinatorTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TodayScreen(
                activeSession = activeSession,
                lastCompletedSession = lastCompleted,
                onStartFast = {},
                onStopFast = {},
                previewCurrentTimeMillis = fixedNow
            )
        }
    }
}

/**
 * Preview with a session started exactly 8h 24m ago in Dark Theme.
 */
@Preview(name = "Today Screen - Fasting 8h 24m (Dark)", showBackground = true)
@Composable
fun TodayScreenFastingDarkPreview() {
    val fixedNow = 1762160000000L
    val elapsedMillis = (8L * 3600L + 24L * 60L) * 1000L
    val activeSession = FastingSession(
        id = "preview_active_session_dark",
        startTimeMillis = fixedNow - elapsedMillis
    )

    val lastCompleted = FastingSession(
        id = "preview_last_session_dark",
        startTimeMillis = fixedNow - (30L * 3600L * 1000L),
        endTimeMillis = fixedNow - (16L * 3600L * 1000L)
    )

    FastinatorTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TodayScreen(
                activeSession = activeSession,
                lastCompletedSession = lastCompleted,
                onStartFast = {},
                onStopFast = {},
                previewCurrentTimeMillis = fixedNow
            )
        }
    }
}

/**
 * Preview with Idle state (empty ring, Start button, last completed session).
 */
@Preview(name = "Today Screen - Idle (Light)", showBackground = true)
@Composable
fun TodayScreenIdlePreview() {
    val fixedNow = 1762160000000L
    val lastCompleted = FastingSession(
        id = "preview_last_session_idle",
        startTimeMillis = fixedNow - (28L * 3600L * 1000L),
        endTimeMillis = fixedNow - (15L * 3600L * 1000L)
    )

    FastinatorTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TodayScreen(
                activeSession = null,
                lastCompletedSession = lastCompleted,
                onStartFast = {},
                onStopFast = {},
                previewCurrentTimeMillis = fixedNow
            )
        }
    }
}

/**
 * Preview in Dark Mode with session exceeding 13h target (e.g. 14h 30m = 111%).
 */
@Preview(name = "Today Screen - Over Target Fasting (Dark)", showBackground = true)
@Composable
fun TodayScreenOverTargetPreview() {
    val fixedNow = 1762160000000L
    val elapsedMillis = (14L * 3600L + 30L * 60L) * 1000L
    val activeSession = FastingSession(
        id = "preview_over_target",
        startTimeMillis = fixedNow - elapsedMillis
    )

    val lastCompleted = FastingSession(
        id = "preview_last_session",
        startTimeMillis = fixedNow - (40L * 3600L * 1000L),
        endTimeMillis = fixedNow - (27L * 3600L * 1000L)
    )

    FastinatorTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TodayScreen(
                activeSession = activeSession,
                lastCompletedSession = lastCompleted,
                onStartFast = {},
                onStopFast = {},
                previewCurrentTimeMillis = fixedNow
            )
        }
    }
}
