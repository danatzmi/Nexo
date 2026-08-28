package com.nexo.app.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** True when [startTimeMillis] falls on [date] in [zoneId] — the Schedule screen's day filter, evaluated client-side over classes already fetched via `BackendRepository.fetchClasses` (mirrors iOS's Firestore day-bounds query, done here after the fetch instead of as part of it). */
fun isOnLocalDate(startTimeMillis: Long, date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
    Instant.ofEpochMilli(startTimeMillis).atZone(zoneId).toLocalDate() == date

/**
 * The 7 consecutive dates of the week containing [date], starting from
 * [locale]'s first day of week — mirrors iOS's
 * `Calendar.current.dateInterval(of: .weekOfYear, for:)`, which is also
 * locale-dependent (Sunday-first for en_US, Monday-first for most of
 * Europe) rather than a hardcoded Sunday or Monday start.
 */
fun weekDatesFor(date: LocalDate, locale: Locale = Locale.getDefault()): List<LocalDate> {
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val daysFromStart = (date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val start = date.minusDays(daysFromStart.toLong())
    return (0..6).map { start.plusDays(it.toLong()) }
}

/**
 * The Week Day Picker's month/year header — e.g. "August 2026", or
 * "August / September 2026" when [week] straddles two months (or years).
 */
fun weekHeaderTitle(week: List<LocalDate>, locale: Locale = Locale.getDefault()): String {
    val first = week.first()
    val last = week.last()
    val firstMonthName = first.month.getDisplayName(TextStyle.FULL, locale)
    return if (first.month == last.month && first.year == last.year) {
        "$firstMonthName ${first.year}"
    } else {
        "$firstMonthName / ${last.month.getDisplayName(TextStyle.FULL, locale)} ${last.year}"
    }
}

/**
 * Midnight (in [zoneId]) on the Monday of the week containing [millis] —
 * always Monday-first, independent of locale/first-weekday settings
 * (unlike [weekDatesFor], which is locale-dependent for display purposes).
 * Mirrors iOS's `FirebaseBackend.mondayStart(for:)`, used by `copySchedule`
 * for a fixed calendar reference point when computing each class's offset
 * within its source week.
 */
fun mondayStartMillis(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
    val date = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zoneId).toInstant().toEpochMilli()
}

/** e.g. "Aug 18, 2026" — the Schedule screen's empty-state description. */
fun formattedAbbreviatedDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
