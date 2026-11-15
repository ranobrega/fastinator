package com.ruinobrega.fastinator.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ruinobrega.fastinator.MainActivity
import com.ruinobrega.fastinator.R
import com.ruinobrega.fastinator.data.AppDatabase
import com.ruinobrega.fastinator.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Spec §8.1 — persistent fasting notification.
 *
 * Foreground service that runs only while a fast is active. Shows exactly one
 * ongoing, silent (LOW importance) notification with the live elapsed time in
 * HH:MM and the achievement % in 0.00% format, refreshed once per minute.
 * The chronometer is deliberately NOT used: it renders seconds (H:MM:SS) and
 * offers no format control, which would violate the global HH:MM rule.
 *
 * Elapsed is always derived from the stored start instant (doze/process-death
 * safe). Tap opens the app; when the fast ends the notification is removed
 * (stopForeground with remove) and the service stops — it never outlives the session.
 */
class FastingService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground service contract: call startForeground promptly, exactly one notification.
        scope.launch {
            startForeground(NOTIFICATION_ID, buildNotification(readActiveSession()))
        }
        startTicker()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickerJob?.cancel()
        scope.cancel()
        // Spec §8.1: on stop, the notification is removed (not just detached).
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createChannel() {
        // LOW importance: silent (no sound/vibration/heads-up), visible in shade and lock screen.
        val channel = NotificationChannel(CHANNEL_ID, "Fasting status", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        notificationManager.createNotificationChannel(channel)
    }

    private suspend fun readActiveSession(): Session? =
        AppDatabase.getInstance(this).sessionDao().getActiveSessionSync()?.toSession()

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                val session = readActiveSession()
                if (session == null) {
                    // Session ended (or was deleted) — the service must not outlive it.
                    stopSelf()
                    return@launch
                }
                // Once per minute, same cadence as the ring's minute-grained data. No per-second updates.
                notificationManager.notify(NOTIFICATION_ID, buildNotification(session))
                delay(TICK_MS)
            }
        }
    }

    private fun buildNotification(session: Session?): Notification {
        val text = if (session != null) {
            notificationText(session, System.currentTimeMillis())
        } else {
            "Fasting in progress"
        }
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fasting)
            .setContentTitle("Fasting in progress")
            .setContentText(text)
            .setOngoing(true) // not dismissible while the fast is active
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // visible on lock screen
            .setContentIntent(pendingIntent) // tap opens the app (Today screen)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "fasting_status"
        const val NOTIFICATION_ID = 1001
        const val TICK_MS = 60_000L
    }
}

/**
 * Notification body text: elapsed HH:MM + achievement 0.00% (spec §8.1 — no seconds).
 */
fun notificationText(session: Session, nowMillis: Long): String {
    val now = Instant.ofEpochMilli(nowMillis)
    val elapsed = session.formattedElapsed(now) // HH:MM
    val percent = Session.formatPercentage(session.achievementPercentFloat(now))
    return "Elapsed $elapsed · $percent"
}

fun Context.startFastingService() {
    val intent = Intent(this, FastingService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(this, intent)
    } else {
        startService(intent)
    }
}

fun Context.stopFastingService() {
    stopService(Intent(this, FastingService::class.java))
}
