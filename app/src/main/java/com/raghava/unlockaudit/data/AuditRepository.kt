package com.raghava.unlockaudit.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.room.withTransaction
import com.raghava.unlockaudit.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AuditRepository(private val context: Context) {
    private val database = AuditDatabase.get(context)
    private val dao = database.dao()
    private val legacy = context.getSharedPreferences("audit", Context.MODE_PRIVATE)
    private val settings = context.getSharedPreferences("settings_v2", Context.MODE_PRIVATE)

    fun days(date: String = "", search: String = ""): Flow<List<DaySummary>> = dao.days(date, search)
    fun sessionsForDay(date: String, search: String = ""): Flow<List<SessionWithEvents>> = dao.sessionsForDay(date, search)
    fun retentionDays(): Int = settings.getInt("retention_days", 365)
    fun requestedTracking(): Boolean = settings.getBoolean("tracking_requested", false)
    fun setTrackingRequested(value: Boolean) {
        settings.edit().putBoolean("tracking_requested", value).commit()
    }
    fun migrationProblemCount(): Int = settings.getInt("migration_rejected", 0)
    fun hasUnparsedLegacy(): Boolean = migrationProblemCount() > 0
    fun clearMigrationWarning() { settings.edit().putInt("migration_rejected", 0).apply() }

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        // An 'allowed' AppOp is required; MODE_DEFAULT isn't a grant.
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun appLabel(packageName: String): String = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    } catch (_: SecurityException) {
        packageName
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        migrateLegacyOnce()
        pruneExpired()
    }

    /** Idempotent per importKey, with DB writes in a single transaction. */
    private suspend fun migrateLegacyOnce() {
        if (settings.getBoolean("legacy_migration_attempted", false)) return
        val raw = legacy.getString("history", "").orEmpty()
        val result = LegacyHistoryParser.parse(raw)
        database.withTransaction {
            result.sessions.forEach { entry ->
                val id = dao.insertSession(
                    AuditSession(
                        startedAt = entry.start,
                        endedAt = entry.end,
                        localDate = dateKey(entry.start),
                        source = "legacy",
                        importKey = entry.importKey
                    )
                )
                if (id != -1L) {
                    dao.insertEvents(entry.events.map { item ->
                        AuditAppEvent(
                            sessionId = id, occurredAt = item.timestamp,
                            packageName = item.packageName,
                            appLabel = appLabel(item.packageName)
                        )
                    })
                }
            }
        }
        // Mark after a successful transaction. Every importKey is unique in Room,
        // so a crash before this write won't create duplicate sessions on retry.
        settings.edit()
            .putBoolean("legacy_migration_attempted", true)
            .putInt("migration_rejected", result.rejected.size)
            .commit()
        // If everything parsed, free old plaintext. Otherwise keep the entire
        // original history for a manual recovery/export; never silently drop it.
        if (result.rejected.isEmpty()) legacy.edit().remove("history").apply()
    }

    suspend fun saveSession(start: Long, end: Long, interrupted: Boolean = false) =
        withContext(Dispatchers.IO) {
            if (start <= 0L || end <= start) return@withContext
            val transitions = if (!interrupted && hasUsageAccess()) {
                val usage = context.getSystemService(UsageStatsManager::class.java)
                val stream = usage.queryEvents(start, end)
                val raw = mutableListOf<RawUsageEvent>()
                if (stream != null) {
                    val item = android.app.usage.UsageEvents.Event()
                    while (stream.hasNextEvent()) {
                        if (!stream.getNextEvent(item)) break
                        if (item.eventType == EventReducer.ACTIVITY_RESUMED) {
                            raw.add(RawUsageEvent(item.timeStamp, item.packageName, item.eventType))
                        }
                    }
                }
                EventReducer.foregroundTransitions(raw, start, end, context.packageName)
            } else emptyList()
            // The mutex protects Delete All against an in-flight screen-off event.
            // Disabling tracking before erasing data prevents a late save reappearing.
            mutationMutex.withLock {
                if (requestedTracking()) {
                    database.withTransaction {
                        val sessionId = dao.insertSession(
                            AuditSession(
                                startedAt = start, endedAt = end,
                                localDate = dateKey(start), interrupted = interrupted,
                                importKey = "live:$start"
                            )
                        )
                        if (sessionId != -1L) {
                            dao.insertEvents(transitions.map { item ->
                                AuditAppEvent(
                                    sessionId = sessionId, occurredAt = item.timestamp,
                                    packageName = item.packageName, appLabel = appLabel(item.packageName)
                                )
                            })
                        }
                    }
                }
            }
            if (requestedTracking()) pruneExpired()
        }

    suspend fun pruneExpired(): Int = withContext(Dispatchers.IO) {
        val boundary = RetentionPolicy.cutoffMillis(
            retentionDays(), LocalDate.now(), ZoneId.systemDefault()
        ) ?: return@withContext 0
        dao.pruneBefore(boundary)
    }

    suspend fun setRetentionDays(days: Int) = withContext(Dispatchers.IO) {
        require(days in RetentionPolicy.supportedDays)
        settings.edit().putInt("retention_days", days).commit()
        pruneExpired()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        // Stop new sessions and invalidate any pending unlock before erasing.
        setTrackingRequested(false)
        check(legacy.edit().remove("start").commit()) { "Could not discard pending session" }
        mutationMutex.withLock {
            database.withTransaction { dao.clearAll() }
            check(legacy.edit().remove("history").commit()) { "Could not erase legacy history" }
            check(settings.edit().putBoolean("legacy_migration_attempted", true)
                .putInt("migration_rejected", 0).commit()) { "Could not save erasure state" }
        }
    }

    suspend fun exportLegacyText(uri: android.net.Uri) = withContext(Dispatchers.IO) {
        val original = legacy.getString("history", null)
            ?: throw IllegalStateException("No original legacy history remains")
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException("Cannot open document")
        output.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(original) }
        }
    }

    suspend fun exportCsv(uri: android.net.Uri): Int = withContext(Dispatchers.IO) {
        val sessions = dao.allSessions()
        val records = mutableListOf<ExportRecord>()
        for (chunk in sessions.chunked(200)) {
            val events = dao.eventsForIds(chunk.map { it.id }).groupBy { it.sessionId }
            for (session in chunk) {
                val apps = events[session.id].orEmpty()
                if (apps.isEmpty()) records.add(ExportRecord(
                    session.startedAt, session.endedAt, session.interrupted, session.source, "", "", null
                ))
                else apps.forEach { app -> records.add(ExportRecord(
                    session.startedAt, session.endedAt, session.interrupted, session.source,
                    app.packageName, app.appLabel, app.occurredAt
                )) }
            }
        }
        val stream = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException("Cannot open the selected document")
        stream.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { CsvWriter.write(it, records) }
        }
        records.size
    }

    companion object {
        private val mutationMutex = Mutex()
        fun dateKey(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }
}
