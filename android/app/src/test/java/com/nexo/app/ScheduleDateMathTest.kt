package com.nexo.app

import com.nexo.app.domain.model.isOnLocalDate
import com.nexo.app.domain.model.mondayStartMillis
import com.nexo.app.domain.model.weekDatesFor
import com.nexo.app.domain.model.weekHeaderTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Covers the Schedule screen's day/week arithmetic (`ScheduleDateMath.kt`)
 * in isolation — the "easy to get subtly wrong" date/time logic CLAUDE.md
 * flags for test coverage, especially week-start locale handling and
 * month-boundary/day-boundary edge cases.
 */
class ScheduleDateMathTest {

    // MARK: - isOnLocalDate

    @Test
    fun isOnLocalDate_true_forAMillisecondWithinTheDay() {
        val date = LocalDate.of(2026, 8, 18)
        val noon = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertTrue(isOnLocalDate(noon, date, ZoneOffset.UTC))
    }

    @Test
    fun isOnLocalDate_true_atExactlyMidnight_startOfDay() {
        val date = LocalDate.of(2026, 8, 18)
        val midnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        assertTrue(isOnLocalDate(midnight, date, ZoneOffset.UTC))
    }

    @Test
    fun isOnLocalDate_false_oneMillisecondIntoTheNextDay() {
        val date = LocalDate.of(2026, 8, 18)
        val justAfterMidnight = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        assertFalse(isOnLocalDate(justAfterMidnight, date, ZoneOffset.UTC))
    }

    // MARK: - weekDatesFor

    @Test
    fun weekDatesFor_startsSunday_inASundayFirstLocale() {
        // Wednesday, Aug 19 2026
        val wednesday = LocalDate.of(2026, 8, 19)

        val week = weekDatesFor(wednesday, Locale.US)

        assertEquals(LocalDate.of(2026, 8, 16), week.first()) // Sunday
        assertEquals(LocalDate.of(2026, 8, 22), week.last()) // Saturday
        assertEquals(7, week.size)
    }

    @Test
    fun weekDatesFor_startsMonday_inAMondayFirstLocale() {
        val wednesday = LocalDate.of(2026, 8, 19)

        val week = weekDatesFor(wednesday, Locale.GERMANY)

        assertEquals(LocalDate.of(2026, 8, 17), week.first()) // Monday
        assertEquals(LocalDate.of(2026, 8, 23), week.last()) // Sunday
    }

    @Test
    fun weekDatesFor_containsTheOriginalDate() {
        val date = LocalDate.of(2026, 8, 19)

        assertTrue(weekDatesFor(date, Locale.US).contains(date))
    }

    // MARK: - weekHeaderTitle

    @Test
    fun weekHeaderTitle_singleMonth() {
        val week = weekDatesFor(LocalDate.of(2026, 8, 19), Locale.US)

        assertEquals("August 2026", weekHeaderTitle(week, Locale.US))
    }

    @Test
    fun weekHeaderTitle_straddlingTwoMonths() {
        // Week of Aug 30 2026 (Sunday) runs into September.
        val week = weekDatesFor(LocalDate.of(2026, 9, 1), Locale.US)

        assertEquals("August / September 2026", weekHeaderTitle(week, Locale.US))
    }

    // MARK: - mondayStartMillis

    @Test
    fun mondayStartMillis_fromMidWeek_returnsThatWeeksMonday() {
        // Wednesday, Aug 19 2026 -> Monday, Aug 17 2026
        val wednesdayNoon = LocalDate.of(2026, 8, 19).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        val result = mondayStartMillis(wednesdayNoon, ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 8, 17).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), result)
    }

    @Test
    fun mondayStartMillis_fromMondayItself_returnsMidnightThatSameDay() {
        val mondayEvening = LocalDate.of(2026, 8, 17).atTime(20, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        val result = mondayStartMillis(mondayEvening, ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 8, 17).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), result)
    }

    @Test
    fun mondayStartMillis_fromSunday_rollsBackToThePrecedingMonday_notForward() {
        // Sunday, Aug 23 2026 belongs to the week starting Monday Aug 17, not Aug 24.
        val sunday = LocalDate.of(2026, 8, 23).atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        val result = mondayStartMillis(sunday, ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 8, 17).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), result)
    }

    @Test
    fun mondayStartMillis_alwaysLandsOnMonday_regardlessOfInputDayOfWeek() {
        // Unlike weekDatesFor, this is always Monday-first regardless of
        // any locale's first-day-of-week convention (it takes no locale).
        for (dayOffset in 0..6) {
            val millis = LocalDate.of(2026, 8, 17).plusDays(dayOffset.toLong()).atTime(15, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            val resultDate = java.time.Instant.ofEpochMilli(mondayStartMillis(millis, ZoneOffset.UTC)).atZone(ZoneOffset.UTC).toLocalDate()
            assertEquals(java.time.DayOfWeek.MONDAY, resultDate.dayOfWeek)
        }
    }
}
