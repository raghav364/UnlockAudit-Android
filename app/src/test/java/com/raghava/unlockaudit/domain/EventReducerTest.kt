package com.raghava.unlockaudit.domain

import org.junit.Assert.*
import org.junit.Test

class EventReducerTest {
    @Test fun ordersFiltersAndDeduplicates() {
        val events = listOf(
            RawUsageEvent(300, "com.b", 1),
            RawUsageEvent(110, "com.a", 1),
            RawUsageEvent(150, "com.a", 1),
            RawUsageEvent(200, "com.mine", 1),
            RawUsageEvent(250, "com.b", 2),
            RawUsageEvent(400, "com.c", 1)
        )
        assertEquals(listOf("com.a", "com.b"),
            EventReducer.foregroundTransitions(events, 100, 350, "com.mine").map { it.packageName })
    }
    @Test fun zeroDurationSessionHasNoEvents() {
        assertTrue(EventReducer.foregroundTransitions(
            listOf(RawUsageEvent(10, "com.a", 1)), 10, 10, "com.mine").isEmpty())
    }
}
