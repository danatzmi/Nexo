package com.nexo.app.domain.model

import java.time.Instant
import java.time.ZoneId

enum class RecurrenceType {
    NONE, DAILY, WEEKLY, BIWEEKLY, MONTHLY, CUSTOM
}

/**
 * Computes each occurrence's start time (epoch millis) for a class series
 * under the given recurrence rule — mirrors iOS's `generateRecurrenceDates`.
 * A pure, directly-testable calculation per CLAUDE.md's "business logic
 * stays out of views" rule.
 */
fun generateRecurrenceDates(
    type: RecurrenceType,
    startMillis: Long,
    endMillis: Long,
    selectedWeekdays: Set<Int> = emptySet(),
    zoneId: ZoneId = ZoneId.systemDefault()
): List<Long> {
    if (type == RecurrenceType.NONE) return listOf(startMillis)

    val dates = mutableListOf<Long>()
    var current = Instant.ofEpochMilli(startMillis).atZone(zoneId)
    val end = Instant.ofEpochMilli(endMillis).atZone(zoneId)

    while (!current.isAfter(end)) {
        when (type) {
            RecurrenceType.NONE -> {}
            RecurrenceType.DAILY -> {
                dates.add(current.toInstant().toEpochMilli())
                current = current.plusDays(1)
            }
            RecurrenceType.WEEKLY -> {
                dates.add(current.toInstant().toEpochMilli())
                current = current.plusWeeks(1)
            }
            RecurrenceType.BIWEEKLY -> {
                dates.add(current.toInstant().toEpochMilli())
                current = current.plusWeeks(2)
            }
            RecurrenceType.MONTHLY -> {
                dates.add(current.toInstant().toEpochMilli())
                current = current.plusMonths(1)
            }
            RecurrenceType.CUSTOM -> {
                // iOS's Calendar.component(.weekday, from:) is fixed 1=Sunday..7=Saturday
                // regardless of locale; java.time's DayOfWeek.value is 1=Monday..7=Sunday.
                val iosWeekday = (current.dayOfWeek.value % 7) + 1
                if (iosWeekday in selectedWeekdays) dates.add(current.toInstant().toEpochMilli())
                current = current.plusDays(1)
            }
        }
    }
    return dates
}

/**
 * [originalMillis]'s date with [templateMillis]'s time-of-day applied —
 * mirrors the date-components merge in iOS's `updateClassSeries`, used
 * when editing "this & future" occurrences of a series: each occurrence
 * keeps its own date, only the time-of-day shifts to match the edit.
 */
fun applyTimeOfDay(originalMillis: Long, templateMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
    val original = Instant.ofEpochMilli(originalMillis).atZone(zoneId)
    val template = Instant.ofEpochMilli(templateMillis).atZone(zoneId)
    return original
        .withHour(template.hour)
        .withMinute(template.minute)
        .withSecond(0)
        .withNano(0)
        .toInstant()
        .toEpochMilli()
}
