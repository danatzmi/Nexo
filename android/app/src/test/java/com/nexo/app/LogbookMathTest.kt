package com.nexo.app

import com.nexo.app.domain.model.WorkoutLog
import com.nexo.app.domain.model.calculateWeeklyAveragePreviousMonth
import com.nexo.app.domain.model.personalRecords
import com.nexo.app.domain.model.previousMonthName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LogbookMathTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun millis(year: Int, month: Int, day: Int): Long =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun weeklyAverage_averagesDatesOverThePreviousCalendarMonth() {
        // Reference date is in March; previous month is February 2026 (28 days -> 4 weeks exactly).
        val reference = millis(2026, 3, 18)
        val datesInFebruary = listOf(
            millis(2026, 2, 2), millis(2026, 2, 9), millis(2026, 2, 16), millis(2026, 2, 23)
        )

        val average = calculateWeeklyAveragePreviousMonth(datesInFebruary, reference, zone)

        // 4 sessions over 28/7 = 4 weeks -> 1.0/wk.
        assertEquals(1.0, average, 0.0001)
    }

    @Test
    fun weeklyAverage_ignoresDatesOutsideThePreviousMonth() {
        val reference = millis(2026, 3, 18)
        val dates = listOf(
            millis(2026, 2, 5),
            millis(2026, 1, 5), // gap month — not counted
            millis(2026, 3, 5) // reference's own month — not counted
        )

        val average = calculateWeeklyAveragePreviousMonth(dates, reference, zone)

        // 1 session over 28/7 = 4 weeks -> 0.25/wk.
        assertEquals(0.25, average, 0.0001)
    }

    @Test
    fun weeklyAverage_isZero_whenThePreviousMonthHasNoDates() {
        val reference = millis(2026, 3, 18)
        val dates = listOf(millis(2026, 1, 5))

        assertEquals(0.0, calculateWeeklyAveragePreviousMonth(dates, reference, zone), 0.0001)
    }

    @Test
    fun weeklyAverage_isZero_withNoDatesAtAll() {
        assertEquals(0.0, calculateWeeklyAveragePreviousMonth(emptyList(), System.currentTimeMillis(), zone), 0.0001)
    }

    @Test
    fun previousMonthName_returnsTheFullNameOfTheMonthBeforeReference() {
        val reference = millis(2026, 3, 18)
        assertEquals("February", previousMonthName(reference, zone, java.util.Locale.US))
    }

    @Test
    fun previousMonthName_wrapsToDecemberAcrossAYearBoundary() {
        val reference = millis(2026, 1, 5)
        assertEquals("December", previousMonthName(reference, zone, java.util.Locale.US))
    }

    @Test
    fun personalRecords_picksTheHighestScorePerMovement() {
        val logs = listOf(
            WorkoutLog(id = "1", movement = "Back Squat", score = 100.0, dateMillis = millis(2026, 1, 1)),
            WorkoutLog(id = "2", movement = "Back Squat", score = 120.0, dateMillis = millis(2026, 2, 1)),
            WorkoutLog(id = "3", movement = "Back Squat", score = 110.0, dateMillis = millis(2026, 3, 1))
        )

        val prs = personalRecords(logs)

        assertEquals("2", prs.getValue("Back Squat").id)
    }

    @Test
    fun personalRecords_breaksTiesByTheEarliestDate() {
        val logs = listOf(
            WorkoutLog(id = "1", movement = "Deadlift", score = 150.0, dateMillis = millis(2026, 3, 1)),
            WorkoutLog(id = "2", movement = "Deadlift", score = 150.0, dateMillis = millis(2026, 1, 1))
        )

        val prs = personalRecords(logs)

        assertEquals("2", prs.getValue("Deadlift").id)
    }

    @Test
    fun personalRecords_ties_atTheEarliestSession_whenNoEntryIsScored() {
        val logs = listOf(
            WorkoutLog(id = "1", movement = "Yoga Flow", reps = null, dateMillis = millis(2026, 1, 1)),
            WorkoutLog(id = "2", movement = "Yoga Flow", reps = null, dateMillis = millis(2026, 2, 1))
        )

        val prs = personalRecords(logs)

        assertEquals("1", prs.getValue("Yoga Flow").id)
    }

    @Test
    fun personalRecords_keepsOneEntryPerDistinctMovement() {
        val logs = listOf(
            WorkoutLog(id = "1", movement = "Back Squat", score = 100.0, dateMillis = millis(2026, 1, 1)),
            WorkoutLog(id = "2", movement = "Deadlift", score = 150.0, dateMillis = millis(2026, 1, 2))
        )

        val prs = personalRecords(logs)

        assertEquals(setOf("Back Squat", "Deadlift"), prs.keys)
    }
}
