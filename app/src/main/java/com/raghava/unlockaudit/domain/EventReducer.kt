package com.raghava.unlockaudit.domain

// ACTIVITY_RESUMED is 1 on Android API 29+, same numerical event v1.1 used.
data class RawUsageEvent(val timestamp: Long, val packageName: String?, val type: Int)
data class ForegroundAppEvent(val timestamp: Long, val packageName: String)

object EventReducer {
    const val ACTIVITY_RESUMED = 1

    fun foregroundTransitions(
        events: List<RawUsageEvent>,
        start: Long, end: Long, ownPackage: String
    ): List<ForegroundAppEvent> {
        if (end <= start) return emptyList()
        var previous = ""
        return events.asSequence()
            .filter { it.type == ACTIVITY_RESUMED && it.timestamp in start..end }
            .sortedBy { it.timestamp }
            .mapNotNull { e ->
                val pkg = e.packageName ?: return@mapNotNull null
                if (pkg.isBlank() || pkg == ownPackage || pkg == previous) null
                else {
                    previous = pkg
                    ForegroundAppEvent(e.timestamp, pkg)
                }
            }.toList()
    }
}
