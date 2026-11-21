package com.raghava.unlockaudit

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raghava.unlockaudit.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires an Android emulator/device: NOT EXECUTED in source-only environment. */
@RunWith(AndroidJUnit4::class)
class AuditDaoInstrumentedTest {
    @Test fun dayGroupingAndCascadeDeletion() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java).build()
        try {
            val dao = db.dao()
            val s1 = dao.insertSession(AuditSession(startedAt = 1000, endedAt = 61000, localDate = "2026-11-21"))
            val s2 = dao.insertSession(AuditSession(startedAt = 200000, endedAt = 260000, localDate = "2026-11-21"))
            dao.insertEvents(listOf(
                AuditAppEvent(sessionId = s1, occurredAt = 3000, packageName = "com.one", appLabel = "One"),
                AuditAppEvent(sessionId = s1, occurredAt = 5000, packageName = "com.two", appLabel = "Two"),
                AuditAppEvent(sessionId = s2, occurredAt = 210000, packageName = "com.one", appLabel = "One")
            ))
            val first = dao.days("", "").first().single()
            assertEquals(2, first.sessionCount)
            assertEquals(120000, first.totalDurationMs)
            assertEquals(2, first.appCount)
            assertEquals(1, dao.sessionsForDay("2026-11-21", "Two").first().size)
            dao.clearAll()
            assertTrue(dao.sessionsForDay("2026-11-21", "").first().isEmpty())
            assertTrue(dao.eventsForIds(listOf(s1, s2)).isEmpty())
        } finally { db.close() }
    }

    @Test fun uniqueLiveKeyPreventsReinsertionAfterCrash() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java).build()
        try {
            val dao = db.dao()
            val original = AuditSession(startedAt = 100, endedAt = 200, localDate = "2026-11-21", importKey = "live:100")
            assertTrue(dao.insertSession(original) > 0)
            assertEquals(-1L, dao.insertSession(original))
        } finally { db.close() }
    }
}
