package com.ruinobrega.fastinator.data

import com.ruinobrega.fastinator.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Room-backed session repository exposing StateFlow<List<Session>>.
 * All tabs (Today, History, Reports, Settings) react immediately to the same data changes.
 * The active (in-progress) fast is persisted in Room so it survives app kill and device reboot.
 */
@Suppress("unused")
class SessionRepository(
    private val dao: SessionDao,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val sessions: StateFlow<List<Session>> = dao.getAllSessions()
        .map { entities -> entities.map { it.toSession() } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // First launch is clean by design (spec §4): no sample data is ever preloaded,
    // and no code path erases existing data at launch.

    /**
     * Starts a new fasting session with null end timestamp.
     * Any previously active session is removed to ensure only one active session at a time.
     */
    fun startFast(startTime: Instant = Instant.now()) {
        scope.launch {
            dao.deleteActiveSessions()
            dao.insert(
                SessionEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    startTimeMillis = startTime.toEpochMilli(),
                    endTimeMillis = null,
                ),
            )
        }
    }

    /**
     * Stops the active fasting session and records the end timestamp.
     */
    fun stopFast(endTime: Instant = Instant.now()) {
        scope.launch {
            val active = dao.getActiveSessionSync() ?: return@launch
            dao.update(active.copy(endTimeMillis = endTime.toEpochMilli()))
        }
    }

    /**
     * Adds a newly created session from History screen.
     */
    fun addSession(session: Session) {
        scope.launch {
            dao.insert(SessionEntity.fromSession(session))
        }
    }

    /**
     * Updates an edited session.
     */
    fun updateSession(session: Session) {
        scope.launch {
            dao.update(SessionEntity.fromSession(session))
        }
    }

    /**
     * Deletes a session by ID.
     */
    fun deleteSession(sessionId: String) {
        scope.launch {
            dao.deleteById(sessionId)
        }
    }

    /**
     * Restores a deleted session (e.g. from Undo snackbar).
     */
    fun restoreSession(session: Session) {
        scope.launch {
            dao.insert(SessionEntity.fromSession(session))
        }
    }
}
