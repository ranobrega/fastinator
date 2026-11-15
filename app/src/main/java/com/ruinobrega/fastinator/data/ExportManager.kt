package com.ruinobrega.fastinator.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ruinobrega.fastinator.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Automatic JSON export (spec §3.6).
 *
 * Observes the shared session StateFlow and, on every change, writes fastinator/v1
 * JSON documents to the app's Fastinator export folder:
 * - one rolling latest snapshot per scope (day / week / month / all)
 * - one timestamped full-history file per export round
 *
 * [shareLatestExport] opens the Android share sheet for the most recently written file.
 */
class ExportManager(
    context: Context,
    private val sessions: StateFlow<List<Session>>,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    fun start() {
        if (job == null) {
            job = scope.launch { sessions.collect { exportNow(it) } }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Writes all scope exports for the given session list. Public so tests can call it directly.
     */
    suspend fun exportNow(allSessions: List<Session>) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val month = YearMonth.from(today)

        fun dayOf(s: Session): LocalDate = s.start.atZone(zone).toLocalDate()

        val scopes: Map<String, List<Session>> = mapOf(
            "day" to allSessions.filter { dayOf(it) == today },
            "week" to allSessions.filter { !dayOf(it).isBefore(sunday) && !dayOf(it).isAfter(sunday.plusDays(6)) },
            "month" to allSessions.filter { YearMonth.from(dayOf(it)) == month },
            "all" to allSessions.sortedByDescending { it.startTimeMillis },
        )

        val dir = exportDir(appContext)
        dir.mkdirs()
        for ((scopeName, records) in scopes) {
            writeAtomically(File(dir, "fastinator-$scopeName-latest.json"), buildDocument(scopeName, records))
        }
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
            .withZone(zone)
            .format(Instant.now())
        writeAtomically(File(dir, "fastinator-all-$stamp.json"), buildDocument("all", scopes.getValue("all")))
    }

    private fun buildDocument(scopeName: String, records: List<Session>): JSONObject {
        val exportedAt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        return JSONObject().apply {
            put("schema", SCHEMA)
            put("exportedAt", exportedAt)
            put("scope", scopeName)
            put("records", JSONArray().apply { records.forEach { put(recordJson(it)) } })
        }
    }

    private fun recordJson(s: Session): JSONObject {
        val zone = ZoneId.systemDefault()
        val start = s.start.atZone(zone)
        val elapsedSeconds = s.elapsedDuration().seconds
        val elapsedHours = elapsedSeconds / 3600.0
        val achievementPercent = elapsedSeconds.toDouble() / Session.TARGET_SECONDS.toDouble() * 100.0
        val achievementHours = maxOf(0.0, elapsedHours - Session.TARGET_HOURS.toDouble())
        return JSONObject().apply {
            put("date", start.toLocalDate().toString())
            put("start", start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("end", s.end?.atZone(zone)?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) ?: JSONObject.NULL)
            put("targetHours", Session.TARGET_HOURS)
            put("elapsedHours", round2(elapsedHours))
            put("achievementPercent", round2(achievementPercent))
            put("achievementHours", round2(achievementHours))
        }
    }

    private fun round2(value: Double): Double = String.format(Locale.US, "%.2f", value).toDouble()

    private fun writeAtomically(file: File, doc: JSONObject) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(doc.toString(2))
        tmp.renameTo(file)
    }

    companion object {
        const val SCHEMA = "fastinator/v1"

        fun exportDir(context: Context): File {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            return File(base, "Fastinator/exports")
        }
    }
}

/**
 * Opens the standard Android share sheet for the latest exported JSON file (spec §3.6).
 */
fun shareLatestExport(context: Context) {
    val dir = ExportManager.exportDir(context)
    val file = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share Fastinator export"))
}
