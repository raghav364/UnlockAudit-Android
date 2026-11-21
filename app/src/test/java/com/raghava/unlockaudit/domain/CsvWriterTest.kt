package com.raghava.unlockaudit.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter
import java.time.ZoneId

class CsvWriterTest {
    @Test fun escapesFormulaInjectionAndQuotes() {
        val writer = StringWriter()
        CsvWriter.write(writer, listOf(ExportRecord(
            start = 0, end = 10_000, interrupted = false, source = "live",
            packageName = "com.example", appLabel = "=HYPERLINK(\"bad\")", eventAt = 1_000
        )), ZoneId.of("UTC"))
        val csv = writer.toString()
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"bad\"\")\""))
        assertTrue(csv.startsWith("session_start,session_end"))
    }
    @Test fun handlesLeadingWhitespaceBeforeFormula() {
        val writer = StringWriter()
        CsvWriter.write(writer, listOf(ExportRecord(
            start = 0, end = 10_000, interrupted = false, source = "live",
            packageName = "com.example", appLabel = "  \n=2+3", eventAt = null
        )), ZoneId.of("UTC"))
        assertTrue(writer.toString().contains("\"'  \n=2+3\""))
    }
}
