//
//  ScheduleManagementTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("Schedule Management")
struct ScheduleManagementTests {
    // MARK: - copySchedule

    @Test("copySchedule duplicates a class into the target week with the same offset, resetting attendee/waitlist counts")
    func copyScheduleDuplicatesWithOffsetReset() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let sourceDate = Date()
        let sourceClass = GymClass(
            title: "Morning HIIT",
            coach: "Alex",
            startTime: sourceDate,
            durationMinutes: 45,
            capacity: 10,
            currentAttendees: 7,
            waitlistCount: 2
        )
        mock.classes[gymId] = [sourceClass.id: sourceClass]
        let targetDate = sourceDate.addingTimeInterval(7 * 86400)

        try await mock.copySchedule(gymId: gymId, fromWeekOf: sourceDate, toWeekOf: targetDate)

        let allClasses = Array((mock.classes[gymId] ?? [:]).values)
        #expect(allClasses.count == 2, "original class plus one copy")

        let copy = try #require(allClasses.first { $0.id != sourceClass.id })
        #expect(copy.title == "Morning HIIT")
        #expect(copy.coach == "Alex")
        #expect(copy.durationMinutes == 45)
        #expect(copy.capacity == 10)
        #expect(copy.currentAttendees == 0, "copies start with an empty roster")
        #expect(copy.waitlistCount == 0)

        let expectedStart = sourceClass.startTime.addingTimeInterval(7 * 86400)
        #expect(abs(copy.startTime.timeIntervalSince(expectedStart)) < 1, "same day-of-week/time offset, one week later")
    }

    @Test("copySchedule with no classes in the source week is a no-op")
    func copyScheduleNoSourceClassesIsNoOp() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let sourceDate = Date()

        try await mock.copySchedule(gymId: gymId, fromWeekOf: sourceDate, toWeekOf: sourceDate.addingTimeInterval(7 * 86400))

        #expect((mock.classes[gymId] ?? [:]).isEmpty)
    }

    @Test("copySchedule ignores classes outside the source week")
    func copyScheduleIgnoresClassesOutsideSourceWeek() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let sourceDate = Date()
        // 10 days before sourceDate is guaranteed to fall in a different Monday-Sunday week.
        let outOfRangeClass = GymClass(title: "Old Class", coach: "Alex", startTime: sourceDate.addingTimeInterval(-10 * 86400))
        mock.classes[gymId] = [outOfRangeClass.id: outOfRangeClass]

        try await mock.copySchedule(gymId: gymId, fromWeekOf: sourceDate, toWeekOf: sourceDate.addingTimeInterval(7 * 86400))

        #expect((mock.classes[gymId] ?? [:]).count == 1, "only the original out-of-range class, nothing copied")
    }

    // MARK: - updateClassSeries

    @Test("updateClassSeries applies the template's fields to every occurrence on/after `from`, preserving each occurrence's own date and roster counts")
    func updateClassSeriesAppliesTemplatePreservingDatesAndCounts() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let seriesId = UUID()
        let calendar = Calendar.current

        func occurrence(daysFromNow: Int, hour: Int, currentAttendees: Int, waitlistCount: Int) -> GymClass {
            var components = calendar.dateComponents([.year, .month, .day], from: calendar.date(byAdding: .day, value: daysFromNow, to: Date())!)
            components.hour = hour
            components.minute = 0
            return GymClass(
                title: "CrossFit WOD", coach: "Alex", startTime: calendar.date(from: components)!,
                durationMinutes: 60, capacity: 12, currentAttendees: currentAttendees, waitlistCount: waitlistCount,
                seriesId: seriesId, classType: "CrossFit WOD"
            )
        }

        let before = occurrence(daysFromNow: 0, hour: 9, currentAttendees: 3, waitlistCount: 0)
        let occurrence1 = occurrence(daysFromNow: 1, hour: 9, currentAttendees: 5, waitlistCount: 1)
        let occurrence2 = occurrence(daysFromNow: 2, hour: 9, currentAttendees: 2, waitlistCount: 0)
        mock.classes[gymId] = [before.id: before, occurrence1.id: occurrence1, occurrence2.id: occurrence2]

        var templateComponents = calendar.dateComponents([.year, .month, .day], from: occurrence1.startTime)
        templateComponents.hour = 18
        templateComponents.minute = 30
        let template = GymClass(
            id: occurrence1.id,
            title: "Evening HIIT", coach: "Jamie", startTime: calendar.date(from: templateComponents)!,
            durationMinutes: 45, capacity: 20, currentAttendees: 999, waitlistCount: 999,
            seriesId: seriesId, classType: "HIIT", isPremium: true, description: "New description"
        )

        try await mock.updateClassSeries(gymId: gymId, seriesId: seriesId, from: occurrence1.startTime, updatedTemplate: template)

        let updated1 = try #require(mock.classes[gymId]?[occurrence1.id])
        #expect(updated1.title == "Evening HIIT")
        #expect(updated1.coach == "Jamie")
        #expect(updated1.durationMinutes == 45)
        #expect(updated1.capacity == 20)
        #expect(updated1.classType == "HIIT")
        #expect(updated1.isPremium == true)
        #expect(updated1.description == "New description")
        #expect(updated1.currentAttendees == 5, "roster count preserved, not overwritten by the template")
        #expect(updated1.waitlistCount == 1)
        #expect(calendar.component(.hour, from: updated1.startTime) == 18, "time-of-day shifted to match the template")
        #expect(calendar.isDate(updated1.startTime, inSameDayAs: occurrence1.startTime), "date preserved")

        let updated2 = try #require(mock.classes[gymId]?[occurrence2.id])
        #expect(updated2.title == "Evening HIIT")
        #expect(calendar.isDate(updated2.startTime, inSameDayAs: occurrence2.startTime), "date preserved for a later occurrence too")

        let untouched = try #require(mock.classes[gymId]?[before.id])
        #expect(untouched.title == "CrossFit WOD", "the occurrence before `from` is left untouched")
    }

    @Test("updateClassSeries with no matching occurrences is a no-op")
    func updateClassSeriesNoMatchingOccurrencesIsNoOp() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let seriesId = UUID()
        let template = GymClass(title: "Evening HIIT", coach: "Jamie", startTime: Date())

        try await mock.updateClassSeries(gymId: gymId, seriesId: seriesId, from: Date(), updatedTemplate: template)

        #expect((mock.classes[gymId] ?? [:]).isEmpty)
    }
}
