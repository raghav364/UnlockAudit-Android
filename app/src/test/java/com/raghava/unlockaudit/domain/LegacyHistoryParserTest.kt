package com.raghava.unlockaudit.domain

import org.junit.Assert.*
import org.junit.Test
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class LegacyHistoryParserTest {
    @Test fun importsOriginalV11SessionFormat() {
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.CANADA)
        val start = 1_795_278_000_000L
        val end = start + 120_000L
        val sample = "Session: ${fmt.format(Date(start))} to ${fmt.format(Date(end))}\n" +
            "${fmt.format(Date(start + 60_000L))}  com.example.reader\n\n"
        val result = LegacyHistoryParser.parse(sample)
        assertTrue(result.rejected.toString(), result.rejected.isEmpty())
        assertEquals(1, result.sessions.size)
        assertEquals("com.example.reader", result.sessions.single().events.single().packageName)
    }

    @Test fun incompleteRecordIsKeptForRecovery() {
        val result = LegacyHistoryParser.parse("Session: nonsense to nonsense\n")
        assertTrue(result.sessions.isEmpty())
        assertEquals(1, result.rejected.size)
    }

    @Test fun parserKeyIsDeterministic() {
        val record = "Session: Invalid to Invalid"
        assertEquals(LegacyHistoryParser.parse(record), LegacyHistoryParser.parse(record))
    }
}
