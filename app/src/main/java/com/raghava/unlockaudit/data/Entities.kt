package com.raghava.unlockaudit.data

import androidx.room.*

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["localDate", "startedAt"]), Index(value = ["importKey"], unique = true)]
)
data class AuditSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val localDate: String,
    val interrupted: Boolean = false,
    val source: String = "live",
    val importKey: String? = null
)

@Entity(
    tableName = "app_events",
    foreignKeys = [ForeignKey(
        entity = AuditSession::class, parentColumns = ["id"],
        childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE
    )], indices = [Index("sessionId"), Index("packageName")]
)
data class AuditAppEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val occurredAt: Long,
    val packageName: String,
    val appLabel: String
)

data class SessionWithEvents(
    @Embedded val session: AuditSession,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val events: List<AuditAppEvent>
)

data class DaySummary(
    val localDate: String,
    val sessionCount: Int,
    val totalDurationMs: Long,
    val appCount: Int
)
