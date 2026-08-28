package com.nexo.app.domain.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * The average number of completed sessions per week in the calendar month
 * immediately before [referenceMillis]'s month — e.g. on any date in
 * August, this covers all of July. Weeks-in-month is measured as a
 * fraction (days in month / 7), not a whole-week count, so short and long
 * months stay comparable. Mirrors `calculateWeeklyAveragePreviousMonth` on
 * iOS. A top-level function (not tied to a ViewModel) so it's directly
 * testable, per CLAUDE.md's "business logic stays out of views" rule.
 */
fun calculateWeeklyAveragePreviousMonth(
    dateMillisList: List<Long>,
    referenceMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Double {
    val previousMonth = YearMonth.from(Instant.ofEpochMilli(referenceMillis).atZone(zoneId)).minusMonths(1)
    val countInPreviousMonth = dateMillisList.count { millis ->
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(zoneId)) == previousMonth
    }
    if (countInPreviousMonth == 0) return 0.0

    val weeksInPreviousMonth = previousMonth.lengthOfMonth() / 7.0
    return countInPreviousMonth / weeksInPreviousMonth
}

/**
 * The full localized name of the calendar month immediately before
 * [referenceMillis]'s month — e.g. "July" for any date in August. Backs
 * the "Weekly Avg (<month>)" stat card label. Mirrors `previousMonthName`
 * on iOS.
 */
fun previousMonthName(
    referenceMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val previousMonth = YearMonth.from(Instant.ofEpochMilli(referenceMillis).atZone(zoneId)).minusMonths(1)
    return previousMonth.month.getDisplayName(TextStyle.FULL, locale)
}

/**
 * The personal record (highest logged score) per movement, keyed by
 * movement name — mirrors `personalRecords(from:)` on iOS. An entry with
 * no score counts as 0 for comparison. Ties (including the all-scoreless
 * case, where every entry ties at 0) are broken by earliest date — the
 * date a PR was *first* achieved, not the most recent time it was merely
 * matched again. A top-level function for the same testability reason as
 * [calculateWeeklyAveragePreviousMonth].
 */
fun personalRecords(logs: List<WorkoutLog>): Map<String, WorkoutLog> {
    val best = mutableMapOf<String, WorkoutLog>()
    for (log in logs) {
        val existing = best[log.movement]
        if (existing == null) {
            best[log.movement] = log
        } else {
            val logScore = log.score ?: 0.0
            val existingScore = existing.score ?: 0.0
            if (logScore > existingScore || (logScore == existingScore && log.dateMillis < existing.dateMillis)) {
                best[log.movement] = log
            }
        }
    }
    return best
}
