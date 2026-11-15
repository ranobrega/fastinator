package com.ruinobrega.fastinator.ui.components
import com.ruinobrega.fastinator.debugTag

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ruinobrega.fastinator.model.FastingSession
import com.ruinobrega.fastinator.model.SessionValidator
import com.ruinobrega.fastinator.ui.theme.OverTargetGold
import com.ruinobrega.fastinator.ui.theme.StopRed
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionFormBottomSheet(
    sessionToEdit: FastingSession?,
    allSessions: List<FastingSession>,
    onDismiss: () -> Unit,
    onSave: (FastingSession) -> Unit,
    onDelete: (FastingSession) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val isEditMode = sessionToEdit != null
    val zone = remember { ZoneId.systemDefault() }
    val nowMillis = remember { System.currentTimeMillis() }

    // Initialize values based on editing session or defaults
    // Spec §3.2: the add form defaults to yesterday 18:00 for overnight entry.
    val initialStartDate = remember(sessionToEdit) {
        if (sessionToEdit != null) {
            Instant.ofEpochMilli(sessionToEdit.startTimeMillis).atZone(zone).toLocalDate()
        } else {
            LocalDate.now(zone).minusDays(1)
        }
    }

    val initialStartTime = remember(sessionToEdit) {
        if (sessionToEdit != null) {
            Instant.ofEpochMilli(sessionToEdit.startTimeMillis).atZone(zone).toLocalTime()
        } else {
            LocalTime.of(18, 0)
        }
    }

    val initialEndTime = remember(sessionToEdit) {
        val end = sessionToEdit?.endTimeMillis
        if (end != null) {
            Instant.ofEpochMilli(end).atZone(zone).toLocalTime()
        } else {
            LocalTime.of(9, 30)
        }
    }

    val initialEndsNextDay = remember(sessionToEdit) {
        val end = sessionToEdit?.endTimeMillis
        if (end != null) {
            val sDate = Instant.ofEpochMilli(sessionToEdit.startTimeMillis).atZone(zone).toLocalDate()
            val eDate = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
            eDate.isAfter(sDate)
        } else {
            true // Default 20:00 to 09:30 is overnight
        }
    }

    var selectedDate by remember { mutableStateOf(initialStartDate) }
    var startTime by remember { mutableStateOf(initialStartTime) }
    var endTime by remember { mutableStateOf(initialEndTime) }
    var endsNextDay by remember { mutableStateOf(initialEndsNextDay) }

    // Dialog pickers state
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // Compute epoch milliseconds for current form selections
    val startEpochMillis by remember {
        derivedStateOf {
            selectedDate.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
        }
    }

    val endEpochMillis by remember {
        derivedStateOf {
            val endDate = if (endsNextDay) selectedDate.plusDays(1) else selectedDate
            endDate.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
        }
    }

    // Live computed duration and metrics
    val durationSeconds by remember {
        derivedStateOf {
            ((endEpochMillis - startEpochMillis) / 1000L).coerceAtLeast(0L)
        }
    }

    val elapsedFormatted by remember {
        derivedStateOf {
            FastingSession.formatElapsed(durationSeconds)
        }
    }

    val achievementPercentage by remember {
        derivedStateOf {
            durationSeconds.toFloat() / FastingSession.TARGET_SECONDS.toFloat() * 100f
        }
    }

    val targetClockTime by remember {
        derivedStateOf {
            FastingSession.formatClockTime(startEpochMillis + FastingSession.TARGET_MILLIS)
        }
    }

    // Inline validation errors (spec §2) via the shared, unit-tested validator.
    // The session being edited is excluded from the overlap check.
    val validationErrors by remember {
        derivedStateOf {
            SessionValidator.validate(
                start = Instant.ofEpochMilli(startEpochMillis),
                end = Instant.ofEpochMilli(endEpochMillis),
                now = Instant.ofEpochMilli(nowMillis),
                existing = allSessions,
                excludeId = sessionToEdit?.id,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.debugTag("session_form_bottom_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isEditMode) "Edit Fasting Session" else "Add Fasting Session",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Fixed target badge reminder
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Target: 13:00",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Live Computed Metrics Card (Elapsed time, achievement %, 13h target clock time)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .debugTag("live_computed_metrics_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (validationErrors.isEmpty()) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Elapsed Duration",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = elapsedFormatted,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Achievement",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = FastingSession.formatPercentage(achievementPercentage),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (achievementPercentage >= 100f) OverTargetGold else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "13h target reached at $targetClockTime (24h clock)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Inline Validation Error Banners
            AnimatedVisibility(visible = validationErrors.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        validationErrors.forEach { errorMsg ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Field 1: Date Selector
            val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()) }
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .debugTag("field_select_date"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Start Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedDate.format(dateFormatter),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Field 2: Start Time & Field 3: End Time Pickers
            val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Start Time Card
                OutlinedCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showStartTimePicker = true }
                        .debugTag("field_select_start_time"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Start Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = startTime.format(timeFormatter),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // End Time Card
                OutlinedCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showEndTimePicker = true }
                        .debugTag("field_select_end_time"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "End Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = endTime.format(timeFormatter),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Overnight (+1 day) Toggle Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "End session overnight (+1 day)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FilterChip(
                    selected = endsNextDay,
                    onClick = { endsNextDay = !endsNextDay },
                    label = { Text(if (endsNextDay) "+1 Day" else "Same Day") },
                    leadingIcon = {
                        if (endsNextDay) {
                            Icon(
                                imageVector = Icons.Filled.NightlightRound,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isEditMode) {
                    // Delete button in edit mode
                    OutlinedButton(
                        onClick = { onDelete(sessionToEdit) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StopRed
                        ),
                        border = BorderStroke(1.dp, StopRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.debugTag("button_delete_session")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete session",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val newSession = FastingSession(
                            id = sessionToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                            startTimeMillis = startEpochMillis,
                            endTimeMillis = endEpochMillis
                        )
                        onSave(newSession)
                    },
                    enabled = validationErrors.isEmpty(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.debugTag("button_save_session")
                ) {
                    Text(if (isEditMode) "Save" else "Add Session")
                }
            }
        }
    }

    // Material 3 Date Picker Dialog
    if (showDatePicker) {
        val initialPickerMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialPickerMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("Select Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Material 3 Start Time Picker Dialog
    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    // If user selects overnight times without toggle, adapt smartly
                    if (endTime.isBefore(startTime)) {
                        endsNextDay = true
                    }
                    showStartTimePicker = false
                }) {
                    Text("Set Start Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Select Start Time (24h)") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    // Material 3 End Time Picker Dialog
    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    if (endTime.isBefore(startTime)) {
                        endsNextDay = true
                    }
                    showEndTimePicker = false
                }) {
                    Text("Set End Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Select End Time (24h)") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }
}
