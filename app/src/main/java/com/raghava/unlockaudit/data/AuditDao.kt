package com.raghava.unlockaudit.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: AuditSession): Long

    @Insert
    suspend fun insertEvents(events: List<AuditAppEvent>)

    @Transaction
    @Query("""SELECT * FROM sessions WHERE localDate = :date AND (:search = '' OR EXISTS (
        SELECT 1 FROM app_events e WHERE e.sessionId = sessions.id
        AND (instr(lower(e.appLabel), lower(:search)) > 0 OR instr(lower(e.packageName), lower(:search)) > 0)
    )) ORDER BY startedAt DESC""")
    fun sessionsForDay(date: String, search: String): Flow<List<SessionWithEvents>>

    @Query("""
        SELECT s.localDate AS localDate, COUNT(*) AS sessionCount,
          COALESCE(SUM(CASE WHEN s.interrupted = 0 THEN MAX(0, s.endedAt - s.startedAt) ELSE 0 END), 0) AS totalDurationMs,
          (SELECT COUNT(DISTINCT e.packageName) FROM app_events e
           INNER JOIN sessions x ON x.id = e.sessionId WHERE x.localDate = s.localDate) AS appCount
        FROM sessions s
        WHERE (:date = '' OR s.localDate = :date)
          AND (:search = '' OR EXISTS (
            SELECT 1 FROM app_events e
            WHERE e.sessionId = s.id AND (instr(lower(e.appLabel), lower(:search)) > 0 OR instr(lower(e.packageName), lower(:search)) > 0)
          ))
        GROUP BY s.localDate ORDER BY s.localDate DESC
    """)
    fun days(date: String, search: String): Flow<List<DaySummary>>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun countSessions(): Int

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    suspend fun allSessions(): List<AuditSession>

    @Query("SELECT * FROM app_events WHERE sessionId IN (:ids) ORDER BY occurredAt ASC")
    suspend fun eventsForIds(ids: List<Long>): List<AuditAppEvent>

    @Query("DELETE FROM sessions WHERE startedAt < :before")
    suspend fun pruneBefore(before: Long): Int

    @Query("DELETE FROM sessions")
    suspend fun clearAll()
}
