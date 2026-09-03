package com.nexo.app.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Mirrors `gyms/{gymId}/classes/{classId}` — see FIRESTORE_SCHEMA.md. `id`
 * is a UUID string, matching iOS `GymClass.id`. `startTimeMillis` is
 * epoch millis (Firestore `Timestamp` on the wire) rather than a
 * platform-specific date type, so both clients can compare it directly.
 */
data class GymClass(
    val id: String,
    val title: String,
    val coach: String,
    val startTimeMillis: Long,
    val capacity: Int,
    val currentAttendees: Int,
    val waitlistCount: Int = 0,
    val durationMinutes: Int = 60,
    val description: String = "",
    val classType: String = "CrossFit WOD",
    val isPremium: Boolean = false,
    val seriesId: String? = null
) {
    val isFull: Boolean
        get() = currentAttendees >= capacity

    val availableSpots: Int
        get() = (capacity - currentAttendees).coerceAtLeast(0)

    /** e.g. "Monday, 18/08/26" — the Schedule screen's "Date & Day" field. */
    val formattedDayDate: String
        get() = DAY_DATE_FORMATTER.format(Instant.ofEpochMilli(startTimeMillis).atZone(ZoneId.systemDefault()))

    /** e.g. "14:00" — the Schedule screen's "Time" field. */
    val formattedTime: String
        get() = TIME_FORMATTER.format(Instant.ofEpochMilli(startTimeMillis).atZone(ZoneId.systemDefault()))

    /** e.g. "Wednesday, August 19, 2026" — the Class Detail screen's "Date" row. */
    val formattedFullDate: String
        get() = FULL_DATE_FORMATTER.format(Instant.ofEpochMilli(startTimeMillis).atZone(ZoneId.systemDefault()))

    /** e.g. "14:00 - 15:00" — start time through start + [durationMinutes]. */
    val formattedTimeRange: String
        get() {
            val start = Instant.ofEpochMilli(startTimeMillis).atZone(ZoneId.systemDefault())
            val end = start.plusMinutes(durationMinutes.toLong())
            return "${TIME_FORMATTER.format(start)} - ${TIME_FORMATTER.format(end)}"
        }

    /** e.g. "Alex", or "Unassigned" when [coach] is blank. */
    val formattedCoach: String
        get() = if (coach.isBlank()) "Unassigned" else coach

    private companion object {
        val DAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yy", Locale.getDefault())
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val FULL_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
    }
}
