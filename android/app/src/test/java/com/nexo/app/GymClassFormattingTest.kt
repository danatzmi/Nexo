package com.nexo.app

import com.nexo.app.domain.model.GymClass
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

class GymClassFormattingTest {

    private lateinit var originalLocale: Locale
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        originalTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    private fun classAt(hour: Int, minute: Int, durationMinutes: Int = 60, coach: String = "Alex"): GymClass {
        val startMillis = ZonedDateTime.of(2026, 8, 19, hour, minute, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        return GymClass(
            id = "class-1", title = "WOD", coach = coach, startTimeMillis = startMillis,
            capacity = 10, currentAttendees = 0, durationMinutes = durationMinutes
        )
    }

    @Test
    fun formattedFullDate_isWeekdayMonthDayYear() {
        assertEquals("Wednesday, August 19, 2026", classAt(14, 0).formattedFullDate)
    }

    @Test
    fun formattedTimeRange_addsDurationToStartTime() {
        assertEquals("14:00 - 15:00", classAt(14, 0, durationMinutes = 60).formattedTimeRange)
    }

    @Test
    fun formattedTimeRange_handlesNonHourDurations() {
        assertEquals("14:00 - 14:45", classAt(14, 0, durationMinutes = 45).formattedTimeRange)
    }

    @Test
    fun formattedCoach_returnsCoachNameDirectly() {
        assertEquals("Alex", classAt(14, 0, coach = "Alex").formattedCoach)
    }

    @Test
    fun formattedCoach_isUnassigned_whenCoachIsBlank() {
        assertEquals("Unassigned", classAt(14, 0, coach = "").formattedCoach)
    }
}
