package com.ruinobrega.fastinator

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ruinobrega.fastinator.data.AppDatabase
import com.ruinobrega.fastinator.data.SessionRepository
import com.ruinobrega.fastinator.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

  private lateinit var db: AppDatabase
  private lateinit var repository: SessionRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = SessionRepository(db.sessionDao())
  }

  @Test
  fun sessionCalculates13hTargetAndOverAchievement() {
    val start = Instant.parse("2026-11-01T00:00:00Z")
    val activeSession = Session(start = start, end = null)

    assertTrue(activeSession.isActive)
    assertNull(activeSession.end)

    // Target reached at exactly 13h later
    val expectedTargetReached = start + Duration.ofHours(13)
    assertEquals(expectedTargetReached, activeSession.targetReachedAt)

    // At start (0 elapsed)
    assertEquals(0, activeSession.achievementPercent(start))
    assertEquals("00:00", activeSession.formattedElapsed(start))

    // At 13 hours (100% achievement)
    assertEquals(100, activeSession.achievementPercent(expectedTargetReached))
    assertEquals("13:00", activeSession.formattedElapsed(expectedTargetReached))

    // At 14 hours 18 minutes (110% achievement)
    val overTargetTime = start.plus(Duration.ofHours(14)).plus(Duration.ofMinutes(18))
    val overPercent = activeSession.achievementPercent(overTargetTime)
    assertTrue("Achievement should exceed 100%", overPercent > 100)
    assertEquals(110, overPercent)
    assertEquals("14:18", activeSession.formattedElapsed(overTargetTime))
  }

  @Test
  fun sessionRepositoryStartsStopsAndNotifiesReactivity() = runTest {
    // Wait for initial empty state
    val initial = repository.sessions.first()
    assertEquals(0, initial.size)

    // Start fast
    val startTime = Instant.now()
    repository.startFast(startTime)
    val sessionsAfterStart = repository.sessions.first { it.isNotEmpty() }
    assertEquals(1, sessionsAfterStart.size)
    val active = sessionsAfterStart.first()
    assertTrue(active.isActive)
    assertNull(active.end)

    // Stop fast (truncate to millis since Room stores epoch millis)
    val stopTime = Instant.ofEpochMilli(
      startTime.plus(Duration.ofHours(13).plusMinutes(30)).toEpochMilli()
    )
    repository.stopFast(stopTime)
    // Wait for the update to propagate through the Flow
    val sessionsAfterStop = repository.sessions.first { sessions ->
      sessions.isNotEmpty() && sessions.first().end != null
    }
    assertEquals(1, sessionsAfterStop.size)
    val completed = sessionsAfterStop.first()
    assertFalse(completed.isActive)
    assertNotNull(completed.end)
    assertEquals(stopTime, completed.end)
    assertEquals(103, completed.achievementPercent(stopTime))
  }

  @Test
  fun repositoryCrudAddUpdateDeleteRestoreEmitsReactivity() = runTest {
    // Spec §3.2: the user can add, edit, and delete sessions freely; every change
    // must propagate through the shared reactive flow (spec §3.5).
    val start = Instant.parse("2026-11-10T20:00:00Z")
    val session = Session(id = "crud-1", start = start, end = start.plus(Duration.ofHours(14)))

    repository.addSession(session)
    var list = repository.sessions.first { it.isNotEmpty() }
    assertEquals(1, list.size)
    assertEquals("crud-1", list.first().id)

    val updated = session.copy(end = start.plus(Duration.ofHours(15)))
    repository.updateSession(updated)
    list = repository.sessions.first { it.isNotEmpty() && it.first().end == updated.end }
    assertEquals(15 * 3600L, list.first().getElapsedSeconds())

    repository.deleteSession("crud-1")
    repository.sessions.first { it.isEmpty() }

    repository.restoreSession(updated)
    list = repository.sessions.first { it.isNotEmpty() }
    assertEquals(1, list.size)
    assertEquals(15 * 3600L, list.first().getElapsedSeconds())
  }

  @Test
  fun sessionRepositoryPersistsAcrossInstances() = runTest {
    // Start a fast in the first repository instance (truncate to millis for Room storage)
    val startTime = Instant.ofEpochMilli(Instant.now().toEpochMilli())
    repository.startFast(startTime)
    repository.sessions.first { it.isNotEmpty() }

    // Create a new repository instance pointing at the same database (simulates app restart)
    val newRepository = SessionRepository(db.sessionDao())
    val sessions = newRepository.sessions.first { it.isNotEmpty() }
    assertEquals(1, sessions.size)
    assertTrue("Active session should survive repository recreation", sessions.first().isActive)
    assertEquals(startTime.toEpochMilli(), sessions.first().startTimeMillis)
  }
}
