package com.raghava.unlockaudit.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RetentionPolicyTest {
    private val now = LocalDate.of(2026, 11, 21)
    private val halifax = ZoneId.of("America/Halifax")

    @Test fun thirtyDaysIncludesExactlyThirtyLocalDates() {
        val boundary = RetentionPolicy.cutoffMillis(30, now, halifax)!!
        val first = java.time.Instant.ofEpochMilli(boundary).atZone(halifax).toLocalDate()
        assertEquals(LocalDate.of(2026, 10, 23), first)
        assertEquals(30, java.time.temporal.ChronoUnit.DAYS.between(first, now) + 1)
    }

    @Test fun foreverKeepsAllHistory() {
        assertNull(RetentionPolicy.cutoffMillis(0, now, halifax))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedRetentionIsRejected() {
        RetentionPolicy.cutoffMillis(-2, now, halifax)
    }

    @Test fun midnightBoundaryUsesLocalTime() {
        val boundary = RetentionPolicy.cutoffMillis(30, now, halifax)!!
        assertEquals(0, java.time.Instant.ofEpochMilli(boundary).atZone(halifax).hour)
    }
}
