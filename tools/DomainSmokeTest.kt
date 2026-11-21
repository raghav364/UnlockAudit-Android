import com.raghava.unlockaudit.domain.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.time.ZoneId
import java.time.LocalDate
import java.io.StringWriter

fun main() {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.CANADA)
    val start = 1_795_278_000_000L
    val end = start + 180_000
    val event = start + 60_000
    val raw = "Session: ${fmt.format(Date(start))} to ${fmt.format(Date(end))}\n" +
        "${fmt.format(Date(event))}  com.example.reader\n\n"
    val imported = LegacyHistoryParser.parse(raw)
    check(imported.rejected.isEmpty()) { imported.rejected }
    check(imported.sessions.size == 1)
    check(imported.sessions[0].events.size == 1)
    check(imported.sessions[0].events[0].packageName == "com.example.reader")
    println("PASS: exact legacy-format session and app events")

    check(LegacyHistoryParser.parse("Session: invalid to invalid\n").rejected.size == 1)
    check(LegacyHistoryParser.parse(raw) == imported)
    println("PASS: rejected legacy text retained; deterministic migration keys")

    val reduced = EventReducer.foregroundTransitions(listOf(
        RawUsageEvent(200, "com.beta", 1),
        RawUsageEvent(100, "com.alpha", 1),
        RawUsageEvent(150, "com.alpha", 1),
        RawUsageEvent(180, "com.mine", 1),
        RawUsageEvent(250, "com.no", 2),
        RawUsageEvent(350, "com.tooLate", 1)
    ), 90, 300, "com.mine")
    check(reduced.map { it.packageName } == listOf("com.alpha", "com.beta"))
    check(EventReducer.foregroundTransitions(emptyList(), 20, 10, "x").isEmpty())
    println("PASS: chronological foreground events, duplicate and own-app filters")

    val writer = StringWriter()
    CsvWriter.write(writer, listOf(ExportRecord(
        start, end, false, "legacy", "com.a", "=CALL(\"test\")", start
    )), ZoneId.of("UTC"))
    check(writer.toString().contains("\"'=CALL(\"\"test\"\")\""))
    check(writer.toString().lines().size >= 2)
    val tricky = StringWriter()
    CsvWriter.write(tricky, listOf(ExportRecord(start, end, false, "live", "com.a", "  \n=FORMULA()", start)), ZoneId.of("UTC"))
    check(tricky.toString().contains("\"'  \n=FORMULA()\""))
    println("PASS: CSV formula-injection defense, leading whitespace, quoting and header")

    val usFmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.US)
    val us = "Session: ${usFmt.format(Date(start))} to ${usFmt.format(Date(end))}\n"
    check(LegacyHistoryParser.parse(us).sessions.size == 1)
    println("PASS: US-locale v1.1 history parsing")

    val today = LocalDate.of(2026, 11, 21)
    val zone = ZoneId.of("America/Halifax")
    check(RetentionPolicy.cutoffMillis(0, today, zone) == null)
    val cutoff = RetentionPolicy.cutoffMillis(30, today, zone)!!
    val firstIncluded = java.time.Instant.ofEpochMilli(cutoff).atZone(zone).toLocalDate()
    check(firstIncluded == LocalDate.of(2026, 10, 23))
    check(java.time.temporal.ChronoUnit.DAYS.between(firstIncluded, today) + 1 == 30L)
    check(java.time.Instant.ofEpochMilli(cutoff).atZone(zone).hour == 0)
    check(runCatching { RetentionPolicy.cutoffMillis(-7, today, zone) }.isFailure)
    println("PASS: retention calendar boundaries, Halifax timezone and invalid values")
}
