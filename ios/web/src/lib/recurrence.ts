// Direct TS port of `generateRecurrenceDates` in `AddClassView.swift` — a
// pure function (no Firestore access), kept separate from any component
// for the same reason the Swift version is a free function: business
// logic stays out of views, and it's directly testable in isolation.

export type RecurrenceType = 'none' | 'daily' | 'custom'

export const recurrenceTypeLabels: Record<RecurrenceType, string> = {
  none: 'Never',
  daily: 'Every Day',
  custom: 'Custom Days',
}

function addDays(date: Date, days: number): Date {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}

// `selectedWeekdays` uses `Date.getDay()`'s convention: 0 = Sunday ... 6 =
// Saturday (note this is 0-based, unlike the iOS `Calendar.weekday`
// component, which is 1-based — callers on this side of the port should
// always source weekday numbers from `getDay()`, never mix the two).
export function generateRecurrenceDates(
  recurrenceType: RecurrenceType,
  startTime: Date,
  endDate: Date,
  selectedWeekdays: Set<number> = new Set(),
): Date[] {
  if (recurrenceType === 'none') {
    return [startTime]
  }

  const dates: Date[] = []
  let current = startTime

  while (current <= endDate) {
    if (recurrenceType === 'daily') {
      dates.push(current)
      current = addDays(current, 1)
    } else {
      // custom
      if (selectedWeekdays.has(current.getDay())) {
        dates.push(current)
      }
      current = addDays(current, 1)
    }
  }

  return dates
}
