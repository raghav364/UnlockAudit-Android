package com.raghava.unlockaudit.domain

import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** All data stays local; writes directly into a user-selected SAF document. */
data class ExportRecord(
    val start: Long, val end: Long, val interrupted: Boolean,
    val source: String, val packageName: String, val appLabel: String, val eventAt: Long?
)

object CsvWriter {
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    fun write(writer: Writer, records: List<ExportRecord>, zone: ZoneId = ZoneId.systemDefault()) {
        writer.write("session_start,session_end,interrupted,source,app_package,app_name,app_opened_at\r\n")
        for (row in records) {
            val fields = listOf(
                date(row.start, zone), date(row.end, zone), row.interrupted.toString(),
                row.source, row.packageName, row.appLabel,
                row.eventAt?.let { date(it, zone) }.orEmpty()
            )
            writer.write(fields.joinToString(",") { escape(it) })
            writer.write("\r\n")
        }
        writer.flush()
    }
    private fun date(epoch: Long, zone: ZoneId) = formatter.format(Instant.ofEpochMilli(epoch).atZone(zone))
    private fun escape(value: String): String {
        // Spreadsheet formula injection defense, since labels can come from other installed apps.
        // Leading spaces, tabs, newlines and a BOM can disguise a formula prefix.
        val firstContent = value.trimStart { it == ' ' || it == '\t' || it == '\r' ||
            it == '\n' || it == '\uFEFF' }.firstOrNull()
        val potentiallyFormula = firstContent in listOf('=', '+', '-', '@') ||
            value.firstOrNull() in listOf('\t', '\r', '\n')
        val safe = if (potentiallyFormula) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
