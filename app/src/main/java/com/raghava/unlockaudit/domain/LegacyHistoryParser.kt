package com.raghava.unlockaudit.domain

import java.security.MessageDigest
import java.text.DateFormat
import java.text.ParsePosition
import java.util.Locale

/**
 * Parses the exact v1.1 SharedPreferences history format recovered from its DEX:
 * Session: <medium date, short time> to <medium date, short time>
 * <medium date, short time>  <package.name>
 *
 * Parsing is conservative: malformed sessions are *not* silently discarded.
 * The caller must keep the raw legacy preferences for later recovery.
 */
data class LegacyEvent(val timestamp: Long, val packageName: String)
data class LegacySession(
    val importKey: String,
    val start: Long,
    val end: Long,
    val events: List<LegacyEvent>
)
data class LegacyParseResult(val sessions: List<LegacySession>, val rejected: List<String>)

object LegacyHistoryParser {
    private val commonLocales = listOf(
        Locale.getDefault(), Locale.CANADA, Locale.US, Locale.UK,
        Locale.CANADA_FRENCH, Locale.ENGLISH
    ).distinct()

    fun parse(raw: String): LegacyParseResult {
        if (raw.isBlank()) return LegacyParseResult(emptyList(), emptyList())
        val sessions = mutableListOf<LegacySession>()
        val rejected = mutableListOf<String>()
        val blocks = mutableListOf<MutableList<String>>()
        for (line in raw.lines()) {
            if (line.startsWith("Session: ")) blocks.add(mutableListOf(line))
            else if (blocks.isNotEmpty()) blocks.last().add(line)
            else if (line.isNotBlank()) rejected.add("Orphan line: $line")
        }
        for ((index, block) in blocks.withIndex()) {
            val header = block.first().removePrefix("Session: ")
            val split = header.lastIndexOf(" to ")
            if (split < 1) {
                rejected.add("Invalid header: ${block.first()}")
                continue
            }
            val start = dateToMillis(header.substring(0, split).trim())
            val end = dateToMillis(header.substring(split + 4).trim())
            if (start == null || end == null || end < start) {
                rejected.add("Unparseable session: ${block.first()}")
                continue
            }
            val events = mutableListOf<LegacyEvent>()
            var invalid = false
            block.drop(1).filter { it.isNotBlank() }.forEach { line ->
                val sep = line.lastIndexOf("  ")
                if (sep < 1) { invalid = true; return@forEach }
                val time = dateToMillis(line.substring(0, sep).trim())
                val pkg = line.substring(sep + 2).trim()
                if (time == null || pkg.isBlank() || time < start - 60_000 || time > end + 60_000) {
                    invalid = true
                } else events.add(LegacyEvent(time, pkg))
            }
            if (invalid) {
                rejected.add("Unparseable events: ${block.first()}")
                continue
            }
            // Include position: identical sessions are rare but both must import distinctly.
            val id = sha256("$index\n${block.joinToString("\n")}")
            sessions.add(LegacySession(id, start, end, events))
        }
        return LegacyParseResult(sessions, rejected)
    }

    fun dateToMillis(value: String): Long? {
        for (locale in commonLocales) {
            val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            format.isLenient = false
            val position = ParsePosition(0)
            val result = format.parse(value, position)
            if (result != null && position.index == value.length) return result.time
        }
        return null
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
