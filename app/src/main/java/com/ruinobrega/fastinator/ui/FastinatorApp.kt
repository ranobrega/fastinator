package com.ruinobrega.fastinator.ui
import com.ruinobrega.fastinator.debugTag

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import com.ruinobrega.fastinator.data.AppDatabase
import com.ruinobrega.fastinator.data.ExportManager
import com.ruinobrega.fastinator.data.SessionRepository
import com.ruinobrega.fastinator.notification.startFastingService
import com.ruinobrega.fastinator.notification.stopFastingService
import com.ruinobrega.fastinator.ui.screens.HistoryScreen
import com.ruinobrega.fastinator.ui.screens.ReportsScreen
import com.ruinobrega.fastinator.ui.screens.SettingsScreen
import com.ruinobrega.fastinator.ui.screens.TodayScreen
import com.ruinobrega.fastinator.ui.theme.ThemeMode

enum class FastinatorTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    TODAY(
        title = "Today",
        selectedIcon = Icons.Filled.HourglassEmpty,
        unselectedIcon = Icons.Outlined.HourglassEmpty,
        testTag = "nav_today",
    ),
    HISTORY(
        title = "History",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history"
    ),
    REPORTS(
        title = "Reports",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        testTag = "nav_reports"
    ),
    SETTINGS(
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
}

/**
 * Full Fastinator App Shell:
 *
 * - Scaffold with edge-to-edge support and Material 3 NavigationBar (Today, History, Reports, Settings).
 * - State preservation: Uses rememberSaveable for active tab selection and preserves screen state.
 * - Shared in-memory session repository (SessionRepository with StateFlow<List<Session>>):
 *   All tabs react immediately to data changes (Today start/stop, History add/edit/delete/undo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastinatorApp(
    currentThemeMode: ThemeMode = ThemeMode.DARK,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    notificationsEnabled: Boolean = true,
    onNotificationsEnabledChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val sessionRepository = remember(context) {
        SessionRepository(AppDatabase.getInstance(context).sessionDao())
    }

    // Automatic JSON export (spec §3.6): writes fastinator/v1 exports to the
    // Fastinator export folder on every change of the shared session flow.
    val exportManager = remember(context, sessionRepository) {
        ExportManager(context, sessionRepository.sessions)
    }
    DisposableEffect(exportManager) {
        exportManager.start()
        onDispose { exportManager.stop() }
    }

    // Preserve tab navigation state across orientation changes and process recreation
    var selectedTab by rememberSaveable { mutableStateOf(FastinatorTab.TODAY) }

    // Collect reactive StateFlow from the shared in-memory repository
    val allSessions by sessionRepository.sessions.collectAsState()

    // Derived session states
    val activeSession = remember(allSessions) {
        allSessions.firstOrNull { it.isActive }
    }

    val lastCompletedSession = remember(allSessions) {
        allSessions.asSequence().filter { !it.isActive }.maxByOrNull { it.end ?: it.start }
    }

    // Spec §8.1 (v1.11): the persistent fasting notification (foreground service)
    // runs only while a fast is active AND the Settings toggle is on. Toggling off
    // mid-fast stops the service and removes the notification without touching the
    // session; toggling on mid-fast shows it immediately. This effect also runs on
    // app open, so after a reboot the notification returns as soon as the app is
    // opened with an active fast (accepted gap in spec §8.1).
    LaunchedEffect(activeSession?.id, notificationsEnabled) {
        if (activeSession != null && notificationsEnabled) {
            context.startFastingService()
        } else {
            context.stopFastingService()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            FastinatorTab.TODAY -> "Fastinator"
                            FastinatorTab.HISTORY -> "Daily Session Log"
                            FastinatorTab.REPORTS -> "Fast Reports"
                            FastinatorTab.SETTINGS -> "Settings"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.debugTag("bottom_navigation"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                FastinatorTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                        modifier = Modifier.debugTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tabContentAnimation"
            ) { targetTab ->
                when (targetTab) {
                    FastinatorTab.TODAY -> {
                        TodayScreen(
                            activeSession = activeSession,
                            lastCompletedSession = lastCompletedSession,
                            onStartFast = {
                                sessionRepository.startFast()
                            },
                            onStopFast = {
                                sessionRepository.stopFast()
                            },
                            notificationsEnabled = notificationsEnabled
                        )
                    }
                    FastinatorTab.HISTORY -> {
                        HistoryScreen(
                            sessions = allSessions,
                            onAddSession = { sessionRepository.addSession(it) },
                            onUpdateSession = { sessionRepository.updateSession(it) },
                            onDeleteSession = { sessionRepository.deleteSession(it) },
                            onRestoreSession = { sessionRepository.restoreSession(it) }
                        )
                    }
                    FastinatorTab.REPORTS -> {
                        ReportsScreen(
                            sessions = allSessions
                        )
                    }
                    FastinatorTab.SETTINGS -> {
                        SettingsScreen(
                            currentThemeMode = currentThemeMode,
                            onThemeModeChange = onThemeModeChange,
                            notificationsEnabled = notificationsEnabled,
                            onNotificationsEnabledChange = onNotificationsEnabledChange,
                            sessions = allSessions
                        )
                    }
                }
            }
        }
    }
}
