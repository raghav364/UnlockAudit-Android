package com.raghava.unlockaudit.service

import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import android.os.KeyguardManager
import android.os.PowerManager
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.raghava.unlockaudit.MainActivity
import com.raghava.unlockaudit.R
import com.raghava.unlockaudit.data.AuditRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

/** User-started, transparent foreground session journal. No hidden tracking or network traffic. */
class TrackerService : Service() {
    private sealed interface Command {
        data object Unlock : Command
        data object ScreenOff : Command
        data object Stop : Command
        data object Recover : Command
    }
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: AuditRepository
    private lateinit var prefs: android.content.SharedPreferences
    private var receiver: BroadcastReceiver? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        repository = AuditRepository(applicationContext)
        prefs = getSharedPreferences("audit", MODE_PRIVATE)
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        val listeningReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> commands.trySend(Command.Unlock)
                    Intent.ACTION_SCREEN_OFF -> commands.trySend(Command.ScreenOff)
                }
            }
        }
        registerReceiver(listeningReceiver, filter)
        receiver = listeningReceiver
        scope.launch {
            for (command in commands) {
                try {
                    when (command) {
                        Command.Unlock -> beginSession()
                        Command.ScreenOff -> {
                            if (repository.hasUsageAccess()) finishSession(false)
                            else {
                                // A revoked special permission must not leave a misleading
                                // "tracking active" state with empty app records.
                                finishSession(true)
                                repository.setTrackingRequested(false)
                                isRunning.value = false
                                if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf()
                                commands.close()
                                break
                            }
                        }
                        Command.Recover -> recoverPending()
                        Command.Stop -> {
                            finishSession(false)
                            repository.setTrackingRequested(false)
                            isRunning.value = false
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            commands.close()
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Tracking event failed (no private data logged)", e)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            commands.trySend(Command.Stop)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START && !repository.requestedTracking()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!repository.hasUsageAccess()) {
            repository.setTrackingRequested(false)
            isRunning.value = false
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            if (!foregroundStarted) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification(),
                    if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
                )
                foregroundStarted = true
                repository.setTrackingRequested(true)
                isRunning.value = true
                commands.trySend(Command.Recover)
            }
        } catch (e: Exception) {
            // Includes missing permission or denied foreground-service start.
            Log.e(TAG, "Cannot start foreground tracking", e)
            repository.setTrackingRequested(false)
            isRunning.value = false
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private suspend fun recoverPending() {
        val oldStart = prefs.getLong("start", 0)
        if (oldStart > 0L) {
            // An old or interrupted running session cannot have a verified end time.
            // It is safely closed as interrupted, and excluded from duration totals.
            finishSession(true)
        }
        val power = getSystemService(PowerManager::class.java)
        val keys = getSystemService(KeyguardManager::class.java)
        if (power.isInteractive && !keys.isKeyguardLocked) beginSession()
    }

    private fun beginSession() {
        if (prefs.getLong("start", 0L) == 0L) {
            prefs.edit().putLong("start", System.currentTimeMillis()).commit()
        }
    }

    private suspend fun finishSession(interrupted: Boolean) {
        val start = prefs.getLong("start", 0L)
        if (start <= 0) return
        val end = System.currentTimeMillis()
        if (end > start) repository.saveSession(start, end, interrupted)
        // Clear only after the durable Room write. On restart a unique live
        // importKey prevents duplicate writes if termination interrupted cleanup.
        prefs.edit().remove("start").commit()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Unlock Audit tracking", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Visible indicator while your unlock history is being recorded" })
    }

    private fun notification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, TrackerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Unlock Audit is recording")
            .setContentText("Unlock sessions are stored locally on your phone")
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_notification, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        receiver?.let { try { unregisterReceiver(it) } catch (_: IllegalArgumentException) {} }
        commands.close()
        scope.cancel()
        isRunning.value = false
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "UnlockAuditService"
        private const val CHANNEL_ID = "audit_channel_v2"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.raghava.unlockaudit.START"
        const val ACTION_STOP = "com.raghava.unlockaudit.STOP"
        val isRunning = MutableStateFlow(false)
    }
}
