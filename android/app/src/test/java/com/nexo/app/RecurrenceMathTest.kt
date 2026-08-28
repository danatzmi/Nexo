package com.nexo.app

import com.nexo.app.domain.model.RecurrenceType
import com.nexo.app.domain.model.applyTimeOfDay
import com.nexo.app.domain.model.generateRecurrenceDates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Covers `RecurrenceMath.kt`'s pure date arithmetic — the kind of "easy to get subtly wrong" logic CLAUDE.md flags for test coverage, especially the weekday-convention mapping for CUSTOM recurrence. */
class RecurrenceMathTest {

    private fun millis(date: LocalDate, time: LocalTime = LocalTime.of(9, 0)): Long =
        date.atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()

    // MARK: - NONE

    @Test
    fun generateRecurrenceDates_none_returnsOnlyTheStartDate_ignoringEndDate() {
        val start = millis(LocalDate.of(2026, 8, 19))
        val end = millis(LocalDate.of(2026, 8, 1)) // before start — must be ignored for NONE

        val dates = generateRecurrenceDates(RecurrenceType.NONE, start, end, zoneId = ZoneOffset.UTC)

        assertEquals(listOf(start), dates)
    }

    // MARK: - DAILY / WEEKLY / BIWEEKLY / MONTHLY

    @Test
    fun generateRecurrenceDates_daily_oneEntryPerDayInclusive() {
        val start = millis(LocalDate.of(2026, 8, 1))
        val end = millis(LocalDate.of(2026, 8, 4))

        val dates = generateRecurrenceDates(RecurrenceType.DAILY, start, end, zoneId = ZoneOffset.UTC)

        assertEquals(4, dates.size) // Aug 1, 2, 3, 4
    }

    @Test
    fun generateRecurrenceDates_weekly_stepsBySevenDays() {
        val start = millis(LocalDate.of(2026, 8, 1))
        val end = millis(LocalDate.of(2026, 8, 22))

        val dates = generateRecurrenceDates(RecurrenceType.WEEKLY, start, end, zoneId = ZoneOffset.UTC)

        assertEquals(listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 22)), dates.map { java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
    }

    @Test
    fun generateRecurrenceDates_biweekly_stepsByFourteenDays() {
        val start = millis(LocalDate.of(2026, 8, 1))
        val end = millis(LocalDate.of(2026, 8, 29))

        val dates = generateRecurrenceDates(RecurrenceType.BIWEEKLY, start, end, zoneId = ZoneOffset.UTC)

        assertEquals(listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 29)), dates.map { java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
    }

    @Test
    fun generateRecurrenceDates_monthly_stepsByOneMonth() {
        val start = millis(LocalDate.of(2026, 1, 31))
        val end = millis(LocalDate.of(2026, 4, 30))

        val dates = generateRecurrenceDates(RecurrenceType.MONTHLY, start, end, zoneId = ZoneOffset.UTC)

        // Jan 31 -> Feb 28 (java.time clamps short months) -> Mar 28 -> Apr 28
        assertEquals(4, dates.size)
    }

    @Test
    fun generateRecurrenceDates_endDateExclusiveOfNothing_includesEndDateItself() {
        val start = millis(LocalDate.of(2026, 8, 1))
        val end = millis(LocalDate.of(2026, 8, 1))

        val dates = generateRecurrenceDates(RecurrenceType.DAILY, start, end, zoneId = ZoneOffset.UTC)

        assertEquals(1, dates.size)
    }

    // MARK: - CUSTOM

    @Test
    fun generateRecurrenceDates_custom_onlyIncludesSelectedWeekdays() {
        // Aug 1 2026 is a Saturday; the window covers a full week.
        val start = millis(LocalDate.of(2026, 8, 1))
        val end = millis(LocalDate.of(2026, 8, 7))

        // iOS weekday convention: 1=Sunday, 2=Monday, ... 7=Saturday. Select Monday(2) and Wednesday(4).
        val dates = generateRecurrenceDates(RecurrenceType.CUSTOM, start, end, selectedWeekdays = setOf(2, 4), zoneId = ZoneOffset.UTC)

        val resultDates = dates.map { java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        assertEquals(listOf(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 5)), resultDates) // Monday Aug 3, Wednesday Aug 5
    }

    @Test
    fun generateRecurrenceDates_custom_emptySelection_producesNoDates() {
        val start = millis(LocalDate.of(2026, 8, 1))
        val end = millis(LocalDate.of(2026, 8, 7))

        val dates = generateRecurrenceDates(RecurrenceType.CUSTOM, start, end, selectedWeekdays = emptySet(), zoneId = ZoneOffset.UTC)

        assertEquals(emptyList<Long>(), dates)
    }

    // MARK: - applyTimeOfDay

    @Test
    fun applyTimeOfDay_keepsOriginalDate_appliesTemplateTime() {
        val original = millis(LocalDate.of(2026, 8, 19), LocalTime.of(6, 0))
        val template = millis(LocalDate.of(2026, 1, 1), LocalTime.of(18, 30))

        val result = java.time.Instant.ofEpochMilli(applyTimeOfDay(original, template, ZoneOffset.UTC)).atZone(ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 8, 19), result.toLocalDate())
        assertEquals(LocalTime.of(18, 30), result.toLocalTime())
    }
}
