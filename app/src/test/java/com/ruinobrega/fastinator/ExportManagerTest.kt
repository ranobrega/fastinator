package com.ruinobrega.fastinator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ruinobrega.fastinator.data.ExportManager
import com.ruinobrega.fastinator.model.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportManagerTest {

  private fun manager(context: Context): ExportManager =
    ExportManager(context, MutableStateFlow(emptyList()))

  @Test
  fun exportWritesSchemaCompliantJsonForAllScopes() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // 2026-11-02 20:15Z -> 2026-11-03 11:45Z = 15.5h elapsed = 119.23% of the 13h target
    val session = Session(
      id = "test-1",
      start = Instant.parse("2026-11-02T20:15:00Z"),
      end = Instant.parse("2026-11-03T11:45:00Z"),
    )

    manager(context).exportNow(listOf(session))

    val dir = ExportManager.exportDir(context)
    for (scope in listOf("day", "week", "month", "all")) {
      val file = File(dir, "fastinator-$scope-latest.json")
      assertTrue("missing export file for scope $scope", file.exists())
      val doc = JSONObject(file.readText())
      assertEquals("fastinator/v1", doc.getString("schema"))
      assertEquals(scope, doc.getString("scope"))
      assertTrue(doc.getString("exportedAt").endsWith("Z"))
    }

    val all = JSONObject(File(dir, "fastinator-all-latest.json").readText())
    val records = all.getJSONArray("records")
    assertEquals(1, records.length())
    val rec = records.getJSONObject(0)
    val expectedDate = session.start.atZone(ZoneId.systemDefault()).toLocalDate().toString()
    assertEquals(expectedDate, rec.getString("date"))
    assertEquals(13, rec.getInt("targetHours"))
    assertEquals(15.5, rec.getDouble("elapsedHours"), 0.001)
    assertEquals(119.23, rec.getDouble("achievementPercent"), 0.001)
    assertEquals(2.5, rec.getDouble("achievementHours"), 0.001)
    // ISO 8601 with local timezone offset
    assertTrue(rec.getString("start").matches(Regex(".*[+-]\\d{2}:\\d{2}|Z")))
    assertTrue(rec.getString("end").matches(Regex(".*[+-]\\d{2}:\\d{2}|Z")))

    // A timestamped full-history file is written as well
    val timestamped = dir.listFiles { f -> f.name.matches(Regex("fastinator-all-\\d{8}-\\d{6}\\.json")) }
    assertTrue(timestamped != null && timestamped.isNotEmpty())
  }

  @Test
  fun dayScopeContainsOnlySessionsStartedToday() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todaySession = Session(
      id = "today",
      start = today.atTime(8, 0).atZone(zone).toInstant(),
      end = today.atTime(20, 0).atZone(zone).toInstant(),
    )
    val oldSession = Session(
      id = "old",
      start = Instant.parse("2026-11-02T20:15:00Z"),
      end = Instant.parse("2026-11-03T11:45:00Z"),
    )

    manager(context).exportNow(listOf(todaySession, oldSession))

    val dir = ExportManager.exportDir(context)
    val dayDoc = JSONObject(File(dir, "fastinator-day-latest.json").readText())
    assertEquals(1, dayDoc.getJSONArray("records").length())
    assertEquals(today.toString(), dayDoc.getJSONArray("records").getJSONObject(0).getString("date"))
    val allDoc = JSONObject(File(dir, "fastinator-all-latest.json").readText())
    assertEquals(2, allDoc.getJSONArray("records").length())
  }

  @Test
  fun startExportsAutomaticallyOnEveryDataChange() = runBlocking {
    // Spec §3.6: exports are written automatically whenever the data changes —
    // no user intervention. Uses real dispatchers because ExportManager writes on IO.
    val context = ApplicationProvider.getApplicationContext<Context>()
    val flow = MutableStateFlow<List<Session>>(emptyList())
    val manager = ExportManager(context, flow)
    manager.start()
    try {
      val file = File(ExportManager.exportDir(context), "fastinator-all-latest.json")
      assertTrue(!file.exists())

      flow.value = listOf(
        Session(id = "auto-1", start = Instant.parse("2026-11-02T20:15:00Z"), end = Instant.parse("2026-11-03T11:45:00Z"))
      )
      val deadline = System.currentTimeMillis() + 10_000
      while (!file.exists() && System.currentTimeMillis() < deadline) delay(50)

      assertTrue("export must be written automatically on data change", file.exists())
      val doc = JSONObject(file.readText())
      assertEquals("fastinator/v1", doc.getString("schema"))
      assertEquals(1, doc.getJSONArray("records").length())

      // A second change rewrites the export
      flow.value = emptyList()
      val deadline2 = System.currentTimeMillis() + 10_000
      while (doc.getJSONArray("records").length() == 1 && System.currentTimeMillis() < deadline2) {
        delay(50)
        if (file.exists()) {
          val updated = JSONObject(file.readText())
          if (updated.getJSONArray("records").length() == 0) break
        }
      }
      assertEquals(0, JSONObject(file.readText()).getJSONArray("records").length())
    } finally {
      manager.stop()
    }
  }

  @Test
  fun activeSessionExportsWithNullEnd() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val active = Session(
      id = "active",
      start = Instant.parse("2026-11-02T20:15:00Z"),
      end = null,
    )

    manager(context).exportNow(listOf(active))

    val rec = JSONObject(File(ExportManager.exportDir(context), "fastinator-all-latest.json").readText())
      .getJSONArray("records").getJSONObject(0)
    assertTrue(rec.isNull("end"))
    assertEquals(13, rec.getInt("targetHours"))
  }
}
