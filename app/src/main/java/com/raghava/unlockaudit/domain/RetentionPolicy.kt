package com.raghava.unlockaudit.domain

import java.time.LocalDate
import java.time.ZoneId

/** First millisecond to retain. A finite period includes the current local calendar day. */
object RetentionPolicy {
    val supportedDays = setOf(0, 30, 90, 365)

    fun cutoffMillis(days: Int, today: LocalDate, zone: ZoneId): Long? {
        require(days in supportedDays) { "Unsupported history retention: $days" }
        if (days == 0) return null
        return today.minusDays(days.toLong() - 1L)
            .atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
