//
//  ClassRecurrenceTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@Suite("Class Recurrence Date Generation")
struct ClassRecurrenceTests {
    @Test(".none recurrence returns exactly the start time as a single occurrence")
    func noneRecurrenceReturnsStartTimeOnly() {
        let start = Date()
        let end = Calendar.current.date(byAdding: .day, value: 30, to: start)!

        let dates = generateRecurrenceDates(recurrenceType: .none, startTime: start, endDate: end)

        #expect(dates == [start])
    }

    @Test("daily recurrence generates one occurrence per day, inclusive of the end date")
    func dailyRecurrenceGeneratesOnePerDay() {
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: Date())
        let end = calendar.date(byAdding: .day, value: 6, to: start)! // 7 days total: day 0...6

        let dates = generateRecurrenceDates(recurrenceType: .daily, startTime: start, endDate: end)

        #expect(dates.count == 7)
        #expect(dates.first == start)
    }

    @Test("weekly recurrence generates one occurrence every 7 days")
    func weeklyRecurrenceGeneratesEveryWeek() {
        let calendar = Calendar.current
        let start = Date()
        let end = calendar.date(byAdding: .weekOfYear, value: 4, to: start)! // weeks 0,1,2,3,4

        let dates = generateRecurrenceDates(recurrenceType: .weekly, startTime: start, endDate: end)

        #expect(dates.count == 5)
    }

    @Test("biweekly recurrence generates one occurrence every 2 weeks")
    func biweeklyRecurrenceGeneratesEveryTwoWeeks() {
        let calendar = Calendar.current
        let start = Date()
        let end = calendar.date(byAdding: .weekOfYear, value: 6, to: start)! // weeks 0,2,4,6

        let dates = generateRecurrenceDates(recurrenceType: .biweekly, startTime: start, endDate: end)

        #expect(dates.count == 4)
    }

    @Test("monthly recurrence generates one occurrence per month")
    func monthlyRecurrenceGeneratesEveryMonth() {
        let calendar = Calendar.current
        let start = Date()
        let end = calendar.date(byAdding: .month, value: 3, to: start)! // months 0,1,2,3

        let dates = generateRecurrenceDates(recurrenceType: .monthly, startTime: start, endDate: end)

        #expect(dates.count == 4)
    }

    @Test("custom recurrence includes only occurrences landing on the selected weekdays")
    func customRecurrenceIncludesOnlySelectedWeekdays() {
        let calendar = Calendar.current
        let start = Date()
        let end = calendar.date(byAdding: .day, value: 13, to: start)! // exactly a 14-day (2-week) window
        let startWeekday = calendar.component(.weekday, from: start)
        let secondWeekday = ((startWeekday - 1 + 2) % 7) + 1 // two days after the start's own weekday
        let selected: Set<Int> = [startWeekday, secondWeekday]

        let dates = generateRecurrenceDates(recurrenceType: .custom, startTime: start, endDate: end, selectedWeekdays: selected)

        #expect(dates.allSatisfy { selected.contains(calendar.component(.weekday, from: $0)) })
        #expect(dates.count == 4, "each of the 2 selected weekdays should land twice in a 2-week window")
    }

    @Test("custom recurrence with no selected weekdays produces no occurrences")
    func customRecurrenceWithNoWeekdaysProducesNothing() {
        let start = Date()
        let end = Calendar.current.date(byAdding: .day, value: 10, to: start)!

        let dates = generateRecurrenceDates(recurrenceType: .custom, startTime: start, endDate: end, selectedWeekdays: [])

        #expect(dates.isEmpty)
    }

    @Test("a recurring type with start after end produces no occurrences (unlike .none)")
    func startAfterEndProducesNoOccurrencesForRecurringTypes() {
        let start = Date()
        let end = Calendar.current.date(byAdding: .day, value: -1, to: start)!

        let dates = generateRecurrenceDates(recurrenceType: .daily, startTime: start, endDate: end)

        #expect(dates.isEmpty)
    }
}
