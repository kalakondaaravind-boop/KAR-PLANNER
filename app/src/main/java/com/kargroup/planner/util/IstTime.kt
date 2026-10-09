package com.kargroup.planner.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All planner date logic runs in Indian Standard Time (Asia/Kolkata, UTC+5:30). */
object Ist {
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH)
    val headerDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)
    val longDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMM", Locale.ENGLISH)
    private val shortFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)
    private val fullFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val stampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    fun now(): ZonedDateTime = ZonedDateTime.now(ZONE)
    fun today(): LocalDate = LocalDate.now(ZONE)
    fun todayStr(): String = today().toString()

    fun parse(value: String?): LocalDate? =
        if (value.isNullOrBlank()) null else runCatching { LocalDate.parse(value) }.getOrNull()

    /** "Today", "Tomorrow", "Yesterday", "Mon, 12 Oct" or "12 Oct 2027". */
    fun friendly(value: String?): String {
        val d = parse(value) ?: return ""
        val t = today()
        return when (d) {
            t -> "Today"
            t.plusDays(1) -> "Tomorrow"
            t.minusDays(1) -> "Yesterday"
            else -> if (d.year == t.year) d.format(shortFormat) else d.format(fullFormat)
        }
    }

    fun formatFull(value: String?): String = parse(value)?.format(fullFormat) ?: ""

    fun formatStamp(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZONE).format(stampFormat)

    /** Material DatePicker works with UTC-midnight millis. */
    fun dateToPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun pickerMillisToDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
